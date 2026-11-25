package com.acquiring.simulator.console.dev;

import com.acquiring.simulator.console.ConsoleAuthenticationException;
import com.acquiring.simulator.console.ConsoleTokenClaims;
import com.acquiring.simulator.console.ConsoleTokenDecoder;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Mints the local development console token. <strong>This is NOT authentication.</strong>
 *
 * <p>It produces an opaque random string and keeps the claims in an in-process map. No signature
 * is produced or verified, no issuer is contacted, no JWKS exists and no key material is used.
 * Anyone who can reach the loopback harness can ask for a token, and the same token is accepted
 * until it expires. A passing test of this type proves the development seam works; it proves
 * nothing about who is calling.</p>
 *
 * <p>The token carries no structure on purpose. It is not a JWT and must not be described as one:
 * it is a random handle whose only meaning is the in-memory map entry. It is never logged, never
 * rendered and never persisted by the harness or the browser module that consumes it.</p>
 *
 * <p>Claims satisfy {@link com.acquiring.simulator.console.DemoConsoleAuthorizer}'s policy so the
 * <em>same</em> claim checks run locally: exact issuer, audience membership, authorized party,
 * subject, scope, and expiry. The decoder below only resolves the handle; the policy still
 * decides. A real deployment replaces both with the platform authorizer.</p>
 */
public final class DevStubTokenIssuer {

    /** Lifetime of one local development token. Short by design; not a session guarantee. */
    private static final Duration LIFETIME = Duration.ofMinutes(15);

    /** Distinguishes this opaque handle from a serialized JWT at a glance. */
    private static final String PREFIX = "devstub_";

    private final String issuer;
    private final String audience;
    private final String subject;
    private final String clientId;
    private final String scope;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();
    private final Map<String, ConsoleTokenClaims> outstanding = new ConcurrentHashMap<>();

    public DevStubTokenIssuer(String issuer, String audience, String subject,
            String clientId, String scope, Clock clock) {
        this.issuer = requireText(issuer, "issuer");
        this.audience = requireText(audience, "audience");
        this.subject = requireText(subject, "subject");
        this.clientId = requireText(clientId, "clientId");
        this.scope = requireText(scope, "scope");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** One issued handle and its advertised lifetime; the value is a credential. */
    public record IssuedToken(String accessToken, long expiresInSeconds) {

        /** Never renders the token value. */
        @Override
        public String toString() {
            return "IssuedToken[accessToken=<redacted>, expiresInSeconds=" + expiresInSeconds + "]";
        }
    }

    /** Issue a token at the configured clock's current instant. */
    public IssuedToken issue() {
        return issue(clock.instant());
    }

    /**
     * Issue a token at an explicit instant. The claims are stored, so the decoder can resolve the
     * handle without guessing or re-deriving anything from token text.
     */
    public IssuedToken issue(Instant now) {
        Objects.requireNonNull(now, "now");
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        ConsoleTokenClaims claims = new ConsoleTokenClaims(
                issuer,
                List.of(audience),
                subject,
                clientId,
                List.of(scope),
                now,
                now,
                now.plus(LIFETIME));
        outstanding.put(token, claims);
        return new IssuedToken(token, LIFETIME.toSeconds());
    }

    /**
     * The decoder half of the seam. It resolves only handles this process issued and reports an
     * expired handle as {@code EXPIRED}. It deliberately performs no signature check because there
     * is no signature: that absence is the point, not an omission.
     */
    public ConsoleTokenDecoder decoder() {
        return token -> {
            if (token == null || !token.startsWith(PREFIX)) {
                throw malformed();
            }
            ConsoleTokenClaims claims = outstanding.get(token);
            if (claims == null) {
                throw malformed();
            }
            if (claims.expiresAt() != null && !clock.instant().isBefore(claims.expiresAt())) {
                outstanding.remove(token);
                throw new ConsoleAuthenticationException(
                        ConsoleAuthenticationException.Reason.EXPIRED);
            }
            return claims;
        };
    }

    /** Number of unexpired handles still held. Bounded by the harness lifetime, not a quota. */
    public int outstandingTokens() {
        Instant now = clock.instant();
        outstanding.entrySet().removeIf(entry ->
                entry.getValue().expiresAt() != null && !now.isBefore(entry.getValue().expiresAt()));
        return outstanding.size();
    }

    /** The exact lifetime advertised to the browser. */
    public long lifetimeSeconds() {
        return LIFETIME.toSeconds();
    }

    private static ConsoleAuthenticationException malformed() {
        return new ConsoleAuthenticationException(
                ConsoleAuthenticationException.Reason.MALFORMED_TOKEN);
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("dev stub token issuer requires " + name);
        }
        return value;
    }
}
