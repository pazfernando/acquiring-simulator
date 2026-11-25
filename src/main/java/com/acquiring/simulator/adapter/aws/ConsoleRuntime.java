package com.acquiring.simulator.adapter.aws;

import com.acquiring.simulator.console.ConsoleAuthorizationRoute;
import com.acquiring.simulator.console.ConsoleHistoryRoute;
import com.acquiring.simulator.console.ConsoleHttpRequest;
import com.acquiring.simulator.console.ConsoleHttpResponse;
import com.acquiring.simulator.console.ConsoleIntegrationEntryPoint;
import com.acquiring.simulator.console.ConsoleOriginDenialSink;
import com.acquiring.simulator.console.ConsoleOriginVerificationGate;
import com.acquiring.simulator.console.StandardStreamOriginDenialSink;
import java.time.Clock;
import java.util.Objects;
import java.util.function.Function;

/**
 * Cold-start composition for the deployed console Lambda.
 *
 * <p>The origin-verification gate is built here, from the process environment, <strong>before any
 * request is served</strong>. {@link ConsoleOriginVerificationGate#from} throws when
 * {@code EDGE_ORIGIN_VERIFY} is absent, empty, shorter than the minimum or outside the fixed
 * alphabet, so a misconfigured deployment fails handler construction (a platform 5xx) instead of
 * starting unverified. There is no default, placeholder or degraded mode.</p>
 *
 * <p>This composition is the reason the boundary control runs in a real path rather than only in
 * tests: {@code ConsoleHandler} has no other way to obtain an entry point, and every request on the
 * {@code ANY /api/{proxy+}} integration is handed to
 * {@link ConsoleIntegrationEntryPoint#handle}.</p>
 *
 * <p><strong>Business routes are not composed in this revision.</strong> The submission route
 * requires real credential verification (slice K1) and the history route requires a contract-fixed
 * cursor signing-key source (advisory A-4); neither is invented here. The application below
 * recognizes the two existing route shapes and answers {@code 503 UNAVAILABLE} for them, so a
 * request that does pass the gate receives a truthful "not composed" answer and performs no
 * business action. No endpoint is added and no stub authorizer, registry or key exists.</p>
 */
final class ConsoleRuntime {

    /**
     * Platform-supplied version of the deployed function, used for the denial record's
     * {@code appVersion} field. It is the deployed version fact, not a new configuration surface and
     * not a secret; it never carries request data.
     */
    static final String APP_VERSION_VARIABLE = "AWS_LAMBDA_FUNCTION_VERSION";

    private ConsoleRuntime() {
    }

    /** The production composition: gate and version from the process environment. */
    static ConsoleIntegrationEntryPoint consoleEntryPoint() {
        return consoleEntryPoint(System::getenv);
    }

    /**
     * Resolver-seam variant so fail-closed initialization can be asserted without mutating the
     * process environment.
     */
    static ConsoleIntegrationEntryPoint consoleEntryPoint(Function<String, String> environment) {
        return compose(environment, deployedApplication(),
                new StandardStreamOriginDenialSink(System.out), Clock.systemUTC());
    }

    /**
     * The one composition used by production and by the composed-path tests. The gate and the
     * version are read from the same resolver; a failure in either prevents construction.
     */
    static ConsoleIntegrationEntryPoint compose(Function<String, String> environment,
            Function<ConsoleHttpRequest, ConsoleHttpResponse> application,
            ConsoleOriginDenialSink denials, Clock clock) {
        Objects.requireNonNull(environment, "environment");
        Objects.requireNonNull(application, "application");
        Objects.requireNonNull(denials, "denials");
        Objects.requireNonNull(clock, "clock");

        ConsoleOriginVerificationGate gate = ConsoleOriginVerificationGate.from(environment);
        String appVersion = required(environment, APP_VERSION_VARIABLE);
        return new ConsoleIntegrationEntryPoint(gate, application, denials, clock, appVersion);
    }

    /**
     * Recognizes the two existing console routes and reports them unavailable; every other path is
     * an empty {@code 404}. This is a composition statement, not business logic: the route
     * collaborators are undelivered (see the class note), so returning a fabricated success or a
     * fake authentication failure would be less truthful than {@code 503}.
     */
    static Function<ConsoleHttpRequest, ConsoleHttpResponse> deployedApplication() {
        return request -> {
            if (ConsoleAuthorizationRoute.METHOD.equals(request.method())
                    && ConsoleAuthorizationRoute.PATH.equals(request.path())) {
                return ConsoleHttpResponse.empty(503);
            }
            if (ConsoleHistoryRoute.METHOD.equals(request.method())
                    && ConsoleHistoryRoute.transactionIdOf(request.path()) != null) {
                return ConsoleHttpResponse.empty(503);
            }
            return ConsoleHttpResponse.empty(404);
        };
    }

    private static String required(Function<String, String> environment, String name) {
        String value = environment.apply(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("missing environment " + name);
        }
        return value.trim();
    }
}
