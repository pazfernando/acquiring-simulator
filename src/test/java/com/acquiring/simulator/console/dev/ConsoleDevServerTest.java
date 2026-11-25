package com.acquiring.simulator.console.dev;

import static org.assertj.core.api.Assertions.assertThat;

import com.acquiring.simulator.config.SimulatorProperties;
import com.acquiring.simulator.console.ConsoleAuthorizationRoute;
import com.acquiring.simulator.console.ConsoleIntegrationEntryPoint;
import com.acquiring.simulator.console.ConsoleOriginDenialSink;
import com.acquiring.simulator.console.ConsoleOriginVerificationGate;
import com.acquiring.simulator.domain.AuthorizationService;
import com.acquiring.simulator.domain.model.AuthorizationRecord;
import com.acquiring.simulator.domain.model.AuthorizationResult;
import com.acquiring.simulator.domain.model.AuthorizedContext;
import com.acquiring.simulator.domain.model.CredentialDecision;
import com.acquiring.simulator.domain.model.EvidenceEvent;
import com.acquiring.simulator.domain.model.Outcome;
import com.acquiring.simulator.domain.model.PurchaseRequest;
import com.acquiring.simulator.domain.model.RejectionAudit;
import com.acquiring.simulator.domain.model.Route;
import com.acquiring.simulator.domain.port.AuthorizationRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Real loopback HTTP tests for the development harness, with no Docker and no AWS.
 *
 * <p>The business service is a recording subclass, so these tests isolate the transport harness:
 * static files, runtime configuration, the dev-stub token endpoint, the loopback {@code Host} and
 * {@code Origin} guards, the method table and the exact delegation into the verified console route.
 * The route's own policy and ordering are covered by {@code ConsoleAuthorizationRouteTest}; the
 * atomic path over a real socket is covered by {@code ConsoleDevServerAtomicIntegrationTest}.</p>
 */
class ConsoleDevServerTest {

