package com.acquiring.simulator.console;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * N-02 origin-verification boundary slice: the gate, its fail-closed initialization and the
 * integration entry point's ordering.
 *
 * <p>These tests use no network, no Docker, no AWS and no Keycloak. They assert classification and
 * ordering only. A green result is evidence about the application boundary; it is not evidence that
 * CloudFront injects or overwrites the header, that any deployed edge exists, or that a caller was
 * authenticated.</p>
 */
class ConsoleOriginVerificationGateTest {

    /** A synthetic injected value: documentary symbol, never a real deployment value. */
    private static final String EXPECTED = "SYNTHETIC-ORIGIN-VERIFY-0123456789ABCDEF";
    private static final String APP_VERSION = "console-app-0.0.1-synthetic";
    private static final Instant NOW = Instant.parse("2026-11-23T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void classificationIsClosedAndExhaustive() {
        ConsoleOriginVerificationGate gate = ConsoleOriginVerificationGate.configured(EXPECTED);

        assertThat(gate.evaluate(requestWithoutHeader()).reason())
                .isEqualTo(ConsoleOriginVerificationGate.Reason.ABSENT);
        assertThat(gate.evaluate(requestWith("")).reason())
                .isEqualTo(ConsoleOriginVerificationGate.Reason.EMPTY);
        assertThat(gate.evaluate(requestWith("   ")).reason())
                .isEqualTo(ConsoleOriginVerificationGate.Reason.EMPTY);
        assertThat(gate.evaluate(requestWith(" \t ")).reason())
                .isEqualTo(ConsoleOriginVerificationGate.Reason.EMPTY);
        assertThat(gate.evaluate(requestWithTwoValues(EXPECTED, EXPECTED)).reason())
                .isEqualTo(ConsoleOriginVerificationGate.Reason.DUPLICATE);
        assertThat(gate.evaluate(requestWithTwoValues(EXPECTED, "SYNTHETIC-WRONG")).reason())
                .isEqualTo(ConsoleOriginVerificationGate.Reason.DUPLICATE);
        assertThat(gate.evaluate(requestWith(EXPECTED + ",SYNTHETIC-WRONG")).reason())
                .isEqualTo(ConsoleOriginVerificationGate.Reason.DUPLICATE);
        assertThat(gate.evaluate(requestWithCaseVariantHeaderName(EXPECTED)).reason())
                .isEqualTo(ConsoleOriginVerificationGate.Reason.DUPLICATE);
        assertThat(gate.evaluate(requestWith(oneCharacterDifferent())).reason())
                .isEqualTo(ConsoleOriginVerificationGate.Reason.MISMATCH);
        assertThat(gate.evaluate(requestWith(EXPECTED.toLowerCase())).reason())
                .isEqualTo(ConsoleOriginVerificationGate.Reason.MISMATCH);
        assertThat(gate.evaluate(requestWith(" " + EXPECTED)).reason())
                .isEqualTo(ConsoleOriginVerificationGate.Reason.MISMATCH);
        assertThat(gate.evaluate(requestWith(EXPECTED)).reason())
                .isEqualTo(ConsoleOriginVerificationGate.Reason.VERIFIED);
        assertThat(gate.evaluate(requestWith(EXPECTED)).decision())
                .isEqualTo(ConsoleOriginVerificationGate.Decision.CONTINUE);
        assertThat(gate.evaluate(requestWith(oneCharacterDifferent())).decision())
                .isEqualTo(ConsoleOriginVerificationGate.Decision.REFUSE);
    }

