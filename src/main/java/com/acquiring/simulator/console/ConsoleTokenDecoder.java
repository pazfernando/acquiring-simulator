package com.acquiring.simulator.console;

import java.time.Instant;

/**
 * Cryptographic/decoding seam for the console authorizer.
 *
 * <p><strong>This is a seam, not an authorizer.</strong> The real implementation is
 * {@code com.acquiring.simulator.console.jwt.JwtConsoleTokenDecoder}: it verifies a compact JWS
 * signature against a configured JWKS and checks issuer, audience, authorized party, resource
 * scope, expiry, not-before, issue time and the configured lifetime ceiling before returning the
 * claim view. The labelled local development double
 * ({@code com.acquiring.simulator.console.dev.DevStubTokenIssuer#decoder()}) is still a test
 * harness only and is not authentication.</p>
 *
 * <p>The contract of this seam is narrow on purpose:</p>
 * <ul>
 *   <li>accept a bearer token string that a verified authorizer would release;</li>
 *   <li>return the standard claim view, or throw {@link ConsoleAuthenticationException};</li>
 *   <li>never return partially verified claims — a decoder that cannot verify must
 *       refuse rather than degrade.</li>
 * </ul>
 *
 * <p>{@link DemoConsoleAuthorizer} still applies the issuer/audience/authorized-party/subject/
 * scope/expiry policy and its fixed order to whatever this seam returns. Signature and key
 * retrieval are the decoder's job; the current-mandate intersection remains the registry's.</p>
 */
@FunctionalInterface
public interface ConsoleTokenDecoder {

    /**
     * @param token the bearer token value without the scheme prefix
     * @return the verified claim view; never {@code null}
     * @throws ConsoleAuthenticationException when the token cannot be verified or parsed
     */
    ConsoleTokenClaims decode(String token) throws ConsoleAuthenticationException;

    /**
     * Verify at the same instant the surrounding policy uses for its own expiry decision.
     *
     * <p>The default keeps a decoder that has no clock of its own (such as the labelled local
     * development double) working through the original method. The real JWT decoder overrides it so
     * signature/claim time checks and the policy's expiry check cannot disagree about "now".</p>
     */
    default ConsoleTokenClaims decode(String token, Instant now)
            throws ConsoleAuthenticationException {
        return decode(token);
    }
}
