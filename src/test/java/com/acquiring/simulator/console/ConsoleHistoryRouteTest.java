package com.acquiring.simulator.console;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.acquiring.simulator.MutableClock;
import com.acquiring.simulator.domain.event.EventContext;
import com.acquiring.simulator.domain.event.EventDurability;
import com.acquiring.simulator.domain.event.EventHead;
import com.acquiring.simulator.domain.event.EventSourceWrapper;
import com.acquiring.simulator.domain.event.RecordedEvent;
import com.acquiring.simulator.domain.event.StoredEvent;
import com.acquiring.simulator.domain.model.AuthorizedContext;
import com.acquiring.simulator.domain.model.Route;
import com.acquiring.simulator.domain.port.EventHistoryReader;
import com.acquiring.simulator.domain.port.EventStore;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.Set;import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

/**
 * Read-only history route slice (D-6/R-2, O6): fixed snapshot, signed cursor, re-authentication and
 * truthful coverage.
 *
 * <p>The event store is a double whose {@code append} and {@code find} throw, so every green page
 * proves the route took the read-only path. No network, Docker, AWS, Keycloak or WebSocket is used.
 * A green result is evidence about the route's ordering and coverage arithmetic, not that a deployed
 * API Gateway/Lambda/CloudFront route exists, that the store is strongly consistent in AWS, or that a
 * cursor key has a real custody arrangement.</p>
 */
class ConsoleHistoryRouteTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String ENVIRONMENT = "SIM";
    private static final String ACQUIRER = "ACQ-A";
    private static final String MERCHANT = "MERCHANT-A";
    private static final String TRANSACTION = "TX-001";
    private static final String BEARER = "Bearer demo-token";
    private static final String ISSUER = "https://auth.example.invalid/realms/acq-DEMO";
    private static final String SUBJECT = "demo-user";
    private static final String AUTHORIZED_PARTY = "console-demo";
    private static final String AUDIENCE = "console-api";
    private static final String TERMINAL = "TERMINAL-A";
    private static final String CURSOR_KEY = "synthetic-cursor-signing-key-0123456789";
    private static final Instant NOW = Instant.parse("2026-11-23T12:00:00Z");
    private static final EventContext CONTEXT =
            new EventContext(ENVIRONMENT, ACQUIRER, MERCHANT, TRANSACTION);
    private static final AuthorizedContext MANDATE =
            new AuthorizedContext(ACQUIRER, MERCHANT, "SUB-DEMO", TERMINAL,
                    Set.of(Route.DOMESTIC));

    private final MutableClock clock = new MutableClock(NOW);
    private final ConsoleHistoryCursorCodec codec = new ConsoleHistoryCursorCodec(
            "cursor-key-1", CURSOR_KEY.getBytes(StandardCharsets.UTF_8));

    @Test
    void routeCannotExistWithoutItsRequiredCollaborators() {
        StubStore store = new StubStore();
        StubReader reader = new StubReader();
        ConsoleContextRegistry registry = ignored -> MANDATE;

        assertThatThrownBy(() -> new ConsoleHistoryRoute(null, registry, store, reader, codec,
                ENVIRONMENT, 900, clock))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("authorizer");
        assertThatThrownBy(() -> new ConsoleHistoryRoute(authorizer(), null, store, reader, codec,
                ENVIRONMENT, 900, clock))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("context registry");
        assertThatThrownBy(() -> new ConsoleHistoryRoute(authorizer(), registry, null, reader,
                codec, ENVIRONMENT, 900, clock))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("event store");
        assertThatThrownBy(() -> new ConsoleHistoryRoute(authorizer(), registry, store, null,
                codec, ENVIRONMENT, 900, clock))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("event reader");
        assertThatThrownBy(() -> new ConsoleHistoryRoute(authorizer(), registry, store, reader,
                null, ENVIRONMENT, 900, clock))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("cursor codec");
        assertThatThrownBy(() -> new ConsoleHistoryRoute(authorizer(), registry, store, reader,
                codec, " ", 900, clock))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("environment");
        assertThatThrownBy(() -> new ConsoleHistoryRoute(authorizer(), registry, store, reader,
                codec, ENVIRONMENT, 0, clock))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ttl");
        assertThatThrownBy(() -> new ConsoleHistoryRoute(authorizer(), registry, store, reader,
                codec, ENVIRONMENT, 901, clock))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ttl");
    }

    @Test
    void newSnapshotReturnsTheFixedHeadAndCompleteCoverage() {
        StubStore store = head(3, 1, 0);
        StubReader reader = rows(1, 2, 3);

        ConsoleHttpResponse response = route(store, reader, authorizer())
                .handle(get("?snapshot=new&after=0"));

        assertThat(response.status()).isEqualTo(200);
        JsonNode body = body(response);
        assertThat(body.get("version").asInt()).isEqualTo(1);
        assertThat(body.get("transactionId").asText()).isEqualTo(TRANSACTION);
        assertThat(body.get("snapshotHead").asLong()).isEqualTo(3);
        assertThat(body.get("expiryRevision").asLong()).isZero();
        assertThat(body.get("firstRetainedSequence").asLong()).isEqualTo(1);
        assertThat(body.get("events")).hasSize(3);
        assertThat(body.get("events").get(0).get("sourceSequence").asLong()).isEqualTo(1);
        assertThat(body.get("coveredThrough").asLong()).isEqualTo(3);
        assertThat(body.get("expiredRanges")).isEmpty();
        assertThat(body.get("gapRanges")).isEmpty();
        assertThat(body.get("nextCursor").isNull()).isTrue();
        assertThat(body.get("completeThroughSnapshot").asBoolean()).isTrue();
        assertThat(body.get("retentionTruncated").asBoolean()).isFalse();
        assertThat(reader.calls).isEqualTo(1);
        assertThat(reader.observedThrough).isEqualTo(3);
    }

    @Test
    void continuationKeepsTheFrozenSnapshotWhenTheHeadAdvances() {
        StubStore store = head(3, 1, 0);
        StubReader reader = rows(1, 2, 3);
        String cursor = mintCursor(3, 1, 0, 1, TRANSACTION, SUBJECT, fingerprintOf(MANDATE));

        // The durable head and rows move ahead after the snapshot was frozen.
        store.head = head(5, 1, 0).head;
        StubReader advanced = rows(1, 2, 3, 4, 5);
        reader.rows.clear();
        reader.rows.putAll(advanced.rows);

        ConsoleHttpResponse response = route(store, reader, authorizer())
                .handle(get("?cursor=" + cursor));

        assertThat(response.status()).isEqualTo(200);
        JsonNode body = body(response);
        assertThat(body.get("snapshotHead").asLong()).isEqualTo(3);
        assertThat(body.get("events")).hasSize(2);
        assertThat(body.get("events").get(0).get("sourceSequence").asLong()).isEqualTo(2);
        assertThat(body.get("events").get(1).get("sourceSequence").asLong()).isEqualTo(3);
        assertThat(body.get("coveredThrough").asLong()).isEqualTo(3);
        assertThat(body.get("completeThroughSnapshot").asBoolean()).isTrue();
        assertThat(reader.observedThrough).isEqualTo(3);
    }

    @Test
    void tamperedCursorIsFourHundredInvalidCursor() {
        StubStore store = head(3, 1, 0);
        String cursor = mintCursor(3, 1, 0, 1, TRANSACTION, SUBJECT, fingerprintOf(MANDATE));
        String tampered = cursor.substring(0, cursor.length() - 1)
                + (cursor.endsWith("A") ? "B" : "A");

        ConsoleHttpResponse response = route(store, rows(1, 2, 3), authorizer())
                .handle(get("?cursor=" + tampered));

        assertThat(response.status()).isEqualTo(400);
        assertThat(body(response).get("error").asText()).isEqualTo("INVALID_CURSOR");
        assertThat(body(response).get("action").isNull()).isTrue();
        assertThat(store.headCalls).isZero();
    }

    @Test
    void expiredCursorIsFourTenResyncRequiredWithAction() {
        StubStore store = head(3, 1, 0);
        String cursor = mintCursor(3, 1, 0, 1, TRANSACTION, SUBJECT, fingerprintOf(MANDATE));
        clock.advance(java.time.Duration.ofSeconds(901));

        ConsoleHttpResponse response = route(store, rows(1, 2, 3), authorizer())
                .handle(get("?cursor=" + cursor));

        assertThat(response.status()).isEqualTo(410);
        assertThat(body(response).get("error").asText()).isEqualTo("RESYNC_REQUIRED");
        assertThat(body(response).get("action").asText()).isEqualTo("RESYNC_REQUIRED");
    }

    @Test
    void changedExpiryRevisionForcesFourTen() {
        StubStore store = head(3, 1, 1);
        String cursor = mintCursor(3, 1, 0, 1, TRANSACTION, SUBJECT, fingerprintOf(MANDATE));

        ConsoleHttpResponse response = route(store, rows(1, 2, 3), authorizer())
                .handle(get("?cursor=" + cursor));

        assertThat(response.status()).isEqualTo(410);
        assertThat(body(response).get("action").asText()).isEqualTo("RESYNC_REQUIRED");
        assertThat(response.toString()).doesNotContain("events");
    }

    @Test
    void truncatedFirstRetainedSequenceForcesFourTen() {
        StubStore store = head(3, 2, 0);
        String cursor = mintCursor(3, 1, 0, 1, TRANSACTION, SUBJECT, fingerprintOf(MANDATE));

        ConsoleHttpResponse response = route(store, rows(2, 3), authorizer())
                .handle(get("?cursor=" + cursor));

        assertThat(response.status()).isEqualTo(410);
    }

    @Test
    void retiredMandateGenerationForcesFourTenAndNoPayload() {
        StubStore store = head(3, 1, 0);
        String cursor = mintCursor(3, 1, 0, 1, TRANSACTION, SUBJECT, fingerprintOf(MANDATE));
        AuthorizedContext reissued = new AuthorizedContext(ACQUIRER, MERCHANT, "SUB-DEMO-REISSUED",
                TERMINAL, Set.of(Route.DOMESTIC));

        ConsoleHttpResponse response = route(store, rows(1, 2, 3), authorizer(), ignored -> reissued)
                .handle(get("?cursor=" + cursor));

        assertThat(response.status()).isEqualTo(410);
        assertThat(new String(response.body(), StandardCharsets.UTF_8)).doesNotContain("\"events\"");
    }

    @Test
    void continuationContextMismatchWinsOverARetiredGeneration() {
        StubStore store = head(3, 1, 0);
        String retiredFingerprint = ConsoleHistoryCursorCodec.mandateFingerprint(
                "SUB-OTHER", ACQUIRER, MERCHANT, Set.of("DOMESTIC"));
        String cursor = mintCursor(3, 1, 0, 1, "TX-OTHER", SUBJECT, retiredFingerprint);

        ConsoleHttpResponse response = route(store, rows(1, 2, 3), authorizer())
                .handle(get("?cursor=" + cursor));

        // Both the transaction binding (context) and the mandate fingerprint (generation) differ;
        // C-1.3 requires the context refusal first.
        assertThat(response.status()).isEqualTo(403);
        assertThat(body(response).get("error").asText()).isEqualTo("CONTEXT_DENIED");
        assertThat(store.headCalls).isZero();
    }

    @Test
    void postReadContextMismatchWinsOverARetiredGeneration() {
        StubStore store = head(3, 1, 0);
        StubReader reader = rows(1, 2, 3);
        AtomicInteger registryCalls = new AtomicInteger();
        // Another merchant both changes the context triple and retires the fingerprint.
        AuthorizedContext moved = new AuthorizedContext(ACQUIRER, "MERCHANT-B", "SUB-DEMO",
                TERMINAL, Set.of(Route.DOMESTIC));
        ConsoleContextRegistry changesAfterTheRead = ignored -> registryCalls.incrementAndGet() == 1
                ? MANDATE : moved;

        ConsoleHttpResponse response = route(store, reader, authorizer(), changesAfterTheRead)
                .handle(get("?snapshot=new"));

        assertThat(response.status()).isEqualTo(403);
        assertThat(body(response).get("error").asText()).isEqualTo("CONTEXT_DENIED");
        assertThat(body(response).get("action").isNull()).isTrue();
        assertThat(registryCalls).hasValue(2);
        assertThat(reader.calls).isEqualTo(1);
        assertThat(new String(response.body(), StandardCharsets.UTF_8)).doesNotContain("\"events\"");
    }

    @Test
    void postReadRetiredGenerationAloneStaysFourTen() {
        StubStore store = head(3, 1, 0);
        StubReader reader = rows(1, 2, 3);
        AtomicInteger registryCalls = new AtomicInteger();
        AuthorizedContext reissued = new AuthorizedContext(ACQUIRER, MERCHANT, "SUB-DEMO-REISSUED",
                TERMINAL, Set.of(Route.DOMESTIC));
        ConsoleContextRegistry reissuesAfterTheRead = ignored -> registryCalls.incrementAndGet() == 1
                ? MANDATE : reissued;

        ConsoleHttpResponse response = route(store, reader, authorizer(), reissuesAfterTheRead)
                .handle(get("?snapshot=new"));

        // Same acquirer/merchant/terminal: only the mandate generation retired.
        assertThat(response.status()).isEqualTo(410);
        assertThat(body(response).get("error").asText()).isEqualTo("RESYNC_REQUIRED");
        assertThat(body(response).get("action").asText()).isEqualTo("RESYNC_REQUIRED");
        assertThat(registryCalls).hasValue(2);
        assertThat(new String(response.body(), StandardCharsets.UTF_8)).doesNotContain("\"events\"");
    }

    @Test
    void cursorBoundToAnotherSubjectIsForbidden() {
        StubStore store = head(3, 1, 0);
        String cursor = mintCursor(3, 1, 0, 1, TRANSACTION, "another-user", fingerprintOf(MANDATE));

        ConsoleHttpResponse response = route(store, rows(1, 2, 3), authorizer())
                .handle(get("?cursor=" + cursor));

        assertThat(response.status()).isEqualTo(403);
        assertThat(body(response).get("error").asText()).isEqualTo("CONTEXT_DENIED");
        assertThat(store.headCalls).isZero();
    }

    @Test
    void cursorBoundToAnotherTransactionIsForbidden() {
        StubStore store = head(3, 1, 0);
        String cursor = mintCursor(3, 1, 0, 1, "TX-OTHER", SUBJECT, fingerprintOf(MANDATE));
        ConsoleHttpRequest request = new ConsoleHttpRequest("GET",
                "/console/v1/transactions/" + TRANSACTION + "/events",
                authorizedHeaders(), new byte[0], Map.of("cursor", List.of(cursor)));

        ConsoleHttpResponse response = route(store, rows(1, 2, 3), authorizer()).handle(request);

        assertThat(response.status()).isEqualTo(403);
        assertThat(store.headCalls).isZero();
    }

    @Test
    void gapHaltsCoverageAndSuppressesTheNextCursor() {
        StubStore store = head(3, 1, 0);

        ConsoleHttpResponse response = route(store, rows(1, 3), authorizer())
                .handle(get("?snapshot=new"));

        assertThat(response.status()).isEqualTo(200);
        JsonNode body = body(response);
        assertThat(body.get("events")).hasSize(1);
        assertThat(body.get("events").get(0).get("sourceSequence").asLong()).isEqualTo(1);
        assertThat(body.get("coveredThrough").asLong()).isEqualTo(1);
        assertThat(body.get("gapRanges")).hasSize(1);
        assertThat(body.get("gapRanges").get(0).get(0).asLong()).isEqualTo(2);
        assertThat(body.get("gapRanges").get(0).get(1).asLong()).isEqualTo(2);
        assertThat(body.get("completeThroughSnapshot").asBoolean()).isFalse();
        assertThat(body.get("nextCursor").isNull()).isTrue();
    }

    @Test
    void logicallyExpiredPrefixIsReportedAsARangeNotSkipped() {
        StubStore store = head(3, 2, 0);

        ConsoleHttpResponse response = route(store, rows(2, 3), authorizer())
                .handle(get("?snapshot=new"));

        assertThat(response.status()).isEqualTo(200);
        JsonNode body = body(response);
        assertThat(body.get("events")).hasSize(2);
        assertThat(body.get("coveredThrough").asLong()).isEqualTo(3);
        assertThat(body.get("expiredRanges")).hasSize(1);
        assertThat(body.get("expiredRanges").get(0).get(0).asLong()).isEqualTo(1);
        assertThat(body.get("expiredRanges").get(0).get(1).asLong()).isEqualTo(1);
        assertThat(body.get("retentionTruncated").asBoolean()).isTrue();
        assertThat(body.get("completeThroughSnapshot").asBoolean()).isTrue();
    }

    @Test
    void tokenExpiryDuringTheStoreReadDisclosesNoPayload() {
        StubStore store = head(3, 1, 0);
        StubReader reader = rows(1, 2, 3);
        AtomicInteger credentialCalls = new AtomicInteger();
        ConsoleAuthorizer expiresDuringRead = (header, now) -> {
            if (credentialCalls.incrementAndGet() > 1) {
                throw new ConsoleAuthenticationException(
                        ConsoleAuthenticationException.Reason.EXPIRED);
            }
            return principal();
        };

        ConsoleHttpResponse response = route(store, reader, expiresDuringRead)
                .handle(get("?snapshot=new"));

        assertThat(response.status()).isEqualTo(401);
        assertThat(body(response).get("error").asText()).isEqualTo("UNAUTHENTICATED");
        assertThat(new String(response.body(), StandardCharsets.UTF_8)).doesNotContain("\"events\"");
        assertThat(reader.calls).isEqualTo(1);
        assertThat(credentialCalls).hasValue(2);
    }

    @Test
    void registryOutageAfterTheStoreReadIsFiveZeroThreeWithNoPayload() {
        StubStore store = head(3, 1, 0);
        AtomicInteger registryCalls = new AtomicInteger();
        ConsoleContextRegistry failsAfterFirstRead = ignored -> {
            if (registryCalls.incrementAndGet() > 1) {
                throw new IllegalStateException("registry unavailable");
            }
            return MANDATE;
        };

        ConsoleHttpResponse response = route(store, rows(1, 2, 3), authorizer(), failsAfterFirstRead)
                .handle(get("?snapshot=new"));

        assertThat(response.status()).isEqualTo(503);
        assertThat(body(response).get("error").asText())
                .isEqualTo("AUTHORIZATION_UNAVAILABLE");
        assertThat(new String(response.body(), StandardCharsets.UTF_8)).doesNotContain("\"events\"");
    }

    @Test
    void storeFailureIsFiveZeroThreeWithNoPayload() {
        StubStore store = head(3, 1, 0);
        StubReader reader = rows(1, 2, 3);
        reader.fail = true;

        ConsoleHttpResponse response = route(store, reader, authorizer())
                .handle(get("?snapshot=new"));

        assertThat(response.status()).isEqualTo(503);
        assertThat(body(response).get("error").asText())
                .isEqualTo("AUTHORIZATION_UNAVAILABLE");
    }

    /**
     * QA-DEF-2: the reviewed {@code historyErrors["503"]} fixture is the authority. The previous
     * assertion and implementation encoded {@code UNAVAILABLE}; that was changed because it
     * contradicted the adopted contract, not to make a red test green.
     */
    @Test
    void serviceUnavailableBodyExactlyMatchesTheReviewedFixture() {
        StubStore store = head(3, 1, 0);
        StubReader reader = rows(1, 2, 3);
        reader.fail = true;

        ConsoleHttpResponse response = route(store, reader, authorizer())
                .handle(get("?snapshot=new"));

        assertThat(response.status()).isEqualTo(503);
        assertThat(new String(response.body(), StandardCharsets.UTF_8))
                .isEqualTo("{\"version\":1,\"error\":\"AUTHORIZATION_UNAVAILABLE\","
                        + "\"action\":null}");
    }

    @Test
    void cursorPayloadBindsTheTokenIssuerAndTargetTerminal() {
        StubStore store = head(250, 1, 0);
        long[] sequences = new long[100];
        for (int i = 0; i < sequences.length; i++) {
            sequences[i] = i + 1;
        }

        ConsoleHttpResponse response = route(store, rows(sequences), authorizer())
                .handle(get("?snapshot=new"));

        assertThat(response.status()).isEqualTo(200);
        String cursor = body(response).get("nextCursor").asText();
        JsonNode payload = cursorPayload(cursor);
        assertThat(payload.get("issuer").asText()).isEqualTo(ISSUER);
        assertThat(payload.get("terminalRef").asText()).isEqualTo(TERMINAL);
    }

    @Test
    void cursorMintedUnderAnotherIssuerIsForbidden() {
        StubStore store = head(3, 1, 0);
        String cursor = mintCursor(3, 1, 0, 1, TRANSACTION, SUBJECT, fingerprintOf(MANDATE),
                "https://auth.example.invalid/realms/other", TERMINAL);

        ConsoleHttpResponse response = route(store, rows(1, 2, 3), authorizer())
                .handle(get("?cursor=" + cursor));

        assertThat(response.status()).isEqualTo(403);
        assertThat(body(response).get("error").asText()).isEqualTo("CONTEXT_DENIED");
        assertThat(store.headCalls).isZero();
    }

    @Test
    void cursorBoundToAnotherTerminalIsForbidden() {
        StubStore store = head(3, 1, 0);
        String cursor = mintCursor(3, 1, 0, 1, TRANSACTION, SUBJECT, fingerprintOf(MANDATE),
                ISSUER, "TERMINAL-B");

        ConsoleHttpResponse response = route(store, rows(1, 2, 3), authorizer())
                .handle(get("?cursor=" + cursor));

        assertThat(response.status()).isEqualTo(403);
        assertThat(body(response).get("error").asText()).isEqualTo("CONTEXT_DENIED");
        assertThat(store.headCalls).isZero();
    }

    @Test
    void authorizedUnknownTargetIsFourZeroFourAndDisclosesNothing() {
        StubStore store = new StubStore();

        ConsoleHttpResponse response = route(store, rows(1), authorizer())
                .handle(get("?snapshot=new"));

        assertThat(response.status()).isEqualTo(404);
        assertThat(body(response).get("error").asText()).isEqualTo("NOT_FOUND");
        assertThat(response.toString()).doesNotContain("events");
    }

    @Test
    void missingOrDuplicatedTokenIsRefusedBeforeTheStoreRead() {
        StubStore store = head(3, 1, 0);
        StubReader reader = rows(1, 2, 3);
        ConsoleHistoryRoute route = route(store, reader, authorizer());

        ConsoleHttpResponse missing = route.handle(new ConsoleHttpRequest("GET",
                "/console/v1/transactions/" + TRANSACTION + "/events", Map.of(), new byte[0]));
        Map<String, List<String>> duplicated = authorizedHeaders();
        duplicated.put("Authorization", List.of(BEARER, BEARER));
        ConsoleHttpResponse twice = route.handle(new ConsoleHttpRequest("GET",
                "/console/v1/transactions/" + TRANSACTION + "/events", duplicated, new byte[0]));

        assertThat(missing.status()).isEqualTo(401);
        assertThat(twice.status()).isEqualTo(401);
        assertThat(store.headCalls).isZero();
        assertThat(reader.calls).isZero();
    }

    @Test
    void foreignMethodAndPathAreRejectedWithoutAnyRead() {
        StubStore store = head(3, 1, 0);
        StubReader reader = rows(1, 2, 3);
        ConsoleHistoryRoute route = route(store, reader, authorizer());

        ConsoleHttpResponse wrongMethod = route.handle(new ConsoleHttpRequest("POST",
                "/console/v1/transactions/" + TRANSACTION + "/events",
                authorizedHeaders(), new byte[0]));
        ConsoleHttpResponse wrongPath = route.handle(new ConsoleHttpRequest("GET",
                "/console/v1/transactions/" + TRANSACTION + "/other",
                authorizedHeaders(), new byte[0]));

        assertThat(wrongMethod.status()).isEqualTo(405);
        assertThat(wrongPath.status()).isEqualTo(404);
        assertThat(reader.calls).isZero();
    }

    /**
     * C-3.1: every syntactically unusable pagination form is {@code 400 INVALID_CURSOR} with a null
     * action. The method name is historical (the mapping was provisional before C-3 was adopted);
     * the assertion is now the reviewed contract text for the request-form step. A value of
     * {@code after} beyond the frozen head is deliberately absent: C-3.4 fixes it later.
     */
    @Test
    void malformedPaginationInputUsesTheRecordedProvisionalMapping() {
        StubStore store = head(3, 1, 0);
        ConsoleHistoryRoute route = route(store, rows(1, 2, 3), authorizer());

        for (String query : List.of("?after=abc", "?after=-1", "?after=1&after=2",
                "?snapshot=old", "?snapshot=new&snapshot=new", "?cursor=",
                "?unknown=1", "?after=", "?after=9999999999999")) {
            ConsoleHttpResponse response = route.handle(get(query));
            assertThat(response.status()).as(query).isEqualTo(400);
            assertThat(body(response).get("error").asText()).as(query).isEqualTo("INVALID_CURSOR");
            assertThat(body(response).get("action").isNull()).as(query).isTrue();
        }
        assertThat(store.headCalls).isZero();
    }

    @Test
    void afterBeyondTheDurableHeadIsInvalidRatherThanSilentlyClamped() {
        StubStore store = head(3, 1, 0);
        StubReader reader = rows(1, 2, 3);

        ConsoleHttpResponse response = route(store, reader, authorizer())
                .handle(get("?snapshot=new&after=4"));

        assertThat(response.status()).isEqualTo(400);
        assertThat(body(response).get("error").asText()).isEqualTo("INVALID_CURSOR");
        assertThat(body(response).get("action").isNull()).isTrue();
        // C-3.4 position: the head was read exactly once, and no page/range read occurred.
        assertThat(store.headCalls).isEqualTo(1);
        assertThat(reader.calls).isZero();
    }

    @Test
    void registryOutageBeforeTheStoreReadIsFiveZeroThreeWithNoHead() {
        StubStore store = head(3, 1, 0);
        StubReader reader = rows(1, 2, 3);
        ConsoleContextRegistry failing = ignored -> {
            throw new IllegalStateException("registry unavailable");
        };

        ConsoleHttpResponse response = route(store, reader, authorizer(), failing)
                .handle(get("?snapshot=new"));

        // C-3.3 resolution step 503, before the store head and before any read.
        assertThat(response.status()).isEqualTo(503);
        assertThat(body(response).get("error").asText())
                .isEqualTo("AUTHORIZATION_UNAVAILABLE");
        assertThat(body(response).get("action").isNull()).isTrue();
        assertThat(store.headCalls).isZero();
        assertThat(reader.calls).isZero();
    }

    @Test
    void missingMandateIsFourZeroThreeBeforeTheStoreRead() {
        StubStore store = head(3, 1, 0);
        StubReader reader = rows(1, 2, 3);

        ConsoleHttpResponse response = route(store, reader, authorizer(), ignored -> null)
                .handle(get("?snapshot=new"));

        // C-3.3 resolution step 403, before the store head and before any read.
        assertThat(response.status()).isEqualTo(403);
        assertThat(body(response).get("error").asText()).isEqualTo("CONTEXT_DENIED");
        assertThat(body(response).get("action").isNull()).isTrue();
        assertThat(store.headCalls).isZero();
        assertThat(reader.calls).isZero();
    }

    @Test
    void headUnavailableIsFiveZeroThreeBeforeAnyRead() {
        StubStore store = head(3, 1, 0);
        store.failHead = true;
        StubReader reader = rows(1, 2, 3);

        ConsoleHttpResponse response = route(store, reader, authorizer())
                .handle(get("?snapshot=new&after=4"));

        // C-3.3 head step 503 wins over the later head-dependent after > H 400.
        assertThat(response.status()).isEqualTo(503);
        assertThat(body(response).get("error").asText())
                .isEqualTo("AUTHORIZATION_UNAVAILABLE");
        assertThat(body(response).get("action").isNull()).isTrue();
        assertThat(store.headCalls).isEqualTo(1);
        assertThat(reader.calls).isZero();
    }

    @Test
    void malformedSignedPayloadIsFourHundredBeforeRetiredAndContextChecks() {
        StubStore store = head(3, 1, 0);
        StubReader reader = rows(1, 2, 3);
        // Signature verifies, but the signed payload is not valid JSON: malformed-payload 400,
        // which C-3.3 places before the retired-schema 410 and the context 403.
        String cursor = forge("{\"version\":1,");

        ConsoleHttpResponse response = route(store, reader, authorizer())
                .handle(new ConsoleHttpRequest("GET",
                        "/console/v1/transactions/" + TRANSACTION + "/events", authorizedHeaders(),
                        new byte[0], Map.of("cursor", List.of(cursor))));

        assertThat(response.status()).isEqualTo(400);
        assertThat(body(response).get("error").asText()).isEqualTo("INVALID_CURSOR");
        assertThat(body(response).get("action").isNull()).isTrue();
        assertThat(store.headCalls).isZero();
        assertThat(reader.calls).isZero();
    }

    @Test
    void retiredSchemaCursorIsFourTenBeforeAnyContextCheck() {
        StubStore store = head(3, 1, 0);
        StubReader reader = rows(1, 2, 3);
        // The signature verifies, but the payload declares a retired schema version. Its bound
        // context deliberately differs from the request, so a 403 here would prove the context
        // check ran first. C-3.3 requires the retired-schema 410 before the context 403.
        String cursor = forge("{\"version\":2,\"keyId\":\"cursor-key-1\","
                + "\"transactionId\":\"TX-OTHER\",\"subject\":\"another-user\","
                + "\"acquirerRef\":\"ACQ-OTHER\",\"merchantRef\":\"MERCHANT-OTHER\"}");

        ConsoleHttpResponse response = route(store, reader, authorizer())
                .handle(new ConsoleHttpRequest("GET",
                        "/console/v1/transactions/" + TRANSACTION + "/events", authorizedHeaders(),
                        new byte[0], Map.of("cursor", List.of(cursor))));

        assertThat(response.status()).isEqualTo(410);
        assertThat(body(response).get("error").asText()).isEqualTo("RESYNC_REQUIRED");
        assertThat(body(response).get("action").asText()).isEqualTo("RESYNC_REQUIRED");
        assertThat(store.headCalls).isZero();
        assertThat(reader.calls).isZero();
    }

    @Test
    void expiredCursorIsFourTenBeforeAContextMismatch() {
        StubStore store = head(3, 1, 0);
        StubReader reader = rows(1, 2, 3);
        String cursor = mintCursor(3, 1, 0, 1, "TX-OTHER", SUBJECT, fingerprintOf(MANDATE));
        clock.advance(java.time.Duration.ofSeconds(901));

        ConsoleHttpResponse response = route(store, reader, authorizer())
                .handle(get("?cursor=" + cursor));

        // Expiry 410 precedes the context 403 even though the transaction is also wrong.
        assertThat(response.status()).isEqualTo(410);
        assertThat(body(response).get("error").asText()).isEqualTo("RESYNC_REQUIRED");
        assertThat(store.headCalls).isZero();
        assertThat(reader.calls).isZero();
    }

    @Test
    void unknownQueryParameterIsRefusedInTheRequestFormStep() {
        StubStore store = head(3, 1, 0);
        StubReader reader = rows(1, 2, 3);
        AtomicInteger registryCalls = new AtomicInteger();
        ConsoleContextRegistry counting = ignored -> {
            registryCalls.incrementAndGet();
            return MANDATE;
        };

        ConsoleHttpResponse response = route(store, reader, authorizer(), counting)
                .handle(get("?snapshot=new&unknown=1"));

        // C-3.1(c): the form step runs before mandate resolution and any store read.
        assertThat(response.status()).isEqualTo(400);
        assertThat(body(response).get("error").asText()).isEqualTo("INVALID_CURSOR");
        assertThat(body(response).get("action").isNull()).isTrue();
        assertThat(registryCalls).hasValue(0);
        assertThat(store.headCalls).isZero();
        assertThat(reader.calls).isZero();
    }

    @Test
    void repeatedQueryParameterIsRefusedInTheRequestFormStep() {
        StubStore store = head(3, 1, 0);
        StubReader reader = rows(1, 2, 3);
        AtomicInteger registryCalls = new AtomicInteger();
        ConsoleContextRegistry counting = ignored -> {
            registryCalls.incrementAndGet();
            return MANDATE;
        };

        ConsoleHttpResponse response = route(store, reader, authorizer(), counting)
                .handle(get("?after=1&after=2"));

        // C-3.1(d): a repeated pagination parameter is refused before resolution and the store.
        assertThat(response.status()).isEqualTo(400);
        assertThat(body(response).get("error").asText()).isEqualTo("INVALID_CURSOR");
        assertThat(body(response).get("action").isNull()).isTrue();
        assertThat(registryCalls).hasValue(0);
        assertThat(store.headCalls).isZero();
        assertThat(reader.calls).isZero();
    }

    @Test
    void malformedFormWinsOverARegistryOutage() {
        StubStore store = head(3, 1, 0);
        StubReader reader = rows(1, 2, 3);
        ConsoleContextRegistry failing = ignored -> {
            throw new IllegalStateException("registry unavailable");
        };

        ConsoleHttpResponse response = route(store, reader, authorizer(), failing)
                .handle(get("?after=abc"));

        // C-3.3 order: the request-form 400 precedes the resolution 503.
        assertThat(response.status()).isEqualTo(400);
        assertThat(store.headCalls).isZero();
        assertThat(reader.calls).isZero();
    }

    @Test
    void boundLastSequenceBeyondTheSnapshotHeadIsFourHundredAfterTheHeadRead() {
        StubStore store = head(3, 1, 0);
        StubReader reader = rows(1, 2, 3);
        // Snapshot head 3 but the bound lastSequence claims 5: refused only after the head read.
        String cursor = mintCursor(3, 1, 0, 5, TRANSACTION, SUBJECT, fingerprintOf(MANDATE));

        ConsoleHttpResponse response = route(store, reader, authorizer())
                .handle(get("?cursor=" + cursor));

        assertThat(response.status()).isEqualTo(400);
        assertThat(body(response).get("error").asText()).isEqualTo("INVALID_CURSOR");
        assertThat(body(response).get("action").isNull()).isTrue();
        assertThat(store.headCalls).isEqualTo(1);
        assertThat(reader.calls).isZero();
    }

    @Test
    void unknownTargetOnAContinuationIsFourZeroFourAfterTheHeadStep() {
        StubStore store = new StubStore();
        StubReader reader = rows(1, 2, 3);
        String cursor = mintCursor(3, 1, 0, 1, TRANSACTION, SUBJECT, fingerprintOf(MANDATE));

        ConsoleHttpResponse response = route(store, reader, authorizer())
                .handle(get("?cursor=" + cursor));

        assertThat(response.status()).isEqualTo(404);
        assertThat(body(response).get("error").asText()).isEqualTo("NOT_FOUND");
        assertThat(body(response).get("action").isNull()).isTrue();
        assertThat(store.headCalls).isEqualTo(1);
        assertThat(reader.calls).isZero();
    }

    @Test
    void cursorCombinedWithAfterIsRefusedBeforeMandateResolutionAndTheStore() {
        StubStore store = head(3, 1, 0);
        StubReader reader = rows(1, 2, 3);
        AtomicInteger registryCalls = new AtomicInteger();
        ConsoleContextRegistry counting = ignored -> {
            registryCalls.incrementAndGet();
            return MANDATE;
        };
        String cursor = mintCursor(3, 1, 0, 1, TRANSACTION, SUBJECT, fingerprintOf(MANDATE));
        ConsoleHttpRequest request = new ConsoleHttpRequest("GET",
                "/console/v1/transactions/" + TRANSACTION + "/events", authorizedHeaders(),
                new byte[0], Map.of("cursor", List.of(cursor), "after", List.of("0")));

        ConsoleHttpResponse response = route(store, reader, authorizer(), counting).handle(request);

        // C-2: no precedence — the contradictory combination is refused outright.
        assertThat(response.status()).isEqualTo(400);
        assertThat(body(response).get("error").asText()).isEqualTo("INVALID_CURSOR");
        assertThat(body(response).get("action").isNull()).isTrue();
        assertThat(registryCalls).hasValue(0);
        assertThat(store.headCalls).isZero();
        assertThat(reader.calls).isZero();
    }

    @Test
    void cursorCombinedWithSnapshotIsRefusedBeforeMandateResolutionAndTheStore() {
        StubStore store = head(3, 1, 0);
        StubReader reader = rows(1, 2, 3);
        AtomicInteger registryCalls = new AtomicInteger();
        ConsoleContextRegistry counting = ignored -> {
            registryCalls.incrementAndGet();
            return MANDATE;
        };
        String cursor = mintCursor(3, 1, 0, 1, TRANSACTION, SUBJECT, fingerprintOf(MANDATE));
        ConsoleHttpRequest request = new ConsoleHttpRequest("GET",
                "/console/v1/transactions/" + TRANSACTION + "/events", authorizedHeaders(),
                new byte[0], Map.of("cursor", List.of(cursor), "snapshot", List.of("new")));

        ConsoleHttpResponse response = route(store, reader, authorizer(), counting).handle(request);

        assertThat(response.status()).isEqualTo(400);
        assertThat(body(response).get("error").asText()).isEqualTo("INVALID_CURSOR");
        assertThat(body(response).get("action").isNull()).isTrue();
        assertThat(registryCalls).hasValue(0);
        assertThat(store.headCalls).isZero();
        assertThat(reader.calls).isZero();
    }

    // ------------------------------------------------------------------ fixtures

    private ConsoleHistoryRoute route(StubStore store, StubReader reader,
            ConsoleAuthorizer authorizer) {
        return route(store, reader, authorizer, ignored -> MANDATE);
    }

    private ConsoleHistoryRoute route(StubStore store, StubReader reader,
            ConsoleAuthorizer authorizer, ConsoleContextRegistry registry) {
        return new ConsoleHistoryRoute(authorizer, registry, store, reader, codec, ENVIRONMENT,
                900, clock);
    }

    private static ConsoleAuthorizer authorizer() {
        return (header, now) -> {
            if (!BEARER.equals(header)) {
                throw new ConsoleAuthenticationException(
                        ConsoleAuthenticationException.Reason.MISSING_TOKEN);
            }
            return principal();
        };
    }

    private static ConsolePrincipal principal() {
        return new ConsolePrincipal(ISSUER, SUBJECT, AUTHORIZED_PARTY, AUDIENCE,
                Set.of("console.history"));
    }

    private static String fingerprintOf(AuthorizedContext mandate) {
        return ConsoleHistoryCursorCodec.mandateFingerprint(mandate.callerId(),
                mandate.acquirerRef(), mandate.merchantRef(),
                Set.of("DOMESTIC"));
    }

    private String mintCursor(long snapshotHead, long firstRetained, long revision,
            long lastSequence, String transactionId, String subject, String fingerprint) {
        return mintCursor(snapshotHead, firstRetained, revision, lastSequence, transactionId,
                subject, fingerprint, ISSUER, TERMINAL);
    }

    /**
     * Forge a signature-valid cursor from arbitrary payload bytes, so a test can present a handle
     * whose signature verifies but whose signed payload is malformed or declares a retired schema.
     * This mirrors the codec's documented wire form and uses the same synthetic test key.
     */
    private static String forge(String payloadJson) {
        try {
            byte[] payload = payloadJson.getBytes(StandardCharsets.UTF_8);
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(CURSOR_KEY.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] signature = mac.doFinal(payload);
            Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
            return encoder.encodeToString(payload) + "." + encoder.encodeToString(signature);
        } catch (java.security.GeneralSecurityException failure) {
            throw new IllegalStateException("HmacSHA256 is unavailable", failure);
        }
    }

    private String mintCursor(long snapshotHead, long firstRetained, long revision,
            long lastSequence, String transactionId, String subject, String fingerprint,
            String issuer, String terminalRef) {
        Instant now = clock.instant();
        ConsoleHistoryCursorCodec.Binding binding = new ConsoleHistoryCursorCodec.Binding(
                issuer, subject, AUTHORIZED_PARTY, AUDIENCE, ENVIRONMENT, fingerprint,
                "READ-GEN-1", ACQUIRER, MERCHANT, terminalRef, transactionId, snapshotHead,
                revision, firstRetained, lastSequence, 0, 0, now, now.plusSeconds(900));
        return codec.mint(binding, now, 900);
    }

    private static ConsoleHttpRequest get(String query) {
        Map<String, List<String>> parsed = query.startsWith("?") && query.length() > 1
                ? parse(query.substring(1)) : Map.of();
        return new ConsoleHttpRequest("GET",
                "/console/v1/transactions/" + TRANSACTION + "/events",
                authorizedHeaders(), new byte[0], parsed);
    }

    private static Map<String, List<String>> authorizedHeaders() {
        Map<String, List<String>> headers = new LinkedHashMap<>();
        headers.put("Authorization", List.of(BEARER));
        headers.put("Accept", List.of("application/json"));
        return headers;
    }

    private static Map<String, List<String>> parse(String raw) {
        Map<String, List<String>> parsed = new LinkedHashMap<>();
        for (String pair : raw.split("&", -1)) {
            int equals = pair.indexOf('=');
            String name = equals < 0 ? pair : pair.substring(0, equals);
            String value = equals < 0 ? "" : pair.substring(equals + 1);
            parsed.computeIfAbsent(name, key -> new java.util.ArrayList<>()).add(value);
        }
        return parsed;
    }

    private static JsonNode body(ConsoleHttpResponse response) {
        try {
            return MAPPER.readTree(response.body());
        } catch (Exception failure) {
            throw new IllegalStateException("response body is not JSON", failure);
        }
    }

    private static JsonNode cursorPayload(String cursor) {
        try {
            String encoded = cursor.substring(0, cursor.indexOf('.'));
            return MAPPER.readTree(Base64.getUrlDecoder().decode(encoded));
        } catch (Exception failure) {
            throw new IllegalStateException("cursor payload is not JSON", failure);
        }
    }

    private static StubStore head(long lastSequence, long firstRetained, long revision) {
        StubStore store = new StubStore();
        store.head = new EventHead(CONTEXT, lastSequence, firstRetained, revision, null, null);
        return store;
    }

    private static StubReader rows(long... sequences) {
        StubReader reader = new StubReader();
        for (long sequence : sequences) {
            reader.rows.put(sequence, row(sequence));
        }
        return reader;
    }

    private static RecordedEvent row(long sequence) {
        EventSourceWrapper wrapper = new EventSourceWrapper(EventDurability.TASK_BEST_EFFORT,
                "synthetic-sha-" + sequence, null, null, "2026-11-23T12:00:00Z", sequence,
                "2026-11-23T12:00:01Z", null);
        return new RecordedEvent(wrapper, "{\"eventId\":\"E-" + sequence + "\"}");
    }

    /** Read-only double: any append or dedupe lookup would fail the test. */
    private static final class StubStore implements EventStore {
        private EventHead head;
        private int headCalls;
        private boolean failHead;

        @Override
        public com.acquiring.simulator.domain.event.EventAppendResult append(
                com.acquiring.simulator.domain.event.EventSubmission submission) {
            throw new AssertionError("the history route must not append");
        }

        @Override
        public Optional<StoredEvent> find(
                com.acquiring.simulator.domain.event.EventDedupeKey dedupeKey) {
            throw new AssertionError("the history route must not read the dedupe index");
        }

        @Override
        public Optional<EventHead> head(EventContext context) {
            headCalls++;
            if (failHead) {
                throw new IllegalStateException("event store unavailable");
            }
            return Optional.ofNullable(head);
        }
    }

    private static final class StubReader implements EventHistoryReader {
        private final NavigableMap<Long, RecordedEvent> rows = new TreeMap<>();
        private int calls;
        private long observedThrough = -1;
        private boolean fail;

        @Override
        public List<RecordedEvent> readRange(
                EventContext context, long afterSequence, long throughSequence, int limit) {
            calls++;
            observedThrough = throughSequence;
            if (fail) {
                throw new IllegalStateException("event source unavailable");
            }
            return List.copyOf(rows.subMap(afterSequence, false, throughSequence, true)
                    .values()).stream().limit(limit).toList();
        }
    }
}
