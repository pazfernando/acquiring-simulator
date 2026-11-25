package com.acquiring.simulator.console.jwt;

import static org.assertj.core.api.Assertions.assertThat;

import com.acquiring.simulator.console.ConsoleAuthenticationException;
import com.acquiring.simulator.console.ConsoleAuthenticationException.Reason;
import com.acquiring.simulator.console.ConsoleTokenClaims;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * K1 real decoder, exercised with locally generated RSA keys only.
 *
 * <p>No live issuer, no network JWKS fetch, no Keycloak and no browser flow is involved. The key
 * source is an injected in-memory double that counts every consultation, so the run also shows the
 * decoder consults only that object. Green here means the verification logic and the refusals it
 * claims are exercised; it is not a deployed-issuer or interoperability result.</p>
 */
class JwtConsoleTokenDecoderTest {

    private static final Instant NOW = JwtTestSupport.NOW;
    private static final String KID = "key-1";
    private static final Clock FIXED = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void acceptsAValidTokenAndReturnsTheVerifiedClaimShape() {
        JwtConsoleTokenDecoder decoder = decoder(new JwtTestSupport.CountingJwksSource(
                JwtTestSupport.keySet(KID, JwtTestSupport.KEY_ONE.getPublic())));

        ConsoleTokenClaims claims = decoder.decode(
                JwtTestSupport.token(JwtTestSupport.KEY_ONE, KID,
                        JwtTestSupport.defaultClaims(NOW)),
                NOW);

        assertThat(claims.issuer()).isEqualTo(JwtTestSupport.ISSUER);
        assertThat(claims.audience()).containsExactly(JwtTestSupport.AUDIENCE);
        assertThat(claims.subject()).isEqualTo(JwtTestSupport.SUBJECT);
        assertThat(claims.authorizedParty()).isEqualTo(JwtTestSupport.CLIENT);
        assertThat(claims.scopes()).containsExactly(JwtTestSupport.SCOPE);
        assertThat(claims.issuedAt()).isEqualTo(NOW.minusSeconds(60));
        assertThat(claims.notBefore()).isEqualTo(NOW.minusSeconds(60));
        assertThat(claims.expiresAt()).isEqualTo(NOW.plusSeconds(600));
    }

    @Test
    void refusesAnUnknownAlgorithmWithoutConsultingAnyKey() {
        JwtTestSupport.CountingJwksSource source = new JwtTestSupport.CountingJwksSource(
                JwtTestSupport.keySet(KID, JwtTestSupport.KEY_ONE.getPublic()));
        JwtConsoleTokenDecoder decoder = decoder(source);

        Reason reason = reasonOf(decoder, JwtTestSupport.token(JwtTestSupport.KEY_ONE, KID,
                JwtTestSupport.defaultClaims(NOW), "HS256"));

        assertThat(reason).isEqualTo(Reason.UNSUPPORTED_ALGORITHM);
        assertThat(source.currentCalls()).isZero();
        assertThat(source.refreshCalls()).isZero();
    }

    @Test
    void refusesATokenWithNoAlgorithm() {
        Reason reason = reasonOf(decoderWithKeyOne(), JwtTestSupport.token(JwtTestSupport.KEY_ONE,
                KID, JwtTestSupport.defaultClaims(NOW), null));

        assertThat(reason).isEqualTo(Reason.UNSUPPORTED_ALGORITHM);
    }

    @Test
    void refusesAMissingKeyId() {
        JwtTestSupport.CountingJwksSource source = new JwtTestSupport.CountingJwksSource(
                JwtTestSupport.keySet(KID, JwtTestSupport.KEY_ONE.getPublic()));
        JwtConsoleTokenDecoder decoder = decoder(source);

        Reason reason = reasonOf(decoder, JwtTestSupport.token(JwtTestSupport.KEY_ONE, null,
                JwtTestSupport.defaultClaims(NOW)));

        assertThat(reason).isEqualTo(Reason.UNKNOWN_KEY);
        assertThat(source.currentCalls()).isZero();
    }

    @Test
    void refusesASignatureMismatch() {
        Reason reason = reasonOf(decoderWithKeyOne(), JwtTestSupport.token(
                JwtTestSupport.KEY_TWO, KID, JwtTestSupport.defaultClaims(NOW)));

        assertThat(reason).isEqualTo(Reason.BAD_SIGNATURE);
    }

