package com.acquiring.simulator.console.jwt;

import com.acquiring.simulator.console.ConsoleAuthenticationException;
import com.acquiring.simulator.console.ConsoleAuthenticationException.Reason;
import com.acquiring.simulator.console.ConsoleTokenClaims;
import com.acquiring.simulator.console.ConsoleTokenDecoder;
import com.fasterxml.jackson.databind.JsonNode;
import java.security.GeneralSecurityException;
import java.security.PublicKey;
import java.security.Signature;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Verifies a compact JWS access token against a configured issuer and JWKS.
 *
 * <p>This replaces the decoder seam with a real verification step. It produces the same
 * {@link ConsoleTokenClaims} view the unchanged {@code DemoConsoleAuthorizer} policy already
 * consumes, so claim order and the current-mandate intersection stay in one place. This class adds
 * no authorization decision of its own beyond refusing an unverifiable or out-of-class token.</p>
 *
 * <h2>Fail-closed order</h2>
 * <ol>
 *   <li>compact-JWS shape and JSON: {@code MALFORMED_TOKEN};</li>
 *   <li>algorithm present and in the configured allowlist: {@code UNSUPPORTED_ALGORITHM};</li>
 *   <li>non-empty {@code kid}: {@code UNKNOWN_KEY};</li>
 *   <li>key lookup (refresh once for key rotation), signature: {@code KEY_SOURCE_UNAVAILABLE} or
 *       {@code UNKNOWN_KEY} or {@code BAD_SIGNATURE};</li>
 *   <li>issuer, audience, authorized party, resource scope, expiry, not-before, issue time and
 *       the 900-second lifetime ceiling.</li>
 * </ol>
 *
 * <p>Claims are never trusted before the signature verifies. The token string is not retained: it is
 * decoded inside one call and never stored, logged, rendered or placed in an exception message.
 * There is no fallback to the development stub, a cached authority, a default subject or a
 * "trusted caller" path when verification or key retrieval fails.</p>
 */
public final class JwtConsoleTokenDecoder implements ConsoleTokenDecoder {

    private static final int MAX_TOKEN_CHARACTERS = 16 * 1024;

    private final TrustedIssuerConfiguration configuration;
    private final JwksSource jwksSource;
    private final Clock clock;

