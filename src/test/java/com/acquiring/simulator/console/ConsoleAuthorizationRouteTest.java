package com.acquiring.simulator.console;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.acquiring.simulator.domain.AuthorizationService;
import com.acquiring.simulator.domain.model.AuthorizationResult;
import com.acquiring.simulator.domain.model.AuthorizedContext;
import com.acquiring.simulator.domain.model.Outcome;
import com.acquiring.simulator.domain.model.PurchaseRequest;
import com.acquiring.simulator.domain.model.Route;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * Bounded console API boundary slice: the submission route and the token-policy seam.
 *
 * <p>These tests use no network, no Docker and no AWS. The service is a recording
 * subclass so the only property under test is the boundary's order of operations: a
 * refused credential, a rate-limit refusal, a bad media type, a malformed body or a
 * foreign context must leave the business service completely untouched, and an authorized
 * submission must reach it exactly once with the trusted context rather than the
 * caller-declared one.</p>
 *
 * <p>Nothing here verifies a token signature: the decoder is a test double for the seam
 * described in {@link ConsoleTokenDecoder}. A green result is evidence about policy and
 * ordering, not about cryptography, Keycloak or a deployed authorizer.</p>
 */
class ConsoleAuthorizationRouteTest {

    private static final String ISSUER = "https://auth.sim.test/realms/sim";
    private static final String AUDIENCE = "console-api";
    private static final String SUBJECT = "demo-user";
    private static final String CLIENT = "console-demo";
    private static final String SCOPE = "console.demo";
    private static final String BEARER = "Bearer demo-token";
    private static final Instant NOW = Instant.parse("2026-11-22T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private static final AuthorizedContext CONTEXT = new AuthorizedContext(
            "ACQ-SIM-001", "MERCH-SIM-001", "SUB-DEMO", Set.of(Route.DOMESTIC));