    @Test
    void refusesATamperedPayloadEvenThoughTheHeaderAndKeyAreValid() {
        String token = JwtTestSupport.token(JwtTestSupport.KEY_ONE, KID,
                JwtTestSupport.defaultClaims(NOW));
        Map<String, Object> tampered = new LinkedHashMap<>(JwtTestSupport.defaultClaims(NOW));
        tampered.put("sub", "someone-else");

        assertThat(reasonOf(decoderWithKeyOne(),
                JwtTestSupport.withTamperedPayload(token, tampered)))
                .isEqualTo(Reason.BAD_SIGNATURE);
    }

    @Test
    void refusesAWrongIssuer() {
        Map<String, Object> claims = JwtTestSupport.defaultClaims(NOW);
        claims.put("iss", "https://other.test/realms/sim");

        assertThat(reasonOf(decoderWithKeyOne(),
                JwtTestSupport.token(JwtTestSupport.KEY_ONE, KID, claims)))
                .isEqualTo(Reason.WRONG_ISSUER);
    }

    @Test
    void refusesAWrongAudience() {
        Map<String, Object> claims = JwtTestSupport.defaultClaims(NOW);
        claims.put("aud", List.of("other-api"));

        assertThat(reasonOf(decoderWithKeyOne(),
                JwtTestSupport.token(JwtTestSupport.KEY_ONE, KID, claims)))
                .isEqualTo(Reason.WRONG_AUDIENCE);
    }

    @Test
    void refusesATokenCarryingBothResourceAudiences() {
        Map<String, Object> claims = JwtTestSupport.defaultClaims(NOW);
        claims.put("aud", List.of(JwtTestSupport.AUDIENCE, JwtTestSupport.WS_AUDIENCE));

        assertThat(reasonOf(decoderWithKeyOne(),
                JwtTestSupport.token(JwtTestSupport.KEY_ONE, KID, claims)))
                .isEqualTo(Reason.WRONG_AUDIENCE);
    }

    @Test
    void refusesAWrongAuthorizedParty() {
        Map<String, Object> claims = JwtTestSupport.defaultClaims(NOW);
        claims.put("azp", "svc-console");

        assertThat(reasonOf(decoderWithKeyOne(),
                JwtTestSupport.token(JwtTestSupport.KEY_ONE, KID, claims)))
                .isEqualTo(Reason.WRONG_AUTHORIZED_PARTY);
    }

    @Test
    void refusesTheWsScopeOnTheHttpToken() {
        Map<String, Object> claims = JwtTestSupport.defaultClaims(NOW);
        claims.put("scope", JwtTestSupport.SCOPE + " " + JwtTestSupport.WS_SCOPE);

        assertThat(reasonOf(decoderWithKeyOne(),
                JwtTestSupport.token(JwtTestSupport.KEY_ONE, KID, claims)))
                .isEqualTo(Reason.WRONG_SCOPE);
    }

    @Test
    void refusesAMissingRequiredScope() {
        Map<String, Object> claims = JwtTestSupport.defaultClaims(NOW);
        claims.put("scope", "openid");

        assertThat(reasonOf(decoderWithKeyOne(),
                JwtTestSupport.token(JwtTestSupport.KEY_ONE, KID, claims)))
                .isEqualTo(Reason.MISSING_SCOPE);
    }

    @Test
    void refusesAnExpiredToken() {
        Map<String, Object> claims = JwtTestSupport.defaultClaims(NOW);
        claims.put("exp", NOW.getEpochSecond());

        assertThat(reasonOf(decoderWithKeyOne(),
                JwtTestSupport.token(JwtTestSupport.KEY_ONE, KID, claims)))
                .isEqualTo(Reason.EXPIRED);
    }

    @Test
    void refusesATokenThatIsNotYetValid() {
        Map<String, Object> claims = JwtTestSupport.defaultClaims(NOW);
        claims.put("nbf", NOW.plusSeconds(60).getEpochSecond());

        assertThat(reasonOf(decoderWithKeyOne(),
                JwtTestSupport.token(JwtTestSupport.KEY_ONE, KID, claims)))
                .isEqualTo(Reason.NOT_YET_VALID);
    }

