package com.acquiring.simulator.console;

import java.time.Instant;
import java.util.List;

/**
 * The standard claim set a token verifier exposes <em>after</em> cryptographic
 * verification.
 *
 * <p>This type defines **no token encoding and no wire format**. The field names are the
 * registered JWT/OIDC claims the approved contracts already name ({@code iss}, {@code aud},
 * {@code sub}, {@code azp}, {@code scope}, {@code iat}, {@code nbf}, {@code exp}); whether
 * a real token is a signed JWS, how its {@code scope} string is split and how its
 * signature is checked against the issuer's keys are all decided by the future Gateway
 * authorizer, not here.</p>
 *
 * <p>A claim the verifier did not supply stays {@code null} (or an empty list). The
 * policy refuses a missing claim rather than defaulting it, so an incomplete claim set
 * fails closed.</p>
 */
public record ConsoleTokenClaims(
        String issuer,
        List<String> audience,
        String subject,
        String authorizedParty,
        List<String> scopes,
        Instant issuedAt,
        Instant notBefore,
        Instant expiresAt) {

    public ConsoleTokenClaims {
        audience = audience == null ? null : List.copyOf(audience);
        scopes = scopes == null ? null : List.copyOf(scopes);
    }

    /** Never renders a token; no claim here is credential material. */
    @Override
    public String toString() {
        return "ConsoleTokenClaims[issuer=" + issuer
                + ", audience=" + audience
                + ", subject=" + subject
                + ", authorizedParty=" + authorizedParty
                + ", scopes=" + scopes
                + ", issuedAt=" + issuedAt
                + ", notBefore=" + notBefore
                + ", expiresAt=" + expiresAt + "]";
    }

    /** Defensive: callers cannot mutate the audience list through this copy. */
    @Override
    public List<String> audience() {
        return audience == null ? null : List.copyOf(audience);
    }

    @Override
    public List<String> scopes() {
        return scopes == null ? null : List.copyOf(scopes);
    }
}
