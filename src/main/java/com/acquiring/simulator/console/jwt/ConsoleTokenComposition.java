package com.acquiring.simulator.console.jwt;

import com.acquiring.simulator.console.ConsoleAuthorizer;
import com.acquiring.simulator.console.ConsoleTokenDecoder;
import com.acquiring.simulator.console.DemoConsoleAuthorizer;
import java.time.Clock;
import java.util.Objects;

/**
 * Wires the real JWT decoder into the unchanged claim-policy core.
 *
 * <p>This is the replacement wiring for the stub decoder seam: one configured HTTP console token
 * class produces both the verifier and the {@link DemoConsoleAuthorizer} that applies issuer,
 * audience, authorized-party, subject and scope policy. The authorizer's checks and order are
 * untouched; only the decoder it delegates to is real.</p>
 *
 * <p>Both factory methods require an explicit {@link TrustedIssuerConfiguration}; there is no
 * no-argument or default-configuration version, so a runtime cannot fall back to an invented issuer,
 * audience, subject or key. The deployed composition still has to supply those values and call this
 * factory; composing the Lambda business route remains a separate, later step.</p>
 */
public final class ConsoleTokenComposition {

    private ConsoleTokenComposition() {
    }

    /**
     * Verify against an explicit key source. Tests use this with a non-network source; a deployment
     * can use it with a managed key source that does not perform per-request I/O.
     */
    public static ConsoleAuthorizer authorizer(
            TrustedIssuerConfiguration configuration, JwksSource jwksSource, Clock clock) {
        Objects.requireNonNull(configuration, "configuration");
        Objects.requireNonNull(jwksSource, "jwksSource");
        Objects.requireNonNull(clock, "clock");

        ConsoleTokenDecoder decoder =
                new JwtConsoleTokenDecoder(configuration, jwksSource, clock);
        return new DemoConsoleAuthorizer(
                configuration.issuer(),
                configuration.audience(),
                configuration.demoSubject(),
                configuration.demoClientId(),
                configuration.requiredScope(),
                decoder);
    }

    /**
     * Deployment convenience: verify against the configured JWKS URL. This is the only place a
     * network key source is created, and it is created from configuration rather than a literal.
     */
    public static ConsoleAuthorizer authorizer(
            TrustedIssuerConfiguration configuration, Clock clock) {
        Objects.requireNonNull(configuration, "configuration");
        return authorizer(configuration, HttpJwksSource.forConfiguration(configuration), clock);
    }
}