    @Test
    void refusesAnIssueTimeInTheFuture() {
        Map<String, Object> claims = JwtTestSupport.defaultClaims(NOW);
        claims.put("iat", NOW.plusSeconds(60).getEpochSecond());
        claims.remove("nbf");

        assertThat(reasonOf(decoderWithKeyOne(),
                JwtTestSupport.token(JwtTestSupport.KEY_ONE, KID, claims)))
                .isEqualTo(Reason.NOT_YET_VALID);
    }

    @Test
    void refusesALifetimeAboveTheConfiguredCeiling() {
        Map<String, Object> claims = JwtTestSupport.defaultClaims(NOW);
        claims.put("iat", NOW.getEpochSecond());
        claims.remove("nbf");
        claims.put("exp", NOW.plusSeconds(901).getEpochSecond());

        assertThat(reasonOf(decoderWithKeyOne(),
                JwtTestSupport.token(JwtTestSupport.KEY_ONE, KID, claims)))
                .isEqualTo(Reason.TOKEN_LIFETIME_EXCEEDED);
    }

    @Test
    void refusesAMissingExpiry() {
        Map<String, Object> claims = JwtTestSupport.defaultClaims(NOW);
        claims.remove("exp");

        assertThat(reasonOf(decoderWithKeyOne(),
                JwtTestSupport.token(JwtTestSupport.KEY_ONE, KID, claims)))
                .isEqualTo(Reason.MALFORMED_TOKEN);
    }

    @Test
    void refusesMalformedCompactJwsShapes() {
        JwtConsoleTokenDecoder decoder = decoderWithKeyOne();

        assertThat(reasonOf(decoder, "not-a-jwt")).isEqualTo(Reason.MALFORMED_TOKEN);
        assertThat(reasonOf(decoder, "a.b")).isEqualTo(Reason.MALFORMED_TOKEN);
        assertThat(reasonOf(decoder, "a.b.c")).isEqualTo(Reason.MALFORMED_TOKEN);
        assertThat(reasonOf(decoder, "")).isEqualTo(Reason.MALFORMED_TOKEN);
    }

    @Test
    void refusesAnUnsupportedCriticalHeader() {
        var header = JwtTestSupport.JSON.createObjectNode();
        header.put("alg", "RS256");
        header.put("kid", KID);
        header.putArray("crit").add("exp");

        String token = JwtTestSupport.tokenWithHeader(JwtTestSupport.KEY_ONE, header,
                JwtTestSupport.defaultClaims(NOW));

        assertThat(reasonOf(decoderWithKeyOne(), token)).isEqualTo(Reason.MALFORMED_TOKEN);
    }

    @Test
    void acceptsARotatedKeyOnlyAfterRefreshingTheKeySet() {
        JwtTestSupport.CountingJwksSource source = new JwtTestSupport.CountingJwksSource(
                JwtTestSupport.keySet(KID, JwtTestSupport.KEY_ONE.getPublic()),
                JwtTestSupport.keySet("key-2", JwtTestSupport.KEY_TWO.getPublic()));
        JwtConsoleTokenDecoder decoder = decoder(source);

        ConsoleTokenClaims claims = decoder.decode(JwtTestSupport.token(
                JwtTestSupport.KEY_TWO, "key-2", JwtTestSupport.defaultClaims(NOW)), NOW);

        assertThat(claims.subject()).isEqualTo(JwtTestSupport.SUBJECT);
        assertThat(source.currentCalls()).isEqualTo(1);
        assertThat(source.refreshCalls()).isEqualTo(1);
    }

    @Test
    void refusesTheRetiredOldKeyAfterRotation() {
        JwtTestSupport.CountingJwksSource source = new JwtTestSupport.CountingJwksSource(
                JwtTestSupport.keySet("key-2", JwtTestSupport.KEY_TWO.getPublic()),
                JwtTestSupport.keySet("key-2", JwtTestSupport.KEY_TWO.getPublic()));
        JwtConsoleTokenDecoder decoder = decoder(source);

        Reason reason = reasonOf(decoder, JwtTestSupport.token(
                JwtTestSupport.KEY_ONE, KID, JwtTestSupport.defaultClaims(NOW)));

        assertThat(reason).isEqualTo(Reason.UNKNOWN_KEY);
        assertThat(source.refreshCalls()).isEqualTo(1);
    }

