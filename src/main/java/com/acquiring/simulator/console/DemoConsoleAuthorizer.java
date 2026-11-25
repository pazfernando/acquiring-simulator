package com.acquiring.simulator.console;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;

/**
 * Claim policy for the single-demo-subject console token.
 *
 * <p><strong>This is policy, not cryptography.</strong> Signature verification, key retrieval and
 * token decoding are delegated to {@link ConsoleTokenDecoder}; the real implementation is
 * {@code com.acquiring.simulator.console.jwt.JwtConsoleTokenDecoder}, wired by
 * {@code ConsoleTokenComposition}. This class never inspects token bytes and holds no key.</p>
 *
 * <p>Checks are applied in one fixed order and the first failure decides the refused
 * reason. Every check is an equality or membership test against explicitly supplied
 * configuration — {@code iss}, {@code aud}, {@code azp} (the single public client),
 * {@code sub} (the single demo subject), {@code scope}, and expiry — and anything that
 * does not match exactly is refused. There is no wildcard, no "trusted caller" bypass, no
 * default subject and no clock-skew allowance: skew handling belongs to the issuer and
 * authorizer configuration.</p>
 *
 * <p>The class verifies nothing about how a token was obtained (authorization code + PKCE),
 * nothing about revocation, and it enforces no maximum token lifetime; those are issuer
 * and platform responsibilities named as unverified in the D-5 reconciliation, and are not
 * silently claimed here.</p>
 */
public final class DemoConsoleAuthorizer implements ConsoleAuthorizer {

    private static final String BEARER = "bearer";

    private final String issuer;
    private final String audience;
    private final String demoSubject;
    private final String demoClientId;
    private final String requiredScope;
    private final ConsoleTokenDecoder decoder;

    /**
     * @param issuer the deployed realm issuer, exact match required
     * @param audience the console API resource, membership required
     * @param demoSubject the single configured demo subject, exact match required
     * @param demoClientId the single allowed authorized party ({@code console-demo})
     * @param requiredScope the console submission scope ({@code console.demo})
     * @param decoder the verification seam; must not be {@code null}
     */
    public DemoConsoleAuthorizer(String issuer, String audience, String demoSubject,
            String demoClientId, String requiredScope, ConsoleTokenDecoder decoder) {
        this.issuer = requireConfigured(issuer, "issuer");
        this.audience = requireConfigured(audience, "audience");
        this.demoSubject = requireConfigured(demoSubject, "demoSubject");
        this.demoClientId = requireConfigured(demoClientId, "demoClientId");
        this.requiredScope = requireConfigured(requiredScope, "requiredScope");
        this.decoder = Objects.requireNonNull(decoder, "decoder");
    }

    @Override
    public ConsolePrincipal authorize(String authorizationHeader, Instant now)
            throws ConsoleAuthenticationException {
        Objects.requireNonNull(now, "now");
        if (authorizationHeader == null || authorizationHeader.isBlank()) {
            throw new ConsoleAuthenticationException(
                    ConsoleAuthenticationException.Reason.MISSING_TOKEN);
        }
        String token = tokenOf(authorizationHeader);
        ConsoleTokenClaims claims = decode(token, now);

        if (!issuer.equals(claims.issuer())) {
            throw refused(ConsoleAuthenticationException.Reason.WRONG_ISSUER);
        }
        if (claims.audience() == null || !claims.audience().contains(audience)) {
            throw refused(ConsoleAuthenticationException.Reason.WRONG_AUDIENCE);
        }
        if (!demoClientId.equals(claims.authorizedParty())) {
            // Checked before the subject so a client_credentials token for any service
            // client is refused by class, even if it carried the console audience/scope.
            throw refused(ConsoleAuthenticationException.Reason.WRONG_AUTHORIZED_PARTY);
        }
        if (!demoSubject.equals(claims.subject())) {
            throw refused(ConsoleAuthenticationException.Reason.WRONG_SUBJECT);
        }
        if (claims.scopes() == null || !claims.scopes().contains(requiredScope)) {
            throw refused(ConsoleAuthenticationException.Reason.MISSING_SCOPE);
        }
        if (claims.expiresAt() == null || !now.isBefore(claims.expiresAt())) {
            throw refused(ConsoleAuthenticationException.Reason.EXPIRED);
        }
        if (claims.notBefore() != null && now.isBefore(claims.notBefore())) {
            throw refused(ConsoleAuthenticationException.Reason.NOT_YET_VALID);
        }
        if (claims.issuedAt() != null && now.isBefore(claims.issuedAt())) {
            throw refused(ConsoleAuthenticationException.Reason.NOT_YET_VALID);
        }
        return new ConsolePrincipal(claims.issuer(), claims.subject(),
                claims.authorizedParty(), audience, Set.copyOf(claims.scopes()));
    }

    private ConsoleTokenClaims decode(String token, Instant now) {
        if (token.isBlank()) {
            throw refused(ConsoleAuthenticationException.Reason.MALFORMED_TOKEN);
        }
        ConsoleTokenClaims claims;
        try {
            claims = decoder.decode(token, now);
        } catch (ConsoleAuthenticationException refused) {
            throw refused;
        } catch (RuntimeException decoderFailure) {
            // A decoder that fails unexpectedly is a refusal, never a partial identity.
            throw new ConsoleAuthenticationException(
                    ConsoleAuthenticationException.Reason.MALFORMED_TOKEN, decoderFailure);
        }
        if (claims == null) {
            throw refused(ConsoleAuthenticationException.Reason.MALFORMED_TOKEN);
        }
        return claims;
    }

    /** Requires a single {@code Bearer <token>} credential; anything else is malformed. */
    private static String tokenOf(String authorizationHeader) {
        String trimmed = authorizationHeader.trim();
        int space = trimmed.indexOf(' ');
        if (space <= 0) {
            throw refused(ConsoleAuthenticationException.Reason.MALFORMED_TOKEN);
        }
        String scheme = trimmed.substring(0, space);
        if (!BEARER.equals(scheme.toLowerCase(java.util.Locale.ROOT))) {
            throw refused(ConsoleAuthenticationException.Reason.MALFORMED_TOKEN);
        }
        String token = trimmed.substring(space + 1).trim();
        if (token.isEmpty() || token.indexOf(' ') >= 0) {
            throw refused(ConsoleAuthenticationException.Reason.MALFORMED_TOKEN);
        }
        return token;
    }

    private static ConsoleAuthenticationException refused(
            ConsoleAuthenticationException.Reason reason) {
        return new ConsoleAuthenticationException(reason);
    }

    private static String requireConfigured(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("console authorizer requires " + name);
        }
        return value;
    }
}