    @Test
    void routeCannotExistWithoutTheTokenSeam() {
        ConsoleContextRegistry registry = principal -> CONTEXT;
        RecordingService service = new RecordingService();

        assertThatThrownBy(() -> new ConsoleAuthorizationRoute(
                null, registry, service, ConsoleRateLimitHook.PERMISSIVE, CLOCK))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("token authorizer");

        assertThatThrownBy(() -> new ConsoleAuthorizationRoute(
                authorizer(validClaims()), null, service, ConsoleRateLimitHook.PERMISSIVE, CLOCK))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("context registry");

        assertThatThrownBy(() -> new ConsoleAuthorizationRoute(
                authorizer(validClaims()), registry, null, ConsoleRateLimitHook.PERMISSIVE, CLOCK))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("authorization service");

        assertThatThrownBy(() -> new ConsoleAuthorizationRoute(
                authorizer(validClaims()), registry, service, null, CLOCK))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("rate-limit hook");

        assertThatThrownBy(() -> new DemoConsoleAuthorizer(
                ISSUER, AUDIENCE, SUBJECT, CLIENT, SCOPE, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("decoder");
    }

    @Test
    void missingTokenIsRefusedBeforeAnyBusinessAction() {
        RecordingService service = new RecordingService();
        CountingRateLimit rateLimit = new CountingRateLimit(
                ConsoleRateLimitHook.Decision.PERMITTED);

        ConsoleHttpResponse response = route(service, rateLimit)
                .handle(request(null, validBody()));

        assertThat(response.status()).isEqualTo(401);
        assertThat(text(response)).contains("\"error\":\"UNAUTHENTICATED\"");
        assertThat(service.calls).isZero();
        assertThat(rateLimit.calls.get()).isZero();
        assertThat(text(response)).doesNotContain("demo-token");
        assertThat(response.toString()).doesNotContain("demo-token");
    }

    @Test
    void duplicatedAuthorizationHeaderIsRefusedRatherThanFirstValueRead() {
        RecordingService service = new RecordingService();
        Map<String, List<String>> headers = new LinkedHashMap<>();
        headers.put("Content-Type", List.of("application/json"));
        headers.put("Authorization", List.of(BEARER, BEARER));

        ConsoleHttpResponse response = route(service, ConsoleRateLimitHook.PERMISSIVE)
                .handle(new ConsoleHttpRequest("POST", ConsoleAuthorizationRoute.PATH,
                        headers, validBody()));

        assertThat(response.status()).isEqualTo(401);
        assertThat(service.calls).isZero();
    }

    @Test
    void authorizerRefusesEachCredentialsClassWithItsOwnReason() {
        DemoConsoleAuthorizer authorizer = authorizer(validClaims());

        assertThat(reasonOf(authorizer, null))
                .isEqualTo(ConsoleAuthenticationException.Reason.MISSING_TOKEN);
        assertThat(reasonOf(authorizer, "Basic ZGVtbw=="))
                .isEqualTo(ConsoleAuthenticationException.Reason.MALFORMED_TOKEN);
        assertThat(reasonOf(authorizer, "Bearer "))
                .isEqualTo(ConsoleAuthenticationException.Reason.MALFORMED_TOKEN);
        assertThatThrownBy(() -> authorizer.authorize("Bearer unverifiable", NOW))
                .isInstanceOfSatisfying(ConsoleAuthenticationException.class, refused ->
                        assertThat(refused.reason())
                                .isEqualTo(ConsoleAuthenticationException.Reason.MALFORMED_TOKEN));
        assertThatThrownBy(() -> authorizer(claimsOf("https://other.test/realms/sim",
                List.of(AUDIENCE), SUBJECT, CLIENT, List.of(SCOPE),
                NOW.minusSeconds(60), NOW.plusSeconds(600)))
                .authorize(BEARER, NOW))
                .isInstanceOfSatisfying(ConsoleAuthenticationException.class, refused ->
                        assertThat(refused.reason())
                                .isEqualTo(ConsoleAuthenticationException.Reason.WRONG_ISSUER));
        assertThat(reasonOf(authorizer(claimsOf(ISSUER, List.of("other-api"), SUBJECT,
                CLIENT, List.of(SCOPE), NOW.minusSeconds(60), NOW.plusSeconds(600))), BEARER))
                .isEqualTo(ConsoleAuthenticationException.Reason.WRONG_AUDIENCE);
        // A service-account token for another client is refused by class even though its
        // audience and scope would match: the client allowlist is the primary control.
        assertThat(reasonOf(authorizer(claimsOf(ISSUER, List.of(AUDIENCE),
                "service-account-svc-console", "svc-console", List.of(SCOPE),
                NOW.minusSeconds(60), NOW.plusSeconds(600))), BEARER))
                .isEqualTo(ConsoleAuthenticationException.Reason.WRONG_AUTHORIZED_PARTY);
        assertThat(reasonOf(authorizer(claimsOf(ISSUER, List.of(AUDIENCE), "someone-else",
                CLIENT, List.of(SCOPE), NOW.minusSeconds(60), NOW.plusSeconds(600))), BEARER))
                .isEqualTo(ConsoleAuthenticationException.Reason.WRONG_SUBJECT);
        assertThat(reasonOf(authorizer(claimsOf(ISSUER, List.of(AUDIENCE), SUBJECT,
                CLIENT, List.of("ws.observe"), NOW.minusSeconds(60), NOW.plusSeconds(600))),
                BEARER))
                .isEqualTo(ConsoleAuthenticationException.Reason.MISSING_SCOPE);
        assertThat(reasonOf(authorizer(claimsOf(ISSUER, List.of(AUDIENCE), SUBJECT,
                CLIENT, List.of(SCOPE), NOW.minusSeconds(600), NOW)),
                BEARER, NOW))
                .isEqualTo(ConsoleAuthenticationException.Reason.EXPIRED);
        assertThat(reasonOf(authorizer(claimsOf(ISSUER, List.of(AUDIENCE), SUBJECT,
                CLIENT, List.of(SCOPE), NOW.plusSeconds(60), NOW.plusSeconds(600))),
                BEARER))
                .isEqualTo(ConsoleAuthenticationException.Reason.NOT_YET_VALID);
    }

    @Test
    void everyRefusedCredentialLeavesTheBusinessServiceUntouched() {
        List<ConsoleTokenClaims> refusedClaims = List.of(
                claimsOf(ISSUER, List.of(AUDIENCE), SUBJECT, CLIENT, List.of(SCOPE),
                        NOW.minusSeconds(600), NOW),
                claimsOf(ISSUER, List.of("other-api"), SUBJECT, CLIENT, List.of(SCOPE),
                        NOW.minusSeconds(60), NOW.plusSeconds(600)),
                claimsOf(ISSUER, List.of(AUDIENCE), "someone-else", CLIENT, List.of(SCOPE),
                        NOW.minusSeconds(60), NOW.plusSeconds(600)),
                claimsOf(ISSUER, List.of(AUDIENCE), SUBJECT, CLIENT, List.of("ws.observe"),
                        NOW.minusSeconds(60), NOW.plusSeconds(600)),
                claimsOf("https://other.test/realms/sim", List.of(AUDIENCE), SUBJECT, CLIENT,
                        List.of(SCOPE), NOW.minusSeconds(60), NOW.plusSeconds(600)));

        for (ConsoleTokenClaims claims : refusedClaims) {
            RecordingService service = new RecordingService();
            ConsoleHttpResponse response = new ConsoleAuthorizationRoute(
                    authorizer(claims), principal -> CONTEXT, service,
                    ConsoleRateLimitHook.PERMISSIVE, CLOCK)
                    .handle(request(BEARER, validBody()));

            assertThat(response.status()).as(claims.toString()).isIn(401, 403);
            assertThat(service.calls).as(claims.toString()).isZero();
        }
    }

    @Test
    void wrongMediaTypeIsRefusedAfterAuthenticationWithoutBusinessAction() {
        RecordingService service = new RecordingService();
        Map<String, List<String>> headers = new LinkedHashMap<>();
        headers.put("Content-Type", List.of("text/plain"));
        headers.put("Authorization", List.of(BEARER));

        ConsoleHttpResponse response = route(service, ConsoleRateLimitHook.PERMISSIVE)
                .handle(new ConsoleHttpRequest("POST", ConsoleAuthorizationRoute.PATH,
                        headers, validBody()));

        assertThat(response.status()).isEqualTo(415);
        assertThat(text(response)).contains("\"error\":\"UNSUPPORTED_MEDIA\"");
        assertThat(service.calls).isZero();
    }

    @Test
    void unknownSchemaFieldIsRefusedBeforeBusinessAction() {
        RecordingService service = new RecordingService();
        byte[] body = new String(validBody(), StandardCharsets.US_ASCII)
                .replace("}", ",\"decision\":\"APPROVED\"}")
                .getBytes(StandardCharsets.US_ASCII);

        ConsoleHttpResponse response = route(service, ConsoleRateLimitHook.PERMISSIVE)
                .handle(request(BEARER, body));

        assertThat(response.status()).isEqualTo(400);
        assertThat(text(response)).contains("\"error\":\"INVALID_INPUT\"");
        assertThat(service.calls).isZero();
    }

    @Test
    void oversizedBodyIsRefusedBeforeBusinessAction() {
        RecordingService service = new RecordingService();

        ConsoleHttpResponse response = route(service, ConsoleRateLimitHook.PERMISSIVE)
                .handle(request(BEARER, new byte[5000]));

        assertThat(response.status()).isEqualTo(413);
        assertThat(text(response)).contains("\"error\":\"BODY_TOO_LARGE\"");
        assertThat(service.calls).isZero();
    }

    @Test
    void foreignCallerContextIsRefusedWithoutAnyBusinessWrite() {
        RecordingService service = new RecordingService();
        byte[] body = body("ACQ-B", "MERCH-SIM-001");

        ConsoleHttpResponse response = route(service, ConsoleRateLimitHook.PERMISSIVE)
                .handle(request(BEARER, body));

        assertThat(response.status()).isEqualTo(403);
        assertThat(text(response)).contains("\"error\":\"CONTEXT_DENIED\"");
        assertThat(service.calls).isZero();
    }

    @Test
    void missingRegistryContextIsRefusedAsContextDenied() {
        RecordingService service = new RecordingService();
        ConsoleAuthorizationRoute route = new ConsoleAuthorizationRoute(
                authorizer(validClaims()), principal -> null, service,
                ConsoleRateLimitHook.PERMISSIVE, CLOCK);

        ConsoleHttpResponse response = route.handle(request(BEARER, validBody()));

        assertThat(response.status()).isEqualTo(403);
        assertThat(service.calls).isZero();
    }

    @Test
    void rateLimitRefusalStopsBeforeAnyParsingOrBusinessAction() {
        RecordingService service = new RecordingService();
        CountingRateLimit rateLimit = new CountingRateLimit(
                ConsoleRateLimitHook.Decision.REFUSED);

        ConsoleHttpResponse response = route(service, rateLimit)
                .handle(request(BEARER, validBody()));

        assertThat(response.status()).isEqualTo(429);
        assertThat(response.body()).isEmpty();
        assertThat(response.headers()).doesNotContainKey("Retry-After");
        assertThat(rateLimit.calls.get()).isEqualTo(1);
        assertThat(service.calls).isZero();
    }

    @Test
    void authorizedSubmissionReachesTheServiceExactlyOnceWithTheTrustedContext() {
        RecordingService service = new RecordingService();
        CountingRateLimit rateLimit = new CountingRateLimit(
                ConsoleRateLimitHook.Decision.PERMITTED);

        ConsoleHttpResponse response = route(service, rateLimit)
                .handle(request(BEARER, validBody()));

        assertThat(response.status()).isEqualTo(200);
        assertThat(service.calls).isEqualTo(1);
        assertThat(rateLimit.calls.get()).isEqualTo(1);

        // The trusted context is the registry's, not the body's.
        assertThat(service.context).isSameAs(CONTEXT);
        assertThat(service.request.merchantRef()).isEqualTo("MERCH-SIM-001");
        assertThat(service.request.terminalRef()).isEqualTo("TERM-SIM-001");
        assertThat(service.request.channelPaymentReference()).isEqualTo("PURCHASE-1");
        assertThat(service.request.amount()).isEqualByComparingTo("10.00");
        assertThat(service.request.currency()).isEqualTo("USD");
        assertThat(service.request.credentialRef()).isEqualTo("SIM-APP-001");
        assertThat(service.request.cryptogramPresent()).isTrue();
        assertThat(service.request.priorReference()).isNull();

        assertThat(text(response))
                .contains("\"authorizationReference\":\"AUTH-UNIT-1\"")
                .contains("\"outcome\":\"APPROVED\"")
                .contains("\"correlationId\":\"CORR-CONSOLE-1\"");
        assertThat(text(response)).doesNotContain("demo-token");
    }

    @Test
    void transportRouteIsClosedToEverythingElse() {
        RecordingService service = new RecordingService();
        ConsoleAuthorizationRoute route = route(service, ConsoleRateLimitHook.PERMISSIVE);

        Map<String, List<String>> headers = new LinkedHashMap<>();
        headers.put("Content-Type", List.of("application/json"));
        headers.put("Authorization", List.of(BEARER));

        assertThat(route.handle(new ConsoleHttpRequest("GET", ConsoleAuthorizationRoute.PATH,
                headers, validBody())).status()).isEqualTo(405);
        assertThat(route.handle(new ConsoleHttpRequest("POST", "/console/v1/other",
                headers, validBody())).status()).isEqualTo(404);
        assertThat(service.calls).isZero();
    }

    // ------------------------------------------------------------------ fixtures

    private static ConsoleAuthorizationRoute route(
            AuthorizationService service, ConsoleRateLimitHook rateLimit) {
        return new ConsoleAuthorizationRoute(authorizer(validClaims()),
                principal -> CONTEXT, service, rateLimit, CLOCK);
    }

    private static DemoConsoleAuthorizer authorizer(ConsoleTokenClaims claims) {
        return new DemoConsoleAuthorizer(ISSUER, AUDIENCE, SUBJECT, CLIENT, SCOPE,
                token -> {
                    if (!"demo-token".equals(token)) {
                        throw new ConsoleAuthenticationException(
                                ConsoleAuthenticationException.Reason.MALFORMED_TOKEN);
                    }
                    return claims;
                });
    }

    private static ConsoleAuthenticationException.Reason reasonOf(
            DemoConsoleAuthorizer authorizer, String header) {
        return reasonOf(authorizer, header, NOW);
    }

    private static ConsoleAuthenticationException.Reason reasonOf(
            DemoConsoleAuthorizer authorizer, String header, Instant at) {
        try {
            authorizer.authorize(header, at);
            throw new AssertionError("the credential should have been refused");
        } catch (ConsoleAuthenticationException refused) {
            return refused.reason();
        }
    }

    private static ConsoleTokenClaims validClaims() {
        return claimsOf(ISSUER, List.of(AUDIENCE), SUBJECT, CLIENT, List.of(SCOPE),
                NOW.minusSeconds(60), NOW.plusSeconds(600));
    }

    private static ConsoleTokenClaims claimsOf(String issuer, List<String> audience,
            String subject, String client, List<String> scopes, Instant issuedAt,
            Instant expiresAt) {
        return new ConsoleTokenClaims(issuer, audience, subject, client, scopes,
                issuedAt, issuedAt, expiresAt);
    }

    private static ConsoleHttpRequest request(String authorization, byte[] body) {
        Map<String, List<String>> headers = new LinkedHashMap<>();
        headers.put("Content-Type", List.of("application/json"));
        if (authorization != null) {
            headers.put("Authorization", List.of(authorization));
        }
        return new ConsoleHttpRequest("POST", ConsoleAuthorizationRoute.PATH, headers, body);
    }

    private static byte[] validBody() {
        return body("ACQ-SIM-001", "MERCH-SIM-001");
    }

    /** The exact C1 field order and closed key set. */
    private static byte[] body(String acquirerRef, String merchantRef) {
        String json = "{\"contractVersion\":\"1\",\"operation\":\"A\","
                + "\"correlationId\":\"CORR-CONSOLE-1\","
                + "\"acquirerRef\":\"" + acquirerRef + "\","
                + "\"merchantRef\":\"" + merchantRef + "\","
                + "\"terminalRef\":\"TERM-SIM-001\","
                + "\"paymentRef\":\"PURCHASE-1\","
                + "\"credentialFixtureRef\":\"SIM-APP-001\","
                + "\"amount\":\"10.00\",\"currency\":\"USD\","
                + "\"entryMode\":\"simulated-contact\","
                + "\"cvm\":\"simulated-verified\","
                + "\"cryptogramPresent\":true}";
        return json.getBytes(StandardCharsets.US_ASCII);
    }

    private static String text(ConsoleHttpResponse response) {
        return new String(response.body(), StandardCharsets.UTF_8);
    }

    /** Records the single business call instead of executing it. */
    private static final class RecordingService extends AuthorizationService {
        private int calls;
        private AuthorizedContext context;
        private PurchaseRequest request;

        RecordingService() {
            super(null, null, null, Clock.systemUTC());
        }

        @Override
        public AuthorizationResult authorize(AuthorizedContext context, PurchaseRequest request) {
            calls++;
            this.context = context;
            this.request = request;
            return AuthorizationResult.of(Outcome.APPROVED, "AUTH-UNIT-1");
        }
    }

    private static final class CountingRateLimit implements ConsoleRateLimitHook {
        private final Decision decision;
        private final AtomicInteger calls = new AtomicInteger();

        CountingRateLimit(Decision decision) {
            this.decision = decision;
        }

        @Override
        public Decision evaluate(ConsolePrincipal principal, Instant now) {
            calls.incrementAndGet();
            return decision;
        }
    }
}