    @Test
    void failsClosedWhenTheKeySourceIsUnavailable() {
        JwtTestSupport.CountingJwksSource source = new JwtTestSupport.CountingJwksSource(
                JwtTestSupport.keySet(KID, JwtTestSupport.KEY_ONE.getPublic())).failCurrent();

        assertThat(reasonOf(decoder(source), JwtTestSupport.token(
                JwtTestSupport.KEY_ONE, KID, JwtTestSupport.defaultClaims(NOW))))
                .isEqualTo(Reason.KEY_SOURCE_UNAVAILABLE);
    }

    @Test
    void failsClosedWhenTheRotationRefreshIsUnavailable() {
        JwtTestSupport.CountingJwksSource source = new JwtTestSupport.CountingJwksSource(
                JwtTestSupport.keySet("key-2", JwtTestSupport.KEY_TWO.getPublic()))
                .failRefresh();

        assertThat(reasonOf(decoder(source), JwtTestSupport.token(
                JwtTestSupport.KEY_ONE, KID, JwtTestSupport.defaultClaims(NOW))))
                .isEqualTo(Reason.KEY_SOURCE_UNAVAILABLE);
    }

    /**
     * The unit-test isolation proof: the decoder consults only the injected source (one
     * {@code current()} call, no refresh for a known key), and that source performs no I/O. The
     * decoder has no HTTP client and handles no issuer URL; the composition is what would choose a
     * network source.
     */
    @Test
    void verifiesUsingOnlyTheInjectedKeySourceAndPerformsNoNetworkIo() {
        JwtTestSupport.CountingJwksSource source = new JwtTestSupport.CountingJwksSource(
                JwtTestSupport.keySet(KID, JwtTestSupport.KEY_ONE.getPublic()));
        String token = JwtTestSupport.token(JwtTestSupport.KEY_ONE, KID,
                JwtTestSupport.defaultClaims(NOW));

        new JwtConsoleTokenDecoder(JwtTestSupport.configuration(), source, FIXED)
                .decode(token, NOW);

        assertThat(source.currentCalls()).isEqualTo(1);
        assertThat(source.refreshCalls()).isZero();
    }

    @Test
    void neverRendersOrRepeatsTheTokenInItsOwnText() {
        String token = JwtTestSupport.token(JwtTestSupport.KEY_ONE, KID,
                JwtTestSupport.defaultClaims(NOW));
        JwtConsoleTokenDecoder decoder = decoderWithKeyOne();

        ConsoleTokenClaims claims = decoder.decode(token, NOW);

        assertThat(decoder.toString())
                .doesNotContain(token)
                .contains(JwtTestSupport.ISSUER)
                .contains(JwtTestSupport.AUDIENCE);
        assertThat(claims.toString()).doesNotContain(token);

        String badToken = JwtTestSupport.token(JwtTestSupport.KEY_TWO, KID,
                JwtTestSupport.defaultClaims(NOW));
        try {
            decoder.decode(badToken, NOW);
            throw new AssertionError("the token should have been refused");
        } catch (ConsoleAuthenticationException refused) {
            assertThat(refused.getMessage()).doesNotContain(badToken);
            assertThat(refused.getMessage()).contains("BAD_SIGNATURE");
        }
    }

    // ------------------------------------------------------------------ helpers

    private static JwtConsoleTokenDecoder decoderWithKeyOne() {
        return decoder(new JwtTestSupport.CountingJwksSource(
                JwtTestSupport.keySet(KID, JwtTestSupport.KEY_ONE.getPublic())));
    }

    private static JwtConsoleTokenDecoder decoder(JwtTestSupport.CountingJwksSource source) {
        return new JwtConsoleTokenDecoder(JwtTestSupport.configuration(), source, FIXED);
    }

    private static Reason reasonOf(JwtConsoleTokenDecoder decoder, String token) {
        try {
            decoder.decode(token, NOW);
            throw new AssertionError("the token should have been refused");
        } catch (ConsoleAuthenticationException refused) {
            return refused.reason();
        }
    }
}
