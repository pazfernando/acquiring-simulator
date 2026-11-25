package com.acquiring.simulator.console;

import com.acquiring.simulator.channel.C3Response;
import com.acquiring.simulator.channel.C3ResponseBuilder;
import com.acquiring.simulator.channel.C3ResponseBuilder.AttemptIdentifiers;
import com.acquiring.simulator.channel.C3ResponseBuilder.Denial;
import com.acquiring.simulator.channel.C3ResponseBuilder.Evidence;
import com.acquiring.simulator.channel.C3ResponseBuilder.Facts;
import com.acquiring.simulator.channel.C3ResponseBuilder.Origin;
import com.acquiring.simulator.channel.C3ResponseBuilder.PreAdmissionFailure;
import com.acquiring.simulator.channel.C3ResponseBuilder.UnsentCause;
import com.acquiring.simulator.channel.ChannelAuthorizationRequest;
import com.acquiring.simulator.channel.ChannelErrorCode;
import com.acquiring.simulator.channel.ChannelRequestValidationException;
import com.acquiring.simulator.channel.ChannelRequestValidator;
import com.acquiring.simulator.domain.AuthorizationService;
import com.acquiring.simulator.domain.model.AuthorizationResult;
import com.acquiring.simulator.domain.model.AuthorizedContext;
import com.acquiring.simulator.domain.model.Outcome;
import com.acquiring.simulator.domain.model.PurchaseRequest;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * The console submission route: {@code POST /console/v1/authorizations}.
 *
 * <p>This is the browser-facing edge of Operation A, realized as a pure request/response
 * boundary. It opens no listener: there is no relay and no loopback server, matching the
 * owner's "no relay" direction. An HTTP API / CloudFront edge is expected to hand it the
 * same request shape later, so the boundary can be exercised locally without a socket,
 * without a browser-held service secret and without any AWS call.</p>
 *
 * <h2>Order of operations (fixed)</h2>
 * <ol>
 *   <li>transport route: method and path, or a bare 404/405;</li>
 *   <li>credential: {@link ConsoleAuthorizer} seam; every refusal is a C3
 *       authentication/context denial and nothing else runs;</li>
 *   <li>rate-limit seam: consulted only after the identity is known, before any parsing
 *       or business action; no numeric limit exists here;</li>
 *   <li>C1 body: closed JSON schema through the existing
 *       {@link ChannelRequestValidator} (400/413/415);</li>
 *   <li>trusted context: {@link ConsoleContextRegistry} seam, then an exact match of the
 *       caller's {@code acquirerRef}/{@code merchantRef} against that trusted context
 *       (403 with no write);</li>
 *   <li>exactly one {@link AuthorizationService#authorize} call, on the service instance
 *       the runtime wires, so a verified submission takes the claim, dispatch-intent,
 *       result and evidence path — not a legacy or parallel branch.</li>
 * </ol>
 *
 * <h2>What the body of a reached-service outcome is not</h2>
 * <p>The pinned channel contract C3 defines one closed response envelope with thirteen
 * keys, including the original identifiers, the committed {@code businessVersion} and the
 * enumerated error. The authorization service currently exposes only
 * {@code (outcome, reference)}: it does not return the reason of a pre-admission rejection,
 * the transaction/attempt identifiers, or the committed version. Emitting the C3 envelope
 * for that branch would therefore require inventing values C3 forbids, so the reached-service
 * branch returns a clearly provisional acknowledgement and the refusals that happen
 * <em>before</em> business action return the exact C3 envelope produced by the approved
 * {@link C3ResponseBuilder}. Closing that gap needs a committed-version read in the domain,
 * which is a later slice and is reported as such.</p>
 *
 * <p>The route never logs, never retries and never resends. It reads the supplied
 * {@link Clock} once per request.</p>
 */
public final class ConsoleAuthorizationRoute {

    /** Approved C1 submission route. */
    public static final String PATH = "/console/v1/authorizations";

    /** Approved C1 submission method. */
    public static final String METHOD = "POST";

    /** Approved C1 media type. */
    public static final String MEDIA_TYPE = "application/json";

    private final ConsoleAuthorizer authorizer;
    private final ConsoleContextRegistry contexts;
    private final AuthorizationService service;
    private final ConsoleRateLimitHook rateLimit;
    private final Clock clock;

    /**
     * All five collaborators are required. In particular there is no constructor without
     * an authorizer: the route has no anonymous or "trusted header" path, so it cannot be
     * reached without the token seam.
     */
    public ConsoleAuthorizationRoute(ConsoleAuthorizer authorizer,
            ConsoleContextRegistry contexts, AuthorizationService service,
            ConsoleRateLimitHook rateLimit, Clock clock) {
        this.authorizer = Objects.requireNonNull(authorizer,
                "a console route requires the token authorizer seam");
        this.contexts = Objects.requireNonNull(contexts,
                "a console route requires the trusted context registry seam");
        this.service = Objects.requireNonNull(service,
                "a console route requires the authorization service");
        this.rateLimit = Objects.requireNonNull(rateLimit,
                "a console route requires the rate-limit hook seam");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Handle one console submission request. */
    public ConsoleHttpResponse handle(ConsoleHttpRequest request) {
        Objects.requireNonNull(request, "request");
        if (!METHOD.equals(request.method())) {
            return ConsoleHttpResponse.empty(405);
        }
        if (!PATH.equals(request.path())) {
            return ConsoleHttpResponse.empty(404);
        }

        Instant now = clock.instant();
        ConsolePrincipal principal;
        try {
            principal = authenticate(request, now);
        } catch (ConsoleAuthenticationException refused) {
            return denial(refused, now);
        }

        if (rateLimit.evaluate(principal, now) == ConsoleRateLimitHook.Decision.REFUSED) {
            // No C3 row exists for "too many requests"; the pinned status allowlist has no
            // 429. An empty body is the honest form: no envelope, no Retry-After, no hint.
            return ConsoleHttpResponse.empty(429);
        }

        if (!isJsonRequest(request)) {
            return preAdmission(PreAdmissionFailure.UNSUPPORTED_MEDIA, now);
        }

        ChannelAuthorizationRequest submitted;
        try {
            submitted = ChannelRequestValidator.validate(request.body());
        } catch (ChannelRequestValidationException invalid) {
            PreAdmissionFailure failure = invalid.errorCode() == ChannelErrorCode.BODY_TOO_LARGE
                    ? PreAdmissionFailure.BODY_TOO_LARGE
                    : PreAdmissionFailure.INVALID_INPUT;
            return preAdmission(failure, now);
        }

        AuthorizedContext context;
        try {
            context = contexts.contextFor(principal);
        } catch (RuntimeException registryFailure) {
            // A registry that cannot answer yields no context, never a default one.
            context = null;
        }
        if (context == null || !trustedContextMatches(context, submitted)) {
            return denial(Denial.CONTEXT_DENIED, now);
        }

        AuthorizationResult result = service.authorize(context, purchaseRequest(submitted));
        return acknowledged(result, submitted.correlationId());
    }

    /**
     * Exactly one credential is accepted. A duplicated {@code Authorization} header is a
     * refusal rather than a first-value read, so two credentials cannot be smuggled past
     * a single-value check.
     */
    private ConsolePrincipal authenticate(ConsoleHttpRequest request, Instant now) {
        List<String> values = request.headerValues("Authorization");
        if (values.size() > 1) {
            throw new ConsoleAuthenticationException(
                    ConsoleAuthenticationException.Reason.MISSING_TOKEN);
        }
        return authorizer.authorize(values.isEmpty() ? null : values.get(0), now);
    }

    /**
     * The caller-supplied acquirer and merchant must equal the trusted context. The
     * context itself is never derived from the body; a mismatch is refused before any
     * claim, event, rejection audit or send.
     */
    private static boolean trustedContextMatches(
            AuthorizedContext context, ChannelAuthorizationRequest submitted) {
        return Objects.equals(context.acquirerRef(), submitted.acquirerRef())
                && Objects.equals(context.merchantRef(), submitted.merchantRef());
    }

    /** Map the typed channel request onto the business request; no caller identity. */
    private static PurchaseRequest purchaseRequest(ChannelAuthorizationRequest submitted) {
        return new PurchaseRequest(
                submitted.merchantRef(),
                submitted.terminalRef(),
                submitted.paymentRef(),
                submitted.amount(),
                submitted.currency(),
                submitted.credentialFixtureRef(),
                submitted.entryMode(),
                submitted.cvm(),
                submitted.cryptogramPresent(),
                submitted.risk(),
                submitted.priorAuthorizationId());
    }

    /** C3 authentication/context denial: 401 for the credential, 403 for the context. */
    private ConsoleHttpResponse denial(ConsoleAuthenticationException refused, Instant now) {
        return denial(denialOf(refused.reason()), now);
    }

    private ConsoleHttpResponse denial(Denial denial, Instant now) {
        return c3(C3ResponseBuilder.build(facts(denial, null, now)));
    }

    private ConsoleHttpResponse preAdmission(PreAdmissionFailure failure, Instant now) {
        return c3(C3ResponseBuilder.build(facts(Denial.NONE, failure, now)));
    }

    /**
     * The C3 builder always requires an evidence fact even when an earlier row wins. The
     * placeholder is unreachable: {@code denial} and {@code preAdmissionFailure} short
     * circuit before any evidence branch.
     */
    private static Facts facts(Denial denial, PreAdmissionFailure preAdmission, Instant now) {
        return new Facts(denial, preAdmission, false,
                new Evidence.NoClaimUnsent(UnsentCause.ADMISSION_UNAVAILABLE),
                Origin.SUBMISSION, AttemptIdentifiers.unknown(), now);
    }

    private static ConsoleHttpResponse c3(C3Response response) {
        return ConsoleHttpResponse.json(response.httpStatus().code(),
                response.envelope().toCanonicalJson());
    }

    /**
     * Deliberate choice, stated rather than hidden: the reviewed access direction lists
     * "service token presented -> 403", but a service token fails the audience check first
     * and is reported as 401 UNAUTHENTICATED. C3 permits 401 or 403 for this row; the
     * credential-class check is what refuses it, and the status split does not change that.
     */
    private static Denial denialOf(ConsoleAuthenticationException.Reason reason) {
        return switch (reason) {
            case MISSING_TOKEN, MALFORMED_TOKEN, WRONG_ISSUER, WRONG_AUDIENCE,
                    WRONG_SUBJECT, EXPIRED, NOT_YET_VALID, UNSUPPORTED_ALGORITHM,
                    UNKNOWN_KEY, BAD_SIGNATURE, KEY_SOURCE_UNAVAILABLE,
                    TOKEN_LIFETIME_EXCEEDED -> Denial.UNAUTHENTICATED;
            // The identity is the demo subject, but it may not perform this operation.
            case WRONG_AUTHORIZED_PARTY, MISSING_SCOPE, WRONG_SCOPE -> Denial.CONTEXT_DENIED;
        };
    }

    /**
     * PROVISIONAL acknowledgement for an outcome the authorization service produced.
     *
     * <p>It carries only what the service actually returns: the business outcome, the
     * authorization reference and the caller's correlation id (which C3 allows to be
     * echoed and never treats as trusted). It is <em>not</em> the C3 envelope, because the
     * service does not yet expose the rejection reason, the transaction/attempt
     * identifiers or the committed business version. Status selection:</p>
     * <ul>
     *   <li>{@code APPROVED}/{@code DECLINED} or a network rejection (a reference exists)
     *       -> 200;</li>
     *   <li>{@code PROCESSOR_REJECTED} without a reference is a pre-admission rejection the
     *       service did not classify for this caller -> 400;</li>
     *   <li>{@code NOT_CONFIRMED} -> 202, in flight, never a finality claim.</li>
     * </ul>
     */
    private static ConsoleHttpResponse acknowledged(AuthorizationResult result, String correlationId) {
        Objects.requireNonNull(result, "result");
        Outcome outcome = result.outcome();
        String reference = result.reference();
        int status;
        if (outcome == Outcome.NOT_CONFIRMED) {
            status = 202;
        } else if (outcome == Outcome.PROCESSOR_REJECTED && reference == null) {
            status = 400;
        } else {
            status = 200;
        }
        StringBuilder json = new StringBuilder(160);
        json.append('{');
        field(json, "authorizationReference", reference);
        json.append(',');
        field(json, "correlationId", correlationId);
        json.append(',');
        field(json, "outcome", outcome == null ? null : outcome.name());
        json.append('}');
        return ConsoleHttpResponse.json(status, json.toString());
    }

    private static void field(StringBuilder json, String name, String value) {
        json.append(quote(name)).append(':').append(value == null ? "null" : quote(value));
    }

    /** Minimal JSON string escaping; identifiers here are already profile-constrained. */
    private static String quote(String value) {
        StringBuilder out = new StringBuilder(value.length() + 2);
        out.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format(Locale.ROOT, "\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.append('"').toString();
    }

    /**
     * Requires exactly one {@code Content-Type} whose media type is
     * {@code application/json}; parameters such as {@code charset=utf-8} are ignored
     * because the C1 validator independently rejects any non-ASCII byte.
     */
    private static boolean isJsonRequest(ConsoleHttpRequest request) {
        List<String> values = request.headerValues("Content-Type");
        if (values.size() != 1) {
            return false;
        }
        String value = values.get(0);
        int parameter = value.indexOf(';');
        String mediaType = (parameter < 0 ? value : value.substring(0, parameter)).trim();
        return MEDIA_TYPE.equalsIgnoreCase(mediaType);
    }
}
