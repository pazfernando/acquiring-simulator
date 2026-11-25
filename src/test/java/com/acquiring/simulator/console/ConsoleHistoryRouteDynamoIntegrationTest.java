package com.acquiring.simulator.console;

import static org.assertj.core.api.Assertions.assertThat;

import com.acquiring.simulator.adapter.aws.CriticalWriteComposer;
import com.acquiring.simulator.adapter.aws.DynamoEventHistoryReader;
import com.acquiring.simulator.adapter.aws.DynamoEventStore;
import com.acquiring.simulator.domain.event.CanonicalEvent;
import com.acquiring.simulator.domain.event.CanonicalEventJson;
import com.acquiring.simulator.domain.event.CriticalCapacity;
import com.acquiring.simulator.domain.event.EventContext;
import com.acquiring.simulator.domain.event.EventDurability;
import com.acquiring.simulator.domain.event.EventManifest;
import com.acquiring.simulator.domain.event.EventSubmission;
import com.acquiring.simulator.domain.event.EventTableKeys;
import com.acquiring.simulator.domain.event.EventValidator;
import com.acquiring.simulator.domain.event.OperationAManifest;
import com.acquiring.simulator.domain.model.AuthorizedContext;
import com.acquiring.simulator.domain.model.Route;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeDefinition;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.BillingMode;
import software.amazon.awssdk.services.dynamodb.model.CreateTableRequest;
import software.amazon.awssdk.services.dynamodb.model.KeySchemaElement;
import software.amazon.awssdk.services.dynamodb.model.KeyType;
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType;

/**
 * History route against DynamoDB Local: the same EVENT rows the append path writes, read through the
 * strong HEAD plus the strongly consistent range reader.
 *
 * <p>These tests prove local transactional and read behaviour only. AWS strong-consistency across
 * replicas, IAM, encryption, throttling, TTL timing, API Gateway, CloudFront and WAF stay unverified.
 * The cursor key here is a synthetic test key, not a deployment secret.</p>
 */
@Testcontainers
class ConsoleHistoryRouteDynamoIntegrationTest {

    @Container static final GenericContainer<?> DYNAMO = new GenericContainer<>(
            DockerImageName.parse("amazon/dynamodb-local:2.6.1")).withExposedPorts(8000);

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Path VECTORS =
            Path.of("docs/ai-sdlc/work-items/WI-ACQ-OBS-001/vectors/event-vectors.json");
    /** Test-only example capacity; owner approval of O8 numbers is still pending. */
    private static final EventManifest MANIFEST =
            OperationAManifest.manifest(new CriticalCapacity(5, 8));
    private static final String ENVIRONMENT = "SIM";
    private static final String ACQUIRER = "ACQ-A";
    private static final String MERCHANT = "MERCHANT-A";
    private static final String TRANSACTION = "TX-HISTORY-1";
    private static final String BEARER = "Bearer demo-token";
    private static final String ISSUER = "https://auth.example.invalid/realms/acq-DEMO";
    private static final String TERMINAL = "TERMINAL-A";
    private static final EventContext CONTEXT =
            new EventContext(ENVIRONMENT, ACQUIRER, MERCHANT, TRANSACTION);
    private static final AuthorizedContext MANDATE =
            new AuthorizedContext(ACQUIRER, MERCHANT, "SUB-DEMO", TERMINAL,
                    Set.of(Route.DOMESTIC));
    private static final Instant NOW = Instant.parse("2026-11-23T12:00:00Z");
    private static final String PATH =
            "/console/v1/transactions/" + TRANSACTION + "/events";

    static DynamoDbClient db;
    String table;
    DynamoEventStore store;
    ConsoleHistoryCursorCodec codec;
    ConsoleHistoryRoute route;