    private static final String ISSUER = ConsoleDevConfig.DEV_ISSUER;
    private static final String AUDIENCE = "console-api";
    private static final String SUBJECT = "demo-user";
    private static final String CLIENT = "console-demo";
    private static final String SCOPE = "console.demo";
    private static final Instant NOW = Instant.parse("2026-11-22T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final ObjectMapper JSON = new ObjectMapper();

    /** Synthetic local edge value; the harness injects it and the real gate verifies it. */
    private static final String DEV_ORIGIN_VERIFY =
            "SYNTHETIC-ORIGIN-VERIFY-0123456789abcdef";

    private static final String VALID_BODY = "{\"contractVersion\":\"1\",\"operation\":\"A\","
            + "\"correlationId\":\"CORR-DEV-1\",\"acquirerRef\":\"ACQ-SIM-001\","
            + "\"merchantRef\":\"MERCH-SIM-001\",\"terminalRef\":\"TERM-SIM-001\","
            + "\"paymentRef\":\"PAY-DEV-1\",\"credentialFixtureRef\":\"SIM-APP-001\","
            + "\"amount\":\"10.00\",\"currency\":\"USD\",\"entryMode\":\"simulated-contact\","
            + "\"cvm\":\"simulated-verified\",\"cryptogramPresent\":true}";

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2))
            .build();

    private RecordingService service;
    private ConsoleDevServer server;
    private String base;

    @BeforeEach
    void startHarness() throws Exception {
        service = new RecordingService();
        ConsoleDevConfig config = config("journal-test", "events-test");

        DevStubTokenIssuer tokens = new DevStubTokenIssuer(
                ISSUER, AUDIENCE, SUBJECT, CLIENT, SCOPE, CLOCK);
        DemoConsoleAuthorizerAdapter authorizer = new DemoConsoleAuthorizerAdapter(tokens);
        AuthorizedContext context = new AuthorizedContext(
                "ACQ-SIM-001", "MERCH-SIM-001", "SUB-DEMO", Set.of(Route.DOMESTIC));
        ConsoleAuthorizationRoute route = new ConsoleAuthorizationRoute(
                authorizer.authorizer(), principal -> context, service,
                com.acquiring.simulator.console.ConsoleRateLimitHook.PERMISSIVE, CLOCK);
        ConsoleOriginVerificationGate gate =
                ConsoleOriginVerificationGate.configured(DEV_ORIGIN_VERIFY);
        ConsoleIntegrationEntryPoint entryPoint = new ConsoleIntegrationEntryPoint(
                gate, route::handle, ConsoleOriginDenialSink.noop(), CLOCK,
                "console-dev-test");

        server = new ConsoleDevServer(config, Path.of("console-ui").toAbsolutePath(),
                entryPoint, DEV_ORIGIN_VERIFY, tokens);
        server.start();
        base = server.url();
    }

    @AfterEach
    void stopHarness() {
        if (server != null) {
            server.close();
        }
    }

    @Test
    void servesTheBrowserScreenWithSecurityHeaders() throws Exception {
        HttpResponse<String> response = get("/");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("content-type").orElse(""))
                .startsWith("text/html");
        assertThat(response.body()).contains("id=\"authorization-form\"");
        assertThat(response.body()).contains("NOT authentication");
        assertThat(response.body()).contains("not implemented");
        assertThat(response.headers().firstValue("content-security-policy").orElse(""))
                .contains("default-src 'none'")
                .contains("script-src 'self'")
                .contains("frame-ancestors 'none'");
        assertThat(response.headers().firstValue("x-content-type-options").orElse(""))
                .isEqualTo("nosniff");
        assertThat(response.headers().firstValue("referrer-policy").orElse(""))
                .isEqualTo("no-referrer");
        assertThat(response.headers().firstValue("access-control-allow-origin")).isEmpty();
    }

    @Test
    void servesTheModuleScriptWithAJavascriptMediaType() throws Exception {
        HttpResponse<String> response = get("/js/app.js");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("content-type").orElse(""))
                .startsWith("text/javascript");
        assertThat(response.body()).contains("createTokenClient");
        assertThat(response.body()).doesNotContain("localStorage");
    }

    @Test
    void servesRuntimeConfigThatTheTokenClientCanStart() throws Exception {
        HttpResponse<String> response = get("/config.json");

        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode body = JSON.readTree(response.body());
        assertThat(body.get("mode").asText()).isEqualTo("dev-stub");
        assertThat(body.get("stub").asBoolean()).isTrue();
        assertThat(body.get("tokenEndpoint").asText()).isEqualTo("/dev/v1/stub-token");
        assertThat(body.get("demoContext").get("acquirerRef").asText())
                .isEqualTo("ACQ-SIM-001");
        assertThat(body.get("history").get("serverReadImplemented").asBoolean()).isFalse();
    }

    @Test
    void issuesAnOpaqueDevelopmentToken() throws Exception {
        HttpResponse<String> response = post("/dev/v1/stub-token", null, null);

        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode body = JSON.readTree(response.body());
        assertThat(body.get("stub").asBoolean()).isTrue();
        assertThat(body.get("tokenType").asText()).isEqualTo("dev-stub");
        assertThat(body.get("accessToken").asText()).startsWith("devstub_");
        assertThat(body.get("expiresIn").asLong()).isEqualTo(900);
    }

    @Test
    void anAuthorizedSubmissionReachesTheRouteExactlyOnce() throws Exception {
        String token = acquireToken();
        HttpResponse<String> response = post(
                ConsoleAuthorizationRoute.PATH, VALID_BODY, "Bearer " + token);

        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode body = JSON.readTree(response.body());
        assertThat(body.get("outcome").asText()).isEqualTo("APPROVED");
        assertThat(body.get("authorizationReference").asText()).isEqualTo("AUTH-TEST-1");
        assertThat(service.calls.get()).isEqualTo(1);
    }

    @Test
    void theLocalEdgeReplacesACallerSuppliedOriginHeader() throws Exception {
        String token = acquireToken();
        HttpRequest request = HttpRequest.newBuilder(
                        URI.create(base + trimSlash(ConsoleAuthorizationRoute.PATH)))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + token)
                .header("X-Origin-Verify", "FORGED-BY-THE-CALLER-0123456789")
                .POST(HttpRequest.BodyPublishers.ofString(VALID_BODY, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        // The harness is the local edge: it overwrites the viewer value, so the real gate verifies
        // the edge value and the request proceeds. A second (duplicate) header would instead be a
        // DUPLICATE refusal.
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(service.calls.get()).isEqualTo(1);
    }

    @Test
    void aMissingCredentialIsRefusedBeforeTheServiceRuns() throws Exception {
        HttpResponse<String> response = post(ConsoleAuthorizationRoute.PATH, VALID_BODY, null);

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.body()).contains("UNAUTHENTICATED");
        assertThat(service.calls.get()).isZero();
    }

    @Test
    void aForeignOriginIsRefusedBeforeTheServiceRuns() throws Exception {
        String token = acquireToken();
        HttpResponse<String> response = postWithOrigin(
                ConsoleAuthorizationRoute.PATH, VALID_BODY, "Bearer " + token,
                "http://attacker.invalid:9999");

        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(service.calls.get()).isZero();
    }

    @Test
    void onlyDeclaredMethodsAndFilesAreServed() throws Exception {
        assertThat(get("/console/v1/authorizations").statusCode()).isEqualTo(405);
        assertThat(get("/dev/v1/stub-token").statusCode()).isEqualTo(405);
        assertThat(get("/pom.xml").statusCode()).isEqualTo(404);
        assertThat(get("/js/does-not-exist.js").statusCode()).isEqualTo(404);
        assertThat(get("/../pom.xml").statusCode()).isEqualTo(404);
    }

    // ------------------------------------------------------------------ helpers

    private String acquireToken() throws Exception {
        HttpResponse<String> response = post("/dev/v1/stub-token", null, null);
        return JSON.readTree(response.body()).get("accessToken").asText();
    }

    private HttpResponse<String> get(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(base + trimSlash(path)))
                .GET()
                .timeout(Duration.ofSeconds(5))
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String path, String body, String authorization)
            throws Exception {
        return postWithOrigin(path, body, authorization, null);
    }

    private HttpResponse<String> postWithOrigin(String path, String body, String authorization,
            String origin) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(
                        URI.create(base + trimSlash(path)))
                .timeout(Duration.ofSeconds(10))
                .header("Accept", "application/json");

        if (body != null) {
            builder.header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        } else {
            builder.POST(HttpRequest.BodyPublishers.noBody());
        }
        if (authorization != null) {
            builder.header("Authorization", authorization);
        }
        if (origin != null) {
            builder.header("Origin", origin);
        }
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static String trimSlash(String path) {
        return path.startsWith("/") ? path.substring(1) : path;
    }

    private static ConsoleDevConfig config(String journalTable, String eventTable) {
        return new ConsoleDevConfig(0, "http://127.0.0.1:8000", "us-east-1",
                "console-ui", "SIM-TEST", ISSUER, AUDIENCE, CLIENT, SCOPE, SUBJECT,
                "ACQ-SIM-001", "MERCH-SIM-001", "TERM-SIM-001", "USD",
                journalTable, eventTable);
    }

    private static SimulatorProperties properties() {
        return new SimulatorProperties(
                new SimulatorProperties.Authorization(Duration.ofHours(24),
                        Duration.ofSeconds(2)),
                new SimulatorProperties.Profile(
                        List.of(new SimulatorProperties.AcquirerFixture("ACQ-SIM-001",
                                List.of(Route.DOMESTIC))),
                        List.of(new SimulatorProperties.MerchantFixture(
                                "MERCH-SIM-001", "ACQ-SIM-001")),
                        List.of(new SimulatorProperties.TerminalFixture(
                                "TERM-SIM-001", "MERCH-SIM-001")),
                        List.of(new SimulatorProperties.CredentialRange("SIM-APP-",
                                CredentialDecision.APPROVE, Route.DOMESTIC))));
    }

    /** Keeps the token issuer wiring readable while still using the real claim policy. */
    private record DemoConsoleAuthorizerAdapter(DevStubTokenIssuer tokens) {

        com.acquiring.simulator.console.DemoConsoleAuthorizer authorizer() {
            return new com.acquiring.simulator.console.DemoConsoleAuthorizer(
                    ISSUER, AUDIENCE, SUBJECT, CLIENT, SCOPE, tokens.decoder());
        }
    }

    /** Recording service: proves whether the boundary reached business code, and how often. */
    private static final class RecordingService extends AuthorizationService {

        final AtomicInteger calls = new AtomicInteger();

        RecordingService() {
            super(new NoopRepository(), request -> null, properties(), CLOCK);
        }

        @Override
        public AuthorizationResult authorize(AuthorizedContext context, PurchaseRequest request) {
            calls.incrementAndGet();
            return new AuthorizationResult(Outcome.APPROVED, "AUTH-TEST-1");
        }
    }

    private static final class NoopRepository implements AuthorizationRepository {

        @Override public void claim(AuthorizationRecord record) {
            throw new AssertionError("claim must not run in this test");
        }

        @Override public void markDispatched(String authorizationId, String correlationId,
                Instant dispatchedAt) {
            throw new AssertionError("markDispatched must not run in this test");
        }

        @Override public void updateOutcome(String authorizationId, String state,
                String decisionSource, String reasonCategory, Instant outcomeDeterminedAt) {
            throw new AssertionError("updateOutcome must not run in this test");
        }

        @Override public Optional<AuthorizationRecord> findById(String id) {
            return Optional.empty();
        }

        @Override public Optional<AuthorizationRecord> findLatestByBusinessKey(String businessKey) {
            return Optional.empty();
        }

        @Override public List<EvidenceEvent> eventsFor(String authorizationId) {
            return List.of();
        }

        @Override public void appendEvent(EvidenceEvent event) {
            throw new AssertionError("appendEvent must not run in this test");
        }

        @Override public void appendRejection(RejectionAudit audit) {
            throw new AssertionError("appendRejection must not run in this test");
        }
    }
}
