package com.acquiring.simulator.console.jwt;

import java.net.URI;
import java.time.Duration;
import java.util.Objects;
import java.util.Set;

/**
 * Required, externally supplied parameters for verifying one token class.
 *
 * <p>Every value is required: there is no literal issuer, no default audience, no built-in key,
 * no fallback subject and no algorithm default. A missing, blank or inconsistent value fails
 * construction, so a misconfigured runtime cannot start in a trusting mode. Values are supplied by
 * the deployment (DevOps) configuration; this type invents no production URL or subject.</p>
 *
 * <p>The record describes the <em>HTTP console</em> token class. {@code forbiddenScope} and
 * {@code forbiddenAudience} name the other resource class (the WS observation class in the
 * approved D-5 text) so a token carrying both is refused rather than narrowed. They are explicit
 * configuration, not inferred aliases.</p>
 *
 * <p>The allowed algorithm set is checked against the algorithms this repository implements
 * ({@code RS256}/{@code RS384}/{@code RS512}). Unknown configured algorithms fail construction
 * rather than being silently dropped. Asymmetric RSA only: HMAC and {@code none} can never be
 * configured.</p>
 */
public record TrustedIssuerConfiguration(
        String issuer,
        String audience,
        URI jwksUri,
        String demoSubject,
        String demoClientId,
        String requiredScope,
        String forbiddenScope,
        String forbiddenAudience,
        Set<String> allowedAlgorithms,
        Duration maxTokenLifetime) {

    /** Algorithms this decoder can actually verify. */
    public static final Set<String> SUPPORTED_ALGORITHMS = Set.of("RS256", "RS384", "RS512");

    /** D-5 access-token ceiling: both browser tokens live at most 900 seconds. */
    public static final Duration MAXIMUM_LIFETIME = Duration.ofSeconds(900);

    public TrustedIssuerConfiguration {
        issuer = requireText(issuer, "issuer");
        audience = requireText(audience, "audience");
        demoSubject = requireText(demoSubject, "demoSubject");
        demoClientId = requireText(demoClientId, "demoClientId");
        requiredScope = requireText(requiredScope, "requiredScope");
        forbiddenScope = requireText(forbiddenScope, "forbiddenScope");
        forbiddenAudience = requireText(forbiddenAudience, "forbiddenAudience");

        Objects.requireNonNull(jwksUri, "jwksUri");
        if (!isSecureOrLoopback(jwksUri)) {
            throw new IllegalArgumentException(
                    "jwksUri must be https (or loopback http for local testing)");
        }
        if (requiredScope.equals(forbiddenScope)) {
            throw new IllegalArgumentException("required and forbidden resource scopes must be disjoint");
        }
        if (audience.equals(forbiddenAudience)) {
            throw new IllegalArgumentException(
                    "required and forbidden resource audiences must be disjoint");
        }

        Objects.requireNonNull(allowedAlgorithms, "allowedAlgorithms");
        allowedAlgorithms = Set.copyOf(allowedAlgorithms);
        if (allowedAlgorithms.isEmpty()) {
            throw new IllegalArgumentException("at least one verification algorithm is required");
        }
        for (String algorithm : allowedAlgorithms) {
            if (!SUPPORTED_ALGORITHMS.contains(algorithm)) {
                throw new IllegalArgumentException(
                        "unsupported verification algorithm: " + algorithm);
            }
        }

        Objects.requireNonNull(maxTokenLifetime, "maxTokenLifetime");
        if (maxTokenLifetime.isZero() || maxTokenLifetime.isNegative()) {
            throw new IllegalArgumentException("maxTokenLifetime must be positive");
        }
        if (maxTokenLifetime.compareTo(MAXIMUM_LIFETIME) > 0) {
            throw new IllegalArgumentException(
                    "maxTokenLifetime must not exceed the 900 second D-5 ceiling");
        }
    }

    /**
     * An issuer is an HTTPS URL for every deployed realm. Loopback HTTP is accepted only so a
     * local harness can label itself without implying a real realm.
     */
    private static boolean isSecureOrLoopback(URI uri) {
        String scheme = uri.getScheme();
        if ("https".equalsIgnoreCase(scheme)) {
            return uri.getHost() != null && !uri.getHost().isBlank();
        }
        if ("http".equalsIgnoreCase(scheme)) {
            String host = uri.getHost();
            return "127.0.0.1".equals(host) || "localhost".equals(host) || "::1".equals(host);
        }
        return false;
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("trusted issuer configuration requires " + name);
        }
        return value;
    }
}
