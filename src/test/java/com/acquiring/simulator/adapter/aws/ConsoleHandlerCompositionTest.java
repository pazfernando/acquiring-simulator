package com.acquiring.simulator.adapter.aws;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.acquiring.simulator.console.ConsoleAuthorizationRoute;
import com.acquiring.simulator.console.ConsoleHistoryRoute;
import com.acquiring.simulator.console.ConsoleHttpResponse;
import com.acquiring.simulator.console.ConsoleIntegrationEntryPoint;
import com.acquiring.simulator.console.ConsoleOriginDenial;
import com.acquiring.simulator.console.ConsoleOriginVerificationGate;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

/**
 * Composed-path tests for the deployed console Lambda: {@link ConsoleHandler} over the cold-start
 * composition in {@link ConsoleRuntime}, not the gate in isolation.
 *
 * <p>They assert the boundary control actually runs where a request arrives: an absent header is
 * refused with the fixed {@code 403} before the application is invoked, a verified header reaches
 * the application, and an unusable {@code EDGE_ORIGIN_VERIFY} or missing version prevents the
 * handler from being constructed at all.</p>
 *
 * <p>The last test records the deliberately limited composition: the two existing route shapes pass
 * the gate and then answer {@code 503 UNAVAILABLE}, because the authorizer, registry and cursor-key
 * collaborators are undelivered (K1, A-4). It asserts that honest answer, not business behaviour.</p>
 */
class ConsoleHandlerCompositionTest {

    private static final String ORIGIN_VERIFY = "SYNTHETIC-ORIGIN-VERIFY-0123456789abcdef";
    private static final String APP_VERSION = "42";
    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-11-23T12:00:00Z"), ZoneOffset.UTC);

    private final AtomicInteger applicationCalls = new AtomicInteger();
    private final List<ConsoleOriginDenial> denials = new ArrayList<>();

    @Test
    void refusesAnAbsentHeaderBeforeTheApplicationIsInvoked() throws Exception {
        ConsoleHandler handler = composedHandler(200);

        JsonNode response = call(handler, ConsoleAuthorizationRoute.METHOD,
                ConsoleAuthorizationRoute.PATH, null);

        assertThat(response.path("statusCode").asInt()).isEqualTo(403);
        assertThat(response.path("body").asText())
                .isEqualTo(ConsoleIntegrationEntryPoint.DENIAL_BODY);
        assertThat(applicationCalls.get()).isZero();
        assertThat(denials).hasSize(1);
        ConsoleOriginDenial denial = denials.get(0);
        assertThat(denial.reason()).isEqualTo("ABSENT");
        assertThat(denial.requestId()).isEqualTo("REQ-1");
        assertThat(denial.appVersion()).isEqualTo(APP_VERSION);
        assertThat(denial.path()).isEqualTo(ConsoleAuthorizationRoute.PATH);
    }

    @Test
    void admitsAVerifiedHeaderToTheApplication() throws Exception {
        ConsoleHandler handler = composedHandler(200);

        JsonNode response = call(handler, ConsoleAuthorizationRoute.METHOD,
                ConsoleAuthorizationRoute.PATH, ORIGIN_VERIFY);

        assertThat(response.path("statusCode").asInt()).isEqualTo(200);
        assertThat(applicationCalls.get()).isEqualTo(1);
        assertThat(denials).isEmpty();
    }

    @Test
    void refusesAMismatchedHeaderBeforeTheApplicationIsInvoked() throws Exception {
        ConsoleHandler handler = composedHandler(200);

        JsonNode response = call(handler, ConsoleAuthorizationRoute.METHOD,
                ConsoleAuthorizationRoute.PATH, "SYNTHETIC-ORIGIN-VERIFY-fedcba9876543210");

        assertThat(response.path("statusCode").asInt()).isEqualTo(403);
        assertThat(applicationCalls.get()).isZero();
        assertThat(denials).singleElement()
                .extracting(ConsoleOriginDenial::reason).isEqualTo("MISMATCH");
    }