    @Test
    void initializationFailsClosedOnAnyUnusableConfiguredValue() {
        assertThatThrownBy(() -> ConsoleOriginVerificationGate.configured(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("non-empty");
        assertThatThrownBy(() -> ConsoleOriginVerificationGate.configured(""))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("non-empty");
        assertThatThrownBy(() -> ConsoleOriginVerificationGate.configured("too-short"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least 32");
        assertThatThrownBy(() -> ConsoleOriginVerificationGate.configured(
                "SYNTHETIC-ORIGIN-VERIFY-0123456789 ABC"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("alphabet");
        assertThatThrownBy(() -> ConsoleOriginVerificationGate.from(name -> null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(ConsoleOriginVerificationGate.ENVIRONMENT_VARIABLE);
        assertThatThrownBy(() -> ConsoleOriginVerificationGate.from(
                name -> "(SYNTHETIC)-ORIGIN-VERIFY-0123456789ABCDEF)"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("alphabet");

        ConsoleOriginVerificationGate gate = ConsoleOriginVerificationGate.from(
                name -> ConsoleOriginVerificationGate.ENVIRONMENT_VARIABLE.equals(name)
                        ? EXPECTED : null);
        assertThat(gate.evaluate(requestWith(EXPECTED)).reason())
                .isEqualTo(ConsoleOriginVerificationGate.Reason.VERIFIED);
    }

    @Test
    void everyRefusalReturnsTheSameFixedBytesWithoutEcho() {
        ConsoleOriginVerificationGate gate = ConsoleOriginVerificationGate.configured(EXPECTED);
        List<ConsoleOriginDenial> records = new ArrayList<>();
        ConsoleIntegrationEntryPoint entry = entry(gate, request -> {
            throw new AssertionError("a refused request must not reach the application");
        }, records);

        List<ConsoleHttpResponse> refusals = List.of(
                entry.handle(requestWithoutHeader(), identity()),
                entry.handle(requestWith(""), identity()),
                entry.handle(requestWithTwoValues(EXPECTED, EXPECTED), identity()),
                entry.handle(requestWith(oneCharacterDifferent()), identity()),
                entry.handle(requestWith(EXPECTED + ",SYNTHETIC-WRONG"), identity()));

        for (ConsoleHttpResponse refusal : refusals) {
            assertThat(refusal.status()).isEqualTo(403);
            assertThat(text(refusal)).isEqualTo(ConsoleIntegrationEntryPoint.DENIAL_BODY);
            assertThat(refusal.headers().get("Content-Type"))
                    .containsExactly("application/json");
            assertThat(refusal.headers().keySet())
                    .noneMatch(name -> name.toLowerCase().startsWith("access-control-allow"));
            assertThat(text(refusal)).doesNotContain(EXPECTED);
            assertThat(text(refusal)).doesNotContain("SYNTHETIC-WRONG");
            assertThat(text(refusal)).doesNotContain("ABSENT");
            assertThat(text(refusal)).doesNotContain("MISMATCH");
            assertThat(text(refusal)).doesNotContain(APP_VERSION);
        }
        assertThat(records).hasSize(5);
        assertThat(records).extracting(ConsoleOriginDenial::reason)
                .containsExactly("ABSENT", "EMPTY", "DUPLICATE", "MISMATCH", "DUPLICATE");
    }

    @Test
    void refusalEmitsExactlyOneClosedRecordAndContainsASinkFailure() {
        ConsoleOriginVerificationGate gate = ConsoleOriginVerificationGate.configured(EXPECTED);
        List<ConsoleOriginDenial> records = new ArrayList<>();
        ConsoleIntegrationEntryPoint entry = entry(gate, request -> {
            throw new AssertionError("a refused request must not reach the application");
        }, records);

        ConsoleHttpResponse response = entry.handle(requestWith(EXPECTED.toLowerCase()),
                new ConsoleRequestIdentity("REQ-1", "203.0.113.7"));

        assertThat(response.status()).isEqualTo(403);
        assertThat(records).hasSize(1);
        ConsoleOriginDenial record = records.getFirst();
        assertThat(record.fields().keySet()).containsExactly(
                "event", "reason", "requestId", "sourceIp", "host", "httpMethod", "path",
                "timestamp", "appVersion");
        assertThat(record.event()).isEqualTo("console_origin_verification_denied");
        assertThat(record.reason()).isEqualTo("MISMATCH");
        assertThat(record.requestId()).isEqualTo("REQ-1");
        assertThat(record.sourceIp()).isEqualTo("203.0.113.7");
        assertThat(record.host()).isEqualTo("api.sim.synthetic");
        assertThat(record.httpMethod()).isEqualTo("GET");
        assertThat(record.path()).isEqualTo("/console/v1/transactions/TX-001/events");
        assertThat(record.timestamp()).isEqualTo(NOW.toString());
        assertThat(record.appVersion()).isEqualTo(APP_VERSION);
        assertThat(record.fields().values()).noneMatch(value ->
                value != null && value.contains(EXPECTED.toLowerCase()));

        ConsoleIntegrationEntryPoint throwingSink = new ConsoleIntegrationEntryPoint(gate,
                request -> {
                    throw new AssertionError("a refused request must not reach the application");
                },
                denial -> {
                    throw new IllegalStateException("sink unavailable");
                }, CLOCK, APP_VERSION);
        ConsoleHttpResponse stillRefused = throwingSink.handle(requestWithoutHeader(), identity());
        assertThat(stillRefused.status()).isEqualTo(403);
        assertThat(text(stillRefused)).isEqualTo(ConsoleIntegrationEntryPoint.DENIAL_BODY);
    }

    @Test
    void callerControlledRecordFieldsAreBoundedAndNeverEchoed() {
        ConsoleOriginVerificationGate gate = ConsoleOriginVerificationGate.configured(EXPECTED);
        List<ConsoleOriginDenial> records = new ArrayList<>();
        ConsoleIntegrationEntryPoint entry = entry(gate, request -> {
            throw new AssertionError("a refused request must not reach the application");
        }, records);

        Map<String, List<String>> headers = new LinkedHashMap<>();
        headers.put("Host", List.of("evil.example\nX-Injected: 1"));
        headers.put(ConsoleOriginVerificationGate.HEADER_NAME, List.of(oneCharacterDifferent()));
        ConsoleHttpRequest hostile = new ConsoleHttpRequest("POST", "/console/v1/transactions/"
                + "T".repeat(4096) + "/events", headers, new byte[0]);

        ConsoleHttpResponse response = entry.handle(hostile, ConsoleRequestIdentity.UNKNOWN);

        assertThat(response.status()).isEqualTo(403);
        assertThat(records).hasSize(1);
        assertThat(records.getFirst().host()).isEqualTo(ConsoleOriginDenial.INVALID);
        assertThat(records.getFirst().path()).isEqualTo(ConsoleOriginDenial.INVALID);
        assertThat(records.getFirst().requestId()).isNull();
        assertThat(records.getFirst().sourceIp()).isNull();
        assertThat(text(response)).doesNotContain("evil.example");
    }

    @Test
    void entryPointCannotExistWithoutTheGate() {
        assertThatThrownBy(() -> new ConsoleIntegrationEntryPoint(null,
                request -> ConsoleHttpResponse.empty(204), ConsoleOriginDenialSink.noop(),
                CLOCK, APP_VERSION))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("origin verification gate");
        assertThatThrownBy(() -> new ConsoleIntegrationEntryPoint(
                ConsoleOriginVerificationGate.configured(EXPECTED), null,
                ConsoleOriginDenialSink.noop(), CLOCK, APP_VERSION))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("application");
        assertThatThrownBy(() -> new ConsoleIntegrationEntryPoint(
                ConsoleOriginVerificationGate.configured(EXPECTED), request -> null,
                ConsoleOriginDenialSink.noop(), CLOCK, " "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("app version");
    }

    @Test
    void refusalPerformsNoDispatchCredentialRateLimitOrRegistryAction() {
        CountingRateLimit rateLimit = new CountingRateLimit();
        RecordingService service = new RecordingService();
        AtomicInteger credentialCalls = new AtomicInteger();
        ConsoleAuthorizer countedAuthorizer = (header, now) -> {
            credentialCalls.incrementAndGet();
            return principal();
        };
        ConsoleContextRegistry countedRegistry = new ConsoleContextRegistry() {
            @Override
            public AuthorizedContext contextFor(ConsolePrincipal ignored) {
                throw new AssertionError("the registry must not be read on a boundary refusal");
            }
        };
        ConsoleAuthorizationRoute route = new ConsoleAuthorizationRoute(countedAuthorizer,
                countedRegistry, service, rateLimit, CLOCK);
        ConsoleIntegrationEntryPoint entry = entry(
                ConsoleOriginVerificationGate.configured(EXPECTED), route::handle,
                new ArrayList<>());

        ConsoleHttpResponse response = entry.handle(requestWith(oneCharacterDifferent()),
                identity());

        assertThat(response.status()).isEqualTo(403);
        assertThat(credentialCalls).hasValue(0);
        assertThat(rateLimit.calls.get()).isZero();
        assertThat(service.calls).isZero();
    }

    @Test
    void verifiedOriginIsNotAuthenticationAndTheCredentialSeamStillRefuses() {
        RecordingService service = new RecordingService();
        DemoConsoleAuthorizer authorizer = new DemoConsoleAuthorizer(
                "https://auth.sim.test/realms/sim", "console-api", "demo-user", "console-demo",
                "console.demo", token -> {
                    throw new ConsoleAuthenticationException(
                            ConsoleAuthenticationException.Reason.MALFORMED_TOKEN);
                });
        ConsoleAuthorizationRoute route = new ConsoleAuthorizationRoute(authorizer,
                ignored -> new AuthorizedContext("ACQ-SIM-001", "MERCH-SIM-001", "SUB-DEMO",
                        Set.of(Route.DOMESTIC)),
                service, ConsoleRateLimitHook.PERMISSIVE, CLOCK);
        ConsoleIntegrationEntryPoint entry = entry(
                ConsoleOriginVerificationGate.configured(EXPECTED), route::handle,
                new ArrayList<>());

        ConsoleHttpResponse response = entry.handle(submissionRequestWithoutToken(), identity());
        assertThat(response.status()).isEqualTo(401);
        assertThat(text(response)).contains("\"error\":\"UNAUTHENTICATED\"");
        assertThat(service.calls).isZero();
    }

    @Test
    void verifiedOriginReachesTheApplicationUnchanged() {
        List<ConsoleHttpRequest> dispatched = new ArrayList<>();
        ConsoleIntegrationEntryPoint entry = entry(
                ConsoleOriginVerificationGate.configured(EXPECTED), request -> {
                    dispatched.add(request);
                    return ConsoleHttpResponse.empty(204);
                }, new ArrayList<>());

        ConsoleHttpResponse response = entry.handle(requestWith(EXPECTED), identity());

        assertThat(response.status()).isEqualTo(204);
        assertThat(dispatched).hasSize(1);
        assertThat(dispatched.getFirst().headerValues(
                ConsoleOriginVerificationGate.HEADER_NAME)).containsExactly(EXPECTED);
    }

    // ------------------------------------------------------------------ fixtures

    private static ConsoleIntegrationEntryPoint entry(ConsoleOriginVerificationGate gate,
            java.util.function.Function<ConsoleHttpRequest, ConsoleHttpResponse> application,
            List<ConsoleOriginDenial> records) {
        return new ConsoleIntegrationEntryPoint(gate, application, records::add, CLOCK,
                APP_VERSION);
    }

    private static ConsoleHttpRequest submissionRequestWithoutToken() {
        Map<String, List<String>> headers = new LinkedHashMap<>();
        headers.put("Host", List.of("api.sim.synthetic"));
        headers.put("Content-Type", List.of("application/json"));
        headers.put(ConsoleOriginVerificationGate.HEADER_NAME, List.of(EXPECTED));
        return new ConsoleHttpRequest("POST", ConsoleAuthorizationRoute.PATH, headers,
                new byte[0]);
    }

    private static ConsoleRequestIdentity identity() {
        return new ConsoleRequestIdentity("REQ-1", "203.0.113.7");
    }

    private static ConsoleHttpRequest requestWithoutHeader() {
        return new ConsoleHttpRequest("GET", "/console/v1/transactions/TX-001/events",
                headers(null), new byte[0]);
    }

    private static ConsoleHttpRequest requestWith(String value) {
        return new ConsoleHttpRequest("GET", "/console/v1/transactions/TX-001/events",
                headers(Map.of(ConsoleOriginVerificationGate.HEADER_NAME, List.of(value))),
                new byte[0]);
    }

    private static ConsoleHttpRequest requestWithTwoValues(String first, String second) {
        return new ConsoleHttpRequest("GET", "/console/v1/transactions/TX-001/events",
                headers(Map.of(ConsoleOriginVerificationGate.HEADER_NAME,
                        List.of(first, second))), new byte[0]);
    }

    private static ConsoleHttpRequest requestWithCaseVariantHeaderName(String value) {
        Map<String, List<String>> headers = new LinkedHashMap<>();
        headers.put("Host", List.of("api.sim.synthetic"));
        headers.put("X-Origin-Verify", List.of(value));
        headers.put("x-origin-verify", List.of(value));
        return new ConsoleHttpRequest("GET", "/console/v1/transactions/TX-001/events",
                headers, new byte[0]);
    }

    private static Map<String, List<String>> headers(Map<String, List<String>> extra) {
        Map<String, List<String>> headers = new LinkedHashMap<>();
        headers.put("Host", List.of("api.sim.synthetic"));
        if (extra != null) {
            headers.putAll(extra);
        }
        return headers;
    }

    private static String oneCharacterDifferent() {
        char[] characters = EXPECTED.toCharArray();
        characters[characters.length - 1] = characters[characters.length - 1] == 'F' ? 'E' : 'F';
        return new String(characters);
    }

    private static ConsolePrincipal principal() {
        return new ConsolePrincipal("https://auth.sim.test/realms/sim", "demo-user", "console-demo",
                "console-api", Set.of("console.demo"));
    }

    private static String text(ConsoleHttpResponse response) {
        return new String(response.body(), StandardCharsets.UTF_8);
    }

    private static final class CountingRateLimit implements ConsoleRateLimitHook {
        private final AtomicInteger calls = new AtomicInteger();

        @Override
        public Decision evaluate(ConsolePrincipal principal, Instant now) {
            calls.incrementAndGet();
            return Decision.PERMITTED;
        }
    }

    /** Records the single business call instead of executing it. */
    private static final class RecordingService extends AuthorizationService {
        private int calls;

        RecordingService() {
            super(null, null, null, Clock.systemUTC());
        }

        @Override
        public AuthorizationResult authorize(AuthorizedContext context, PurchaseRequest request) {
            calls++;
            return AuthorizationResult.of(Outcome.APPROVED, "AUTH-UNIT-1");
        }
    }
}
