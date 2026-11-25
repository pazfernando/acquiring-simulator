package com.acquiring.simulator.console.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.acquiring.simulator.console.ConsoleAuthenticationException;
import com.acquiring.simulator.console.ConsoleAuthenticationException.Reason;
import com.acquiring.simulator.console.ConsoleAuthorizer;
import com.acquiring.simulator.console.ConsoleAuthorizationRoute;
import com.acquiring.simulator.console.ConsoleHttpRequest;
import com.acquiring.simulator.console.ConsoleHttpResponse;
import com.acquiring.simulator.console.ConsoleRateLimitHook;
import com.acquiring.simulator.domain.AuthorizationService;
import com.acquiring.simulator.domain.model.AuthorizationResult;
import com.acquiring.simulator.domain.model.AuthorizedContext;
import com.acquiring.simulator.domain.model.Outcome;
import com.acquiring.simulator.domain.model.PurchaseRequest;
import com.acquiring.simulator.domain.model.Route;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * End-to-end wiring of the real decoder behind the unchanged console submission route.
 *
 * <p>No network, Docker or AWS: keys and tokens are generated locally, and the key source is the
 * in-memory double. These tests show that the composition seam is what the route consumes and that
 * a refused token never reaches the business service; they are not a deployed-issuer result.</p>
 */
class ConsoleTokenCompositionTest {

    private static final Instant NOW = JwtTestSupport.NOW;
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final String KID = "key-1";

    private static final AuthorizedContext CONTEXT = new AuthorizedContext(
            "ACQ-SIM-001", "MERCH-SIM-001", "SUB-DEMO", Set.of(Route.DOMESTIC));

    @Test
    void aValidTokenWiredThroughTheCompositionReachesTheServiceExactlyOnce() {
        RecordingService service = new RecordingService();
        ConsoleAuthorizer authorizer = composition(new JwtTestSupport.CountingJwksSource(
                JwtTestSupport.keySet(KID, JwtTestSupport.KEY_ONE.getPublic())));
        String token = JwtTestSupport.token(JwtTestSupport.KEY_ONE, KID,
                JwtTestSupport.defaultClaims(NOW));

        ConsoleHttpResponse response = route(authorizer, service)
                .handle(request("Bearer " + token));

        assertThat(response.status()).isEqualTo(200);
        assertThat(service.calls).isEqualTo(1);
        assertThat(service.context).isSameAs(CONTEXT);
        assertThat(text(response)).doesNotContain(token);
    }

    @Test
    void thePolicyCoreStillRefusesASubjectOtherThanTheConfiguredDemoSubject() {
        ConsoleAuthorizer authorizer = composition(new JwtTestSupport.CountingJwksSource(
                JwtTestSupport.keySet(KID, JwtTestSupport.KEY_ONE.getPublic())));
        Map<String, Object> claims = JwtTestSupport.defaultClaims(NOW);
        claims.put("sub", "someone-else");
        String token = JwtTestSupport.token(JwtTestSupport.KEY_ONE, KID, claims);

        assertThatThrownBy(() -> authorizer.authorize("Bearer " + token, NOW))
                .isInstanceOfSatisfying(ConsoleAuthenticationException.class, refused ->
                        assertThat(refused.reason()).isEqualTo(Reason.WRONG_SUBJECT));
    }

    @Test
    void aWsAudienceTokenIsUnauthenticatedAndNeverReachesTheService() {
        RecordingService service = new RecordingService();
        ConsoleAuthorizer authorizer = composition(new JwtTestSupport.CountingJwksSource(
                JwtTestSupport.keySet(KID, JwtTestSupport.KEY_ONE.getPublic())));
        Map<String, Object> claims = JwtTestSupport.defaultClaims(NOW);
        claims.put("aud", List.of(JwtTestSupport.WS_AUDIENCE));
        String token = JwtTestSupport.token(JwtTestSupport.KEY_ONE, KID, claims);

        ConsoleHttpResponse response = route(authorizer, service)
                .handle(request("Bearer " + token));

        assertThat(response.status()).isEqualTo(401);
        assertThat(text(response)).contains("\"error\":\"UNAUTHENTICATED\"");
        assertThat(service.calls).isZero();
    }