    @Test
    void deployedCompositionAnswersUnavailableOnlyAfterTheGateVerifies() throws Exception {
        ConsoleHandler handler = new ConsoleHandler(resolver());

        JsonNode refused = call(handler, ConsoleAuthorizationRoute.METHOD,
                ConsoleAuthorizationRoute.PATH, null);
        assertThat(refused.path("statusCode").asInt()).isEqualTo(403);

        JsonNode authorized = call(handler, ConsoleAuthorizationRoute.METHOD,
                ConsoleAuthorizationRoute.PATH, ORIGIN_VERIFY);
        assertThat(authorized.path("statusCode").asInt()).isEqualTo(503);

        JsonNode history = call(handler, ConsoleHistoryRoute.METHOD,
                "/console/v1/transactions/TXN-1/events", ORIGIN_VERIFY);
        assertThat(history.path("statusCode").asInt()).isEqualTo(503);

        JsonNode unknown = call(handler, "GET", "/console/v1/unknown", ORIGIN_VERIFY);
        assertThat(unknown.path("statusCode").asInt()).isEqualTo(404);
    }

    @Test
    void malformedConfigurationPreventsHandlerConstruction() {
        assertThatThrownBy(() -> new ConsoleHandler(environment(null, APP_VERSION)))
                .as("absent EDGE_ORIGIN_VERIFY")
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new ConsoleHandler(environment("short", APP_VERSION)))
                .as("short EDGE_ORIGIN_VERIFY")
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new ConsoleHandler(environment(
                "SYNTHETIC ORIGIN VERIFY 0123456789abcdef", APP_VERSION)))
                .as("out-of-alphabet EDGE_ORIGIN_VERIFY")
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new ConsoleHandler(environment(ORIGIN_VERIFY, null)))
                .as("absent AWS_LAMBDA_FUNCTION_VERSION")
                .isInstanceOf(IllegalStateException.class);
    }

    // ------------------------------------------------------------------ composition helpers

    private ConsoleHandler composedHandler(int applicationStatus) {
        ConsoleIntegrationEntryPoint entryPoint = ConsoleRuntime.compose(resolver(),
                request -> {
                    applicationCalls.incrementAndGet();
                    return ConsoleHttpResponse.empty(applicationStatus);
                },
                denials::add, CLOCK);
        return new ConsoleHandler(entryPoint);
    }

    private static Function<String, String> resolver() {
        return environment(ORIGIN_VERIFY, APP_VERSION);
    }

    private static Function<String, String> environment(String originVerify, String appVersion) {
        Map<String, String> values = new LinkedHashMap<>();
        if (originVerify != null) {
            values.put(ConsoleOriginVerificationGate.ENVIRONMENT_VARIABLE, originVerify);
        }
        if (appVersion != null) {
            values.put(ConsoleRuntime.APP_VERSION_VARIABLE, appVersion);
        }
        return values::get;
    }

    private static JsonNode call(ConsoleHandler handler, String method, String path,
            String originVerify) throws IOException {
        Map<String, Object> http = new LinkedHashMap<>();
        http.put("method", method);
        http.put("sourceIp", "198.51.100.7");

        Map<String, Object> requestContext = new LinkedHashMap<>();
        requestContext.put("requestId", "REQ-1");
        requestContext.put("http", http);

        Map<String, Object> event = new LinkedHashMap<>();
        event.put("version", "2.0");
        event.put("rawPath", path);
        event.put("requestContext", requestContext);
        if (originVerify != null) {
            event.put("headers", Map.of(
                    ConsoleOriginVerificationGate.HEADER_NAME, originVerify));
        }

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        handler.handleRequest(new ByteArrayInputStream(
                WireJson.write(event).getBytes(StandardCharsets.UTF_8)), output, null);
        return WireJson.tree(output.toString(StandardCharsets.UTF_8));
    }
}
