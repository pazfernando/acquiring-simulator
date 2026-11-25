package com.acquiring.simulator.console;

import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * The Lambda integration entry point for the console API, with the N-02 origin-verification gate as
 * its first application step.
 *
 * <p>The gate is a <strong>required collaborator</strong> in the same sense as the authorizer: there
 * is no constructor without it and no request field, environment variable or runtime switch that
 * bypasses it. {@link #handle} evaluates it before invoking the application, so a refused request
 * performs no route dispatch, no method/path matching (including the existing 404/405 handling), no
 * body read, no credential-seam call, no rate-limit consumption, no registry read and no service
 * call. A refusal returns the contract's fixed {@code 403} body and emits exactly one denial record;
 * a failing sink never changes the response.</p>
 *
 * <p>This gate is <strong>not an authentication factor</strong>. It neither reads the verified
 * principal nor decides anything about the caller's authorization: a request with the correct header
 * still needs the credential seam and the console policy.</p>
 */
public final class ConsoleIntegrationEntryPoint {

    /** The exact response body of a refusal (§7): fixed bytes, fixed key order, no newline. */
    public static final String DENIAL_BODY =
            "{\"version\":1,\"error\":\"EDGE_ORIGIN_NOT_VERIFIED\",\"action\":null}";

    private final ConsoleOriginVerificationGate gate;
    private final Function<ConsoleHttpRequest, ConsoleHttpResponse> application;
    private final ConsoleOriginDenialSink denials;
    private final Clock clock;
    private final String appVersion;

    /**
     * All five collaborators are required. The gate cannot be replaced by a flag: a composition
     * without it does not start.
     */
    public ConsoleIntegrationEntryPoint(ConsoleOriginVerificationGate gate,
            Function<ConsoleHttpRequest, ConsoleHttpResponse> application,
            ConsoleOriginDenialSink denials, Clock clock, String appVersion) {
        this.gate = Objects.requireNonNull(gate, "a console entry point requires the origin "
                + "verification gate");
        this.application = Objects.requireNonNull(application,
                "a console entry point requires the application to dispatch to");
        this.denials = Objects.requireNonNull(denials,
                "a console entry point requires the denial sink");
        this.clock = Objects.requireNonNull(clock, "clock");
        if (appVersion == null || appVersion.isBlank() || appVersion.length() > 64) {
            throw new IllegalArgumentException("a console entry point requires an app version");
        }
        this.appVersion = appVersion;
    }

    /**
     * Evaluate the boundary gate, then dispatch only when it verifies.
     *
     * @param request the request as the HTTP API integration delivers it
     * @param identity platform-supplied identity for the denial record; may be {@code null}
     */
    public ConsoleHttpResponse handle(ConsoleHttpRequest request, ConsoleRequestIdentity identity) {
        Objects.requireNonNull(request, "request");
        ConsoleOriginVerificationGate.Result result = gate.evaluate(request);
        if (!result.refused()) {
            return application.apply(request);
        }
        emitDenial(result.reason(), identity, request);
        return ConsoleHttpResponse.json(403, DENIAL_BODY);
    }

    /**
     * Emit exactly one denial record and contain any sink failure. The response is decided before
     * this call and never depends on it.
     */
    private void emitDenial(ConsoleOriginVerificationGate.Reason reason,
            ConsoleRequestIdentity identity, ConsoleHttpRequest request) {
        try {
            List<String> hosts = request.headerValues("Host");
            String host = hosts.size() == 1 ? hosts.get(0) : null;
            denials.emit(ConsoleOriginDenial.of(reason, identity, host, request.method(),
                    request.path(), clock.instant().toString(), appVersion));
        } catch (RuntimeException sinkFailure) {
            // A logging failure is a defect to alert on, never a reason to permit the request and
            // never a reason to change the refusal response.
        }
    }
}