    @Test
    void aWsScopeTokenIsContextDeniedAndNeverReachesTheService() {
        RecordingService service = new RecordingService();
        ConsoleAuthorizer authorizer = composition(new JwtTestSupport.CountingJwksSource(
                JwtTestSupport.keySet(KID, JwtTestSupport.KEY_ONE.getPublic())));
        Map<String, Object> claims = JwtTestSupport.defaultClaims(NOW);
        claims.put("scope", JwtTestSupport.SCOPE + " " + JwtTestSupport.WS_SCOPE);
        String token = JwtTestSupport.token(JwtTestSupport.KEY_ONE, KID, claims);

        ConsoleHttpResponse response = route(authorizer, service)
                .handle(request("Bearer " + token));

        assertThat(response.status()).isEqualTo(403);
        assertThat(text(response)).contains("\"error\":\"CONTEXT_DENIED\"");
        assertThat(service.calls).isZero();
    }

    @Test
    void aKeySourceOutageRefusesTheTokenAndNeverReachesTheService() {
        RecordingService service = new RecordingService();
        ConsoleAuthorizer authorizer = composition(new JwtTestSupport.CountingJwksSource(
                JwtTestSupport.keySet(KID, JwtTestSupport.KEY_ONE.getPublic())).failCurrent());
        String token = JwtTestSupport.token(JwtTestSupport.KEY_ONE, KID,
                JwtTestSupport.defaultClaims(NOW));

        ConsoleHttpResponse response = route(authorizer, service)
                .handle(request("Bearer " + token));

        assertThat(response.status()).isEqualTo(401);
        assertThat(service.calls).isZero();
    }

    // ------------------------------------------------------------------ helpers

    private static ConsoleAuthorizer composition(JwtTestSupport.CountingJwksSource source) {
        return ConsoleTokenComposition.authorizer(JwtTestSupport.configuration(), source, CLOCK);
    }

    private static ConsoleAuthorizationRoute route(
            ConsoleAuthorizer authorizer, RecordingService service) {
        return new ConsoleAuthorizationRoute(authorizer, principal -> CONTEXT, service,
                ConsoleRateLimitHook.PERMISSIVE, CLOCK);
    }

    private static ConsoleHttpRequest request(String authorization) {
        Map<String, List<String>> headers = new LinkedHashMap<>();
        headers.put("Content-Type", List.of("application/json"));
        headers.put("Authorization", List.of(authorization));
        return new ConsoleHttpRequest("POST", ConsoleAuthorizationRoute.PATH, headers, validBody());
    }

    private static byte[] validBody() {
        String json = "{\"contractVersion\":\"1\",\"operation\":\"A\","
                + "\"correlationId\":\"CORR-K1-1\","
                + "\"acquirerRef\":\"ACQ-SIM-001\","
                + "\"merchantRef\":\"MERCH-SIM-001\","
                + "\"terminalRef\":\"TERM-SIM-001\","
                + "\"paymentRef\":\"PURCHASE-K1\","
                + "\"credentialFixtureRef\":\"SIM-APP-001\","
                + "\"amount\":\"10.00\",\"currency\":\"USD\","
                + "\"entryMode\":\"simulated-contact\","
                + "\"cvm\":\"simulated-verified\","
                + "\"cryptogramPresent\":true}";
        return json.getBytes(StandardCharsets.US_ASCII);
    }

    private static String text(ConsoleHttpResponse response) {
        return new String(response.body(), StandardCharsets.UTF_8);
    }

    private static final class RecordingService extends AuthorizationService {
        private int calls;
        private AuthorizedContext context;

        RecordingService() {
            super(null, null, null, Clock.systemUTC());
        }

        @Override
        public AuthorizationResult authorize(AuthorizedContext context, PurchaseRequest request) {
            calls++;
            this.context = context;
            return AuthorizationResult.of(Outcome.APPROVED, "AUTH-K1-1");
        }
    }
}