    @BeforeAll static void connect() {
        db = DynamoDbClient.builder()
                .endpointOverride(URI.create("http://" + DYNAMO.getHost() + ":"
                        + DYNAMO.getMappedPort(8000)))
                .region(Region.US_EAST_1)
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create("local", "local")))
                .build();
    }

    @AfterAll static void close() {
        if (db != null) {
            db.close();
        }
    }

    @BeforeEach void createTable() {
        table = "history-" + UUID.randomUUID();
        db.createTable(CreateTableRequest.builder()
                .tableName(table)
                .billingMode(BillingMode.PAY_PER_REQUEST)
                .attributeDefinitions(
                        AttributeDefinition.builder().attributeName("pk")
                                .attributeType(ScalarAttributeType.S).build(),
                        AttributeDefinition.builder().attributeName("sk")
                                .attributeType(ScalarAttributeType.S).build())
                .keySchema(
                        KeySchemaElement.builder().attributeName("pk")
                                .keyType(KeyType.HASH).build(),
                        KeySchemaElement.builder().attributeName("sk")
                                .keyType(KeyType.RANGE).build())
                .build());
        db.waiter().waitUntilTableExists(b -> b.tableName(table));
        store = new DynamoEventStore(db, table, MANIFEST, new EventValidator(),
                Clock.fixed(NOW, ZoneOffset.UTC), CriticalWriteComposer.NONE);
        codec = new ConsoleHistoryCursorCodec("cursor-key-1",
                "synthetic-cursor-signing-key-0123456789".getBytes(StandardCharsets.UTF_8));
        route = new ConsoleHistoryRoute(authorizer(), ignored -> MANDATE, store,
                new DynamoEventHistoryReader(db, table), codec, ENVIRONMENT, 900,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @AfterEach void dropTable() {
        db.deleteTable(b -> b.tableName(table));
    }

    @Test
    void snapshotReadsTheRetainedRowsAndReportsCompleteCoverage() {
        append(1);
        append(2);
        append(3);

        ConsoleHttpResponse response = route.handle(get(Map.of("snapshot", List.of("new"))));

        assertThat(response.status()).isEqualTo(200);
        JsonNode body = body(response);
        assertThat(body.get("snapshotHead").asLong()).isEqualTo(3);
        assertThat(body.get("expiryRevision").asLong()).isZero();
        assertThat(body.get("firstRetainedSequence").asLong()).isEqualTo(1);
        assertThat(body.get("events")).hasSize(3);
        assertThat(body.get("events").get(0).get("sourceSequence").asLong()).isEqualTo(1);
        assertThat(body.get("events").get(2).get("sourceSequence").asLong()).isEqualTo(3);
        assertThat(body.get("events").get(0).get("deliveryClass").asText())
                .isEqualTo("TASK_BEST_EFFORT");
        assertThat(body.get("coveredThrough").asLong()).isEqualTo(3);
        assertThat(body.get("completeThroughSnapshot").asBoolean()).isTrue();
        assertThat(body.get("nextCursor").isNull()).isTrue();
        assertThat(body.get("expiredRanges")).isEmpty();
        assertThat(body.get("gapRanges")).isEmpty();
    }

    @Test
    void paginationKeepsTheFrozenHeadWhenTheSourceGrows() {
        for (int sequence = 1; sequence <= 101; sequence++) {
            append(sequence);
        }

        ConsoleHttpResponse first = route.handle(get(Map.of("snapshot", List.of("new"))));
        assertThat(first.status()).isEqualTo(200);
        JsonNode firstBody = body(first);
        assertThat(firstBody.get("snapshotHead").asLong()).isEqualTo(101);
        assertThat(firstBody.get("events")).hasSize(100);
        assertThat(firstBody.get("coveredThrough").asLong()).isEqualTo(100);
        assertThat(firstBody.get("completeThroughSnapshot").asBoolean()).isFalse();
        String nextCursor = firstBody.get("nextCursor").asText();
        assertThat(nextCursor).isNotBlank();

        // A later append must not extend the frozen page.
        append(102);

        ConsoleHttpResponse continuation = route.handle(
                get(Map.of("cursor", List.of(nextCursor))));
        assertThat(continuation.status()).isEqualTo(200);
        JsonNode continuationBody = body(continuation);
        assertThat(continuationBody.get("snapshotHead").asLong()).isEqualTo(101);
        assertThat(continuationBody.get("events")).hasSize(1);
        assertThat(continuationBody.get("events").get(0).get("sourceSequence").asLong())
                .isEqualTo(101);
        assertThat(continuationBody.get("coveredThrough").asLong()).isEqualTo(101);
        assertThat(continuationBody.get("completeThroughSnapshot").asBoolean()).isTrue();
        assertThat(continuationBody.get("nextCursor").isNull()).isTrue();
    }

    @Test
    void changedRetentionRevisionAgainstTheRealHeadForcesFourTen() {
        append(1);
        append(2);
        String cursor = codec.mint(new ConsoleHistoryCursorCodec.Binding(ISSUER, "demo-user",
                "console-demo", "console-api", ENVIRONMENT, fingerprintOf(MANDATE), "READ-GEN-1",
                ACQUIRER, MERCHANT, TERMINAL, TRANSACTION, 2, 0, 1, 1, 0, 0,
                NOW, NOW.plusSeconds(900)), NOW, 900);

        db.updateItem(b -> b.tableName(table)
                .key(Map.of("pk", text(EventTableKeys.eventPartition(CONTEXT)),
                        "sk", text(EventTableKeys.headSortKey())))
                .updateExpression("SET expiryRevision = :revision")
                .expressionAttributeValues(Map.of(":revision", numeric(1))));

        ConsoleHttpResponse response = route.handle(get(Map.of("cursor", List.of(cursor))));

        assertThat(response.status()).isEqualTo(410);
        JsonNode body = body(response);
        assertThat(body.get("error").asText()).isEqualTo("RESYNC_REQUIRED");
        assertThat(body.get("action").asText()).isEqualTo("RESYNC_REQUIRED");
        assertThat(body.toString()).doesNotContain("\"events\"");
    }

    @Test
    void physicallyMissingRetainedRowIsAnExplicitGapNotAFabricatedEvent() {
        append(1);
        append(2);
        append(3);
        db.deleteItem(b -> b.tableName(table).key(Map.of(
                "pk", text(EventTableKeys.eventPartition(CONTEXT)),
                "sk", text(EventTableKeys.eventSortKey(2)))));

        ConsoleHttpResponse response = route.handle(get(Map.of("snapshot", List.of("new"))));

        assertThat(response.status()).isEqualTo(200);
        JsonNode body = body(response);
        assertThat(body.get("snapshotHead").asLong()).isEqualTo(3);
        assertThat(body.get("events")).hasSize(1);
        assertThat(body.get("events").get(0).get("sourceSequence").asLong()).isEqualTo(1);
        assertThat(body.get("coveredThrough").asLong()).isEqualTo(1);
        assertThat(body.get("gapRanges")).hasSize(1);
        assertThat(body.get("gapRanges").get(0).get(0).asLong()).isEqualTo(2);
        assertThat(body.get("gapRanges").get(0).get(1).asLong()).isEqualTo(2);
        assertThat(body.get("completeThroughSnapshot").asBoolean()).isFalse();
        assertThat(body.get("nextCursor").isNull()).isTrue();
    }

    // ------------------------------------------------------------------ fixtures

    private void append(int sequence) {
        store.append(new EventSubmission(CONTEXT, payload("EVENT-" + sequence),
                EventDurability.TASK_BEST_EFFORT, null, null, "2026-11-23T12:00:00Z",
                "2026-11-23T12:00:01Z", null));
    }

    private static CanonicalEvent payload(String eventId) {
        try {
            ObjectNode node = (ObjectNode) MAPPER.readTree(VECTORS.toFile())
                    .path("events").get(0).path("producerPayload");
            node.put("eventId", eventId);
            node.put("instanceId", "BOOT-HISTORY-1");
            node.put("status", "SUCCEEDED");
            node.put("acquirerRef", ACQUIRER);
            node.put("merchantRef", MERCHANT);
            node.put("transactionId", TRANSACTION);
            return CanonicalEventJson.parse(node.toString(), MANIFEST);
        } catch (Exception failure) {
            throw new IllegalStateException("cannot read event fixture", failure);
        }
    }

    private static ConsoleAuthorizer authorizer() {
        return (header, now) -> {
            if (!BEARER.equals(header)) {
                throw new ConsoleAuthenticationException(
                        ConsoleAuthenticationException.Reason.MISSING_TOKEN);
            }
            return new ConsolePrincipal(ISSUER, "demo-user", "console-demo", "console-api",
                    Set.of("console.history"));
        };
    }

    private static ConsoleHttpRequest get(Map<String, List<String>> query) {
        Map<String, List<String>> headers = new LinkedHashMap<>();
        headers.put("Authorization", List.of(BEARER));
        return new ConsoleHttpRequest("GET", PATH, headers, new byte[0], query);
    }

    private static String fingerprintOf(AuthorizedContext mandate) {
        return ConsoleHistoryCursorCodec.mandateFingerprint(mandate.callerId(),
                mandate.acquirerRef(), mandate.merchantRef(), Set.of("DOMESTIC"));
    }

    private static JsonNode body(ConsoleHttpResponse response) {
        try {
            return MAPPER.readTree(response.body());
        } catch (Exception failure) {
            throw new IllegalStateException("response body is not JSON", failure);
        }
    }

    private static AttributeValue text(String value) {
        return AttributeValue.builder().s(value).build();
    }

    private static AttributeValue numeric(long value) {
        return AttributeValue.builder().n(Long.toString(value)).build();
    }
}