    public JwtConsoleTokenDecoder(
            TrustedIssuerConfiguration configuration, JwksSource jwksSource, Clock clock) {
        this.configuration = Objects.requireNonNull(configuration, "configuration");
        this.jwksSource = Objects.requireNonNull(jwksSource, "jwksSource");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public ConsoleTokenClaims decode(String token) throws ConsoleAuthenticationException {
        return decode(token, clock.instant());
    }

    /**
     * Verify one token at an explicit request time. The authorizer passes the same instant it uses
     * for its own expiry decision, so decoder and policy cannot disagree about "now".
     */
    @Override
    public ConsoleTokenClaims decode(String token, Instant now)
            throws ConsoleAuthenticationException {
        Objects.requireNonNull(now, "now");
        if (token == null || token.isBlank() || token.length() > MAX_TOKEN_CHARACTERS) {
            throw refused(Reason.MALFORMED_TOKEN);
        }

        CompactJws jws;
        try {
            jws = CompactJws.parse(token);
        } catch (CompactJws.Malformed malformed) {
            throw refused(Reason.MALFORMED_TOKEN);
        }

        String algorithm = text(jws.header(), "alg");
        if (algorithm == null || algorithm.isBlank()
                || !configuration.allowedAlgorithms().contains(algorithm)) {
            // Covers "none", HMAC and every algorithm the configuration does not permit. No key is
            // consulted, so an attacker cannot steer key selection through the header.
            throw refused(Reason.UNSUPPORTED_ALGORITHM);
        }
        if (hasCriticalHeader(jws.header())) {
            throw refused(Reason.MALFORMED_TOKEN);
        }

        String kid = text(jws.header(), "kid");
        if (kid == null || kid.isBlank()) {
            throw refused(Reason.UNKNOWN_KEY);
        }
        PublicKey key = resolveKey(kid);
        verifySignature(algorithm, key, jws);

        return claimsOf(jws.payload(), now);
    }

    // ------------------------------------------------------------------ key handling

    /**
     * Current set first; on an unknown key id, refresh once and retry. That is the rotation path:
     * a new key id becomes usable after the reload, and a retired key id is refused because the
     * refreshed set no longer contains it. A key is never pinned by the first successful lookup.
     */
    private PublicKey resolveKey(String kid) {
        JsonWebKeySet current = load(false);
        PublicKey key = current.key(kid);
        if (key != null) {
            return key;
        }
        JsonWebKeySet refreshed = load(true);
        key = refreshed.key(kid);
        if (key == null) {
            throw refused(Reason.UNKNOWN_KEY);
        }
        return key;
    }

    private JsonWebKeySet load(boolean refresh) {
        try {
            JsonWebKeySet set = refresh ? jwksSource.refresh() : jwksSource.current();
            if (set == null) {
                throw refused(Reason.KEY_SOURCE_UNAVAILABLE);
            }
            return set;
        } catch (ConsoleAuthenticationException refused) {
            throw refused;
        } catch (JwksSourceException unavailable) {
            throw refused(Reason.KEY_SOURCE_UNAVAILABLE);
        } catch (RuntimeException unexpected) {
            // A source that fails unexpectedly is unavailable, never a reason to trust a token.
            throw refused(Reason.KEY_SOURCE_UNAVAILABLE);
        }
    }

    private static void verifySignature(String algorithm, PublicKey key, CompactJws jws) {
        if (!(key instanceof RSAPublicKey)) {
            throw refused(Reason.UNKNOWN_KEY);
        }
        try {
            Signature verifier = Signature.getInstance(signatureAlgorithm(algorithm));
            verifier.initVerify(key);
            verifier.update(jws.signingInput());
            if (!verifier.verify(jws.signature())) {
                throw refused(Reason.BAD_SIGNATURE);
            }
        } catch (GeneralSecurityException invalid) {
            throw refused(Reason.BAD_SIGNATURE);
        }
    }

    private static String signatureAlgorithm(String algorithm) {
        return switch (algorithm) {
            case "RS256" -> "SHA256withRSA";
            case "RS384" -> "SHA384withRSA";
            case "RS512" -> "SHA512withRSA";
            default -> throw new IllegalStateException("unreachable algorithm: " + algorithm);
        };
    }

    // ------------------------------------------------------------------ claims

    private ConsoleTokenClaims claimsOf(JsonNode payload, Instant now) {
        String issuer = text(payload, "iss");
        if (!configuration.issuer().equals(issuer)) {
            throw refused(Reason.WRONG_ISSUER);
        }

        List<String> audience = audience(payload);
        if (audience.size() != 1
                || !configuration.audience().equals(audience.get(0))
                || audience.contains(configuration.forbiddenAudience())) {
            throw refused(Reason.WRONG_AUDIENCE);
        }

        String authorizedParty = text(payload, "azp");
        if (!configuration.demoClientId().equals(authorizedParty)) {
            throw refused(Reason.WRONG_AUTHORIZED_PARTY);
        }

        List<String> scopes = scopes(payload);
        if (scopes.contains(configuration.forbiddenScope())) {
            throw refused(Reason.WRONG_SCOPE);
        }
        if (!scopes.contains(configuration.requiredScope())) {
            throw refused(Reason.MISSING_SCOPE);
        }

        Instant expiresAt = instant(payload, "exp", true);
        if (expiresAt == null || !now.isBefore(expiresAt)) {
            throw refused(Reason.EXPIRED);
        }
        Instant notBefore = instant(payload, "nbf", false);
        if (notBefore != null && now.isBefore(notBefore)) {
            throw refused(Reason.NOT_YET_VALID);
        }
        Instant issuedAt = instant(payload, "iat", true);
        if (issuedAt == null || now.isBefore(issuedAt)) {
            throw refused(Reason.NOT_YET_VALID);
        }
        Duration lifetime = Duration.between(issuedAt, expiresAt);
        if (lifetime.isNegative() || lifetime.isZero()
                || lifetime.compareTo(configuration.maxTokenLifetime()) > 0) {
            throw refused(Reason.TOKEN_LIFETIME_EXCEEDED);
        }

        return new ConsoleTokenClaims(
                issuer,
                audience,
                text(payload, "sub"),
                authorizedParty,
                scopes,
                issuedAt,
                notBefore,
                expiresAt);
    }

    private static List<String> audience(JsonNode payload) {
        JsonNode node = payload.get("aud");
        if (node == null || node.isNull()) {
            return List.of();
        }
        if (node.isTextual()) {
            String single = node.asText();
            return single.isBlank() ? List.of() : List.of(single);
        }
        if (!node.isArray()) {
            throw refused(Reason.MALFORMED_TOKEN);
        }
        List<String> values = new ArrayList<>();
        for (JsonNode element : node) {
            if (!element.isTextual() || element.asText().isBlank()) {
                throw refused(Reason.MALFORMED_TOKEN);
            }
            values.add(element.asText());
        }
        return List.copyOf(values);
    }

    private static List<String> scopes(JsonNode payload) {
        JsonNode node = payload.get("scope");
        if (node == null || node.isNull()) {
            return List.of();
        }
        Set<String> values = new LinkedHashSet<>();
        if (node.isTextual()) {
            for (String scope : node.asText().split("\\s+")) {
                if (!scope.isBlank()) {
                    values.add(scope);
                }
            }
        } else if (node.isArray()) {
            for (JsonNode element : node) {
                if (!element.isTextual() || element.asText().isBlank()) {
                    throw refused(Reason.MALFORMED_TOKEN);
                }
                values.add(element.asText());
            }
        } else {
            throw refused(Reason.MALFORMED_TOKEN);
        }
        return List.copyOf(values);
    }

    private static Instant instant(JsonNode payload, String claim, boolean required) {
        JsonNode node = payload.get(claim);
        if (node == null || node.isNull()) {
            if (required) {
                // A missing mandatory NumericDate is malformed, not "expired by default"; the
                // caller still receives a refusal, never a partial claim set.
                throw refused(Reason.MALFORMED_TOKEN);
            }
            return null;
        }
        if (!node.isNumber()) {
            throw refused(Reason.MALFORMED_TOKEN);
        }
        long epochSeconds = node.asLong();
        try {
            return Instant.ofEpochSecond(epochSeconds);
        } catch (RuntimeException invalid) {
            throw refused(Reason.MALFORMED_TOKEN);
        }
    }

    private static boolean hasCriticalHeader(JsonNode header) {
        JsonNode crit = header.get("crit");
        if (crit == null || crit.isNull()) {
            return false;
        }
        return !crit.isArray() || crit.size() > 0;
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value != null && value.isTextual() ? value.asText() : null;
    }

    private static ConsoleAuthenticationException refused(Reason reason) {
        return new ConsoleAuthenticationException(reason);
    }

    /** Fixed label; the decoder holds no secret and the message never includes the token. */
    @Override
    public String toString() {
        return "JwtConsoleTokenDecoder[issuer=" + configuration.issuer()
                + ", audience=" + configuration.audience()
                + ", algorithms=" + configuration.allowedAlgorithms().stream().sorted()
                        .map(algorithm -> algorithm.toLowerCase(Locale.ROOT))
                        .toList()
                + "]";
    }
}
