package com.acquiring.simulator.aws;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.acquiring.simulator.adapter.aws.CriticalWriteComposer;
import com.acquiring.simulator.adapter.aws.DynamoEventStore;
import com.acquiring.simulator.domain.event.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.*;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

/**
 * DynamoDB Local integration tests for the dedicated EVENT table adapter. These prove local
 * transactional behavior only; AWS throttling, Streams, IAM, encryption and TTL timing stay
 * unverified.
 */
@Testcontainers
class DynamoEventStoreIntegrationTest {

    @Container static final GenericContainer<?> DYNAMO = new GenericContainer<>(
            DockerImageName.parse("amazon/dynamodb-local:2.6.1")).withExposedPorts(8000);

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Path VECTORS =
            Path.of("docs/ai-sdlc/work-items/WI-ACQ-OBS-001/vectors/event-vectors.json");
    /** Test-only example capacity; owner approval of O8 numbers is still pending. */
    private static final EventManifest MANIFEST =
            OperationAManifest.manifest(new CriticalCapacity(5, 8));
    private static final EventContext CONTEXT =
            new EventContext("SIM", "ACQ-A", "MERCHANT-A", "TX-001");
    private static final Instant NOW = Instant.parse("2026-11-18T12:00:05Z");

    static DynamoDbClient db;
    String table;
    DynamoEventStore store;

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
        table = "events-" + UUID.randomUUID();
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
    }

    @AfterEach void dropTable() {
        db.deleteTable(b -> b.tableName(table));
    }

    @Test void firstCriticalAppendAllocatesSequenceOneAndStoresThePayloadOnce() {
        EventSubmission submission = critical("EVENT-001", "BOOT-001", "SUCCEEDED");

        EventAppendResult result = store.append(submission);

        assertThat(result.decision()).isEqualTo(EventDeduplication.Decision.NEW);
        assertThat(result.sourceSequence()).isEqualTo(1);
        assertThat(result.eventSha256()).isEqualTo(CanonicalEventCodec.sha256(submission.event()));

        EventHead head = store.head(CONTEXT).orElseThrow();
        assertThat(head.lastSequence()).isEqualTo(1);
        assertThat(head.firstRetainedSequence()).isEqualTo(1);
        assertThat(head.expiryRevision()).isZero();

        List<Map<String, AttributeValue>> rows = eventRows();
        assertThat(rows).hasSize(1);
        assertThat(rows.getFirst().get("sk").s()).isEqualTo("E#000000000001");
        assertThat(rows.getFirst().get("payload").s())
                .isEqualTo(CanonicalEventCodec.canonicalJson(submission.event()));
        assertThat(rows.getFirst().get("storedInstanceId").s()).isEqualTo("BOOT-001");

        StoredEvent stored = store.find(submission.dedupeKey()).orElseThrow();
        assertThat(stored.sourceSequence()).isEqualTo(1);
        assertThat(stored.eventSha256()).isEqualTo(result.eventSha256());
        assertThat(stored.context()).isEqualTo(CONTEXT);
        assertThat(stored.status()).isEqualTo(StoredEvent.ACTIVE);
        assertThat(stored.journalVersion()).isEqualTo(1L);
    }

    @Test void exactReplayKeepsTheOriginalSequenceAndInstance() {
        EventSubmission first = critical("EVENT-001", "BOOT-001", "SUCCEEDED");
        store.append(first);

        // EV2: identical payload, later collection time only.
        EventSubmission replay = new EventSubmission(CONTEXT, first.event(),
                EventDurability.CRITICAL_COMMITTED, 1L,
                "2026-11-18T12:00:01Z", "2026-11-18T12:00:02Z", "2026-11-18T12:00:06Z",
                "APPROVED");

        EventAppendResult result = store.append(replay);

        assertThat(result.decision()).isEqualTo(EventDeduplication.Decision.EXACT_REPLAY);
        assertThat(result.sourceSequence()).isEqualTo(1);
        assertThat(store.head(CONTEXT).orElseThrow().lastSequence()).isEqualTo(1);
        assertThat(eventRows()).hasSize(1);
        assertThat(eventRows().getFirst().get("storedInstanceId").s()).isEqualTo("BOOT-001");
    }

    @Test void bestEffortReplayKeepsTheFirstAuthorizedCollectionTime() {
        // Best-effort task events retain the first authorized collection time across redelivery
        // (O1a/O2a); a later delivery time must not reset it or allocate a new sequence.
        EventSubmission first = bestEffort("EVENT-TASK-001", "2026-11-18T12:00:01Z");
        EventAppendResult created = store.append(first);
        assertThat(created.decision()).isEqualTo(EventDeduplication.Decision.NEW);
        assertThat(created.sourceSequence()).isEqualTo(1);

        EventAppendResult replay = store.append(bestEffort("EVENT-TASK-001",
                "2026-11-18T13:30:00Z"));

        assertThat(replay.decision()).isEqualTo(EventDeduplication.Decision.EXACT_REPLAY);
        assertThat(replay.sourceSequence()).isEqualTo(1);
        StoredEvent stored = store.find(first.dedupeKey()).orElseThrow();
        assertThat(stored.collectedAt()).isEqualTo("2026-11-18T12:00:01Z");
        assertThat(stored.sourceSequence()).isEqualTo(1);
        assertThat(eventRows()).hasSize(1);
        assertThat(eventRows().getFirst().get("collectedAt").s())
                .isEqualTo("2026-11-18T12:00:01Z");
        assertThat(dedupeRow(first.dedupeKey()).get("collectedAt").s())
                .isEqualTo("2026-11-18T12:00:01Z");
        assertThat(store.head(CONTEXT).orElseThrow().lastCollectedAt())
                .isEqualTo("2026-11-18T12:00:01Z");
    }

    @Test void criticalCollectionTimeEqualsCommitTimeAndSurvivesReplay() {
        // Critical events must persist collectedAt == committedAt (O1a/O2a). A redelivery has a
        // later delivery time, but the strong read still reports the original committed pair.
        EventSubmission first = critical("EVENT-001", "BOOT-001", "SUCCEEDED");
        store.append(first);

        Map<String, AttributeValue> storedRow = eventRows().getFirst();
        assertThat(storedRow.get("collectedAt").s()).isEqualTo("2026-11-18T12:00:01Z");
        assertThat(storedRow.get("committedAt").s()).isEqualTo("2026-11-18T12:00:01Z");
        assertThat(dedupeRow(first.dedupeKey()).get("collectedAt").s())
                .isEqualTo(storedRow.get("committedAt").s());

        EventSubmission replay = new EventSubmission(CONTEXT, first.event(),
                EventDurability.CRITICAL_COMMITTED, 1L,
                "2026-11-18T12:00:01Z", "2026-11-18T14:15:00Z", "2026-11-18T14:15:01Z",
                "APPROVED");

        EventAppendResult result = store.append(replay);

        assertThat(result.decision()).isEqualTo(EventDeduplication.Decision.EXACT_REPLAY);
        StoredEvent stored = store.find(replay.dedupeKey()).orElseThrow();
        assertThat(stored.collectedAt()).isEqualTo("2026-11-18T12:00:01Z");
        assertThat(stored.collectedAt()).isEqualTo(
                eventRows().getFirst().get("committedAt").s());
        assertThat(eventRows()).hasSize(1);
    }

    @Test void freshStoreInstanceReplayReturnsThePersistedFirstCollectionTime() {
        EventSubmission first = bestEffort("EVENT-TASK-002", "2026-11-18T12:00:01Z");
        store.append(first);

        DynamoEventStore freshInstance = new DynamoEventStore(db, table, MANIFEST,
                new EventValidator(), Clock.fixed(Instant.parse("2026-11-20T09:00:00Z"),
                        ZoneOffset.UTC), CriticalWriteComposer.NONE);
        EventAppendResult replay = freshInstance.append(
                bestEffort("EVENT-TASK-002", "2026-11-20T09:00:05Z"));

        assertThat(replay.decision()).isEqualTo(EventDeduplication.Decision.EXACT_REPLAY);
        assertThat(replay.sourceSequence()).isEqualTo(1);
        assertThat(freshInstance.find(first.dedupeKey()).orElseThrow().collectedAt())
                .isEqualTo("2026-11-18T12:00:01Z");
        assertThat(freshInstance.head(CONTEXT).orElseThrow().lastCollectedAt())
                .isEqualTo("2026-11-18T12:00:01Z");
    }

    @Test void legacyDedupeMetadataWithoutCollectionTimeReadsAsExplicitUnknown() {
        // A row written before this additive field must read as unknown, never as "now", and a later
        // replay must not silently invent or backfill a collection time.
        EventSubmission first = bestEffort("EVENT-TASK-003", "2026-11-18T12:00:01Z");
        store.append(first);
        db.updateItem(UpdateItemRequest.builder().tableName(table)
                .key(Map.of("pk", text(EventTableKeys.dedupePartition(
                                first.dedupeKey().environment())),
                        "sk", text(EventTableKeys.dedupeSortKey(first.dedupeKey()))))
                .updateExpression("REMOVE collectedAt")
                .build());

        assertThat(store.find(first.dedupeKey()).orElseThrow().collectedAt()).isNull();

        EventAppendResult replay = store.append(bestEffort("EVENT-TASK-003",
                "2026-11-18T15:00:00Z"));

        assertThat(replay.decision()).isEqualTo(EventDeduplication.Decision.EXACT_REPLAY);
        assertThat(store.find(first.dedupeKey()).orElseThrow().collectedAt()).isNull();
        assertThat(eventRows().getFirst().get("collectedAt").s())
                .isEqualTo("2026-11-18T12:00:01Z");
    }

    @Test void changedPayloadForTheSameIdentityIsAConflictWithoutOverwrite() {
        EventSubmission accepted = critical("EVENT-001", "BOOT-001", "SUCCEEDED");
        store.append(accepted);
        String storedPayload = eventRows().getFirst().get("payload").s();

        // EV3: same producer/event identity, FAILED instead of SUCCEEDED.
        EventSubmission conflicting = critical("EVENT-001", "BOOT-001", "FAILED");

        EventAppendResult result = store.append(conflicting);

        assertThat(result.decision()).isEqualTo(EventDeduplication.Decision.CONTENT_CONFLICT);
        assertThat(result.sourceSequence()).isEqualTo(1);
        assertThat(result.storedEventSha256())
                .isEqualTo(CanonicalEventCodec.sha256(accepted.event()));
        assertThat(eventRows().getFirst().get("payload").s()).isEqualTo(storedPayload);
        assertThat(store.head(CONTEXT).orElseThrow().lastSequence()).isEqualTo(1);

        StoredEvent stored = store.find(conflicting.dedupeKey()).orElseThrow();
        assertThat(stored.eventSha256()).isEqualTo(CanonicalEventCodec.sha256(accepted.event()));
        assertThat(stored.sourceSequence()).isEqualTo(1);
        assertThat(stored.status()).isEqualTo(StoredEvent.ACTIVE);
        assertThat(stored.integrityConflict()).isNotNull();
        assertThat(stored.integrityConflict().conflictingEventSha256())
                .isEqualTo(CanonicalEventCodec.sha256(conflicting.event()));
        Map<String, AttributeValue> dedupeRow = dedupeRow(conflicting.dedupeKey());
        assertThat(dedupeRow.get("integrityStatus").s())
                .isEqualTo(EventIntegrityConflict.INTEGRITY_CONFLICT);
    }

    @Test void duplicateConflictRepeatsTheSameMarkerAndConsumesNoSequenceOrSlot() {
        EventSubmission accepted = critical("EVENT-001", "BOOT-001", "SUCCEEDED");
        store.append(accepted);
        String storedPayload = eventRows().getFirst().get("payload").s();

        // Pre-existing business and capacity rows stand in for a committed claim: the conflict path
        // must not compose any of them.
        db.putItem(PutItemRequest.builder().tableName(table)
                .item(Map.of("pk", text("BIZ#SIM#TX-001"), "sk", text("META"),
                        "state", text("AWAITING"))).build());
        db.putItem(PutItemRequest.builder().tableName(table)
                .item(Map.of("pk", text(EventTableKeys.capacityPartition(CONTEXT)),
                        "sk", text(EventTableKeys.capacitySortKey()),
                        "used", number(1), "slotBudget", number(8))).build());

        EventSubmission conflicting = critical("EVENT-001", "BOOT-001", "FAILED");
        EventAppendResult first = store.append(conflicting);
        EventAppendResult second = store.append(conflicting);

        assertThat(first.decision()).isEqualTo(EventDeduplication.Decision.CONTENT_CONFLICT);
        assertThat(second.decision()).isEqualTo(EventDeduplication.Decision.CONTENT_CONFLICT);
        assertThat(first.sourceSequence()).isEqualTo(1);
        assertThat(second.sourceSequence()).isEqualTo(1);
        assertThat(store.head(CONTEXT).orElseThrow().lastSequence()).isEqualTo(1);
        assertThat(eventRows()).hasSize(1);
        assertThat(eventRows().getFirst().get("payload").s()).isEqualTo(storedPayload);

        StoredEvent stored = store.find(conflicting.dedupeKey()).orElseThrow();
        assertThat(stored.integrityConflict().conflictingEventSha256())
                .isEqualTo(CanonicalEventCodec.sha256(conflicting.event()));

        var business = db.getItem(GetItemRequest.builder().tableName(table)
                .key(Map.of("pk", text("BIZ#SIM#TX-001"), "sk", text("META")))
                .consistentRead(true).build()).item();
        assertThat(business.get("state").s()).isEqualTo("AWAITING");

        var capacity = db.getItem(GetItemRequest.builder().tableName(table)
                .key(Map.of("pk", text(EventTableKeys.capacityPartition(CONTEXT)),
                        "sk", text(EventTableKeys.capacitySortKey())))
                .consistentRead(true).build()).item();
        assertThat(capacity.get("used").n()).isEqualTo("1");
    }

    @Test void unauthorizedContextCannotPoisonTheConflictMarker() {
        // EV4: collector partition is ACQ-A but the payload claims ACQ-B. Context authorization
        // runs before dedupe, so this attempt must write neither identity nor quarantine metadata.
        EventSubmission forged = new EventSubmission(CONTEXT,
                payload("EVENT-001", "BOOT-001", "SUCCEEDED", "ACQ-B"),
                EventDurability.CRITICAL_COMMITTED, 1L,
                "2026-11-18T12:00:01Z", "2026-11-18T12:00:04Z", "2026-11-18T12:00:05Z",
                "APPROVED");

        assertThatThrownBy(() -> store.append(forged))
                .isInstanceOfSatisfying(EventValidationException.class,
                        e -> assertThat(e.violation()).isEqualTo(EventViolation.CONTEXT_MISMATCH));
        assertThat(store.find(forged.dedupeKey())).isEmpty();

        EventSubmission accepted = critical("EVENT-001", "BOOT-001", "SUCCEEDED");
        store.append(accepted);

        assertThatThrownBy(() -> store.append(forged))
                .isInstanceOfSatisfying(EventValidationException.class,
                        e -> assertThat(e.violation()).isEqualTo(EventViolation.CONTEXT_MISMATCH));
        StoredEvent afterForgedAttempt = store.find(forged.dedupeKey()).orElseThrow();
        assertThat(afterForgedAttempt.integrityConflict()).isNull();
        assertThat(dedupeRow(forged.dedupeKey()).get("integrityStatus")).isNull();

        // An authorized conflicting redelivery is what may set the marker.
        EventSubmission conflicting = critical("EVENT-001", "BOOT-001", "FAILED");
        store.append(conflicting);
        StoredEvent marked = store.find(conflicting.dedupeKey()).orElseThrow();
        assertThat(marked.integrityConflict()).isNotNull();
        assertThat(marked.integrityConflict().conflictingEventSha256())
                .isEqualTo(CanonicalEventCodec.sha256(conflicting.event()));
    }

    @Test void concurrentAppendsAllocateContiguousUniqueSequences() throws Exception {
        try (var executor = Executors.newFixedThreadPool(8)) {
            var start = new CountDownLatch(1);
            List<Future<EventAppendResult>> futures = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                String eventId = "EVENT-CONC-" + i;
                futures.add(executor.submit(() -> {
                    start.await();
                    return store.append(critical(eventId, "BOOT-001", "SUCCEEDED"));
                }));
            }
            start.countDown();
            Set<Long> sequences = new TreeSet<>();
            for (Future<EventAppendResult> future : futures) {
                EventAppendResult result = future.get(30, TimeUnit.SECONDS);
                assertThat(result.stored()).isTrue();
                sequences.add(result.sourceSequence());
            }
            assertThat(sequences).containsExactly(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L);
        }
        assertThat(store.head(CONTEXT).orElseThrow().lastSequence()).isEqualTo(8);
        assertThat(eventRows()).hasSize(8);
    }

    @Test void contextMismatchIsRejectedBeforeDedupeAndWritesNothing() {
        // EV4: collector partition is ACQ-A but the payload claims ACQ-B.
        EventSubmission submission = new EventSubmission(CONTEXT,
                payload("EVENT-001", "BOOT-001", "SUCCEEDED", "ACQ-B"),
                EventDurability.CRITICAL_COMMITTED, 1L,
                "2026-11-18T12:00:01Z", "2026-11-18T12:00:04Z", "2026-11-18T12:00:05Z",
                "APPROVED");

        assertThatThrownBy(() -> store.append(submission))
                .isInstanceOfSatisfying(EventValidationException.class,
                        e -> assertThat(e.violation()).isEqualTo(EventViolation.CONTEXT_MISMATCH));

        assertThat(store.find(submission.dedupeKey())).isEmpty();
        assertThat(store.head(CONTEXT)).isEmpty();
        assertThat(eventRows()).isEmpty();
    }

    @Test void bestEffortEventCannotAssertJournalMetadata() {
        EventSubmission submission = new EventSubmission(CONTEXT,
                payload("EVENT-001", "BOOT-001", "SUCCEEDED", "ACQ-A"),
                EventDurability.TASK_BEST_EFFORT, 3L, "2026-11-18T12:00:01Z",
                "2026-11-18T12:00:01Z", "2026-11-18T12:00:02Z", "APPROVED");

        assertThatThrownBy(() -> store.append(submission))
                .isInstanceOfSatisfying(EventValidationException.class,
                        e -> assertThat(e.violation())
                                .isEqualTo(EventViolation.CRITICAL_METADATA_FORBIDDEN));
        assertThat(eventRows()).isEmpty();
        assertThat(store.head(CONTEXT)).isEmpty();
    }

    @Test void criticalEventRequiresJournalVersionAndCommitTime() {
        EventSubmission submission = new EventSubmission(CONTEXT,
                payload("EVENT-001", "BOOT-001", "SUCCEEDED", "ACQ-A"),
                EventDurability.CRITICAL_COMMITTED, null, null,
                "2026-11-18T12:00:01Z", "2026-11-18T12:00:02Z", null);

        assertThatThrownBy(() -> store.append(submission))
                .isInstanceOfSatisfying(EventValidationException.class,
                        e -> assertThat(e.violation())
                                .isEqualTo(EventViolation.CRITICAL_METADATA_REQUIRED));
        assertThat(eventRows()).isEmpty();
    }

    @Test void composedBusinessWriteCommitsInTheSameTransaction() {
        String businessPk = "BIZ#SIM#TX-001";
        store = new DynamoEventStore(db, table, MANIFEST, new EventValidator(),
                Clock.fixed(NOW, ZoneOffset.UTC), (submission, sequence, wrapper) ->
                        List.of(TransactWriteItem.builder()
                                .put(Put.builder().tableName(table)
                                        .item(Map.of("pk", text(businessPk), "sk", text("META")))
                                        .conditionExpression("attribute_not_exists(pk)")
                                        .build())
                                .build()));

        EventAppendResult result = store.append(critical("EVENT-001", "BOOT-001", "SUCCEEDED"));

        assertThat(result.stored()).isTrue();
        assertThat(eventRows()).hasSize(1);
        assertThat(db.getItem(GetItemRequest.builder().tableName(table)
                .key(Map.of("pk", text(businessPk), "sk", text("META")))
                .consistentRead(true).build()).item()).isNotNull().isNotEmpty();
    }

    @Test void failedComposedWriteLeavesNoEventHeadOrDedupe() {
        String businessPk = "BIZ#SIM#TX-001";
        db.putItem(PutItemRequest.builder().tableName(table)
                .item(Map.of("pk", text(businessPk), "sk", text("META"))).build());
        store = new DynamoEventStore(db, table, MANIFEST, new EventValidator(),
                Clock.fixed(NOW, ZoneOffset.UTC), (submission, sequence, wrapper) ->
                        List.of(TransactWriteItem.builder()
                                .put(Put.builder().tableName(table)
                                        .item(Map.of("pk", text(businessPk), "sk", text("META")))
                                        .conditionExpression("attribute_not_exists(pk)")
                                        .build())
                                .build()));
        EventSubmission submission = critical("EVENT-001", "BOOT-001", "SUCCEEDED");

        assertThatThrownBy(() -> store.append(submission))
                .isInstanceOf(TransactionCanceledException.class);

        assertThat(eventRows()).isEmpty();
        assertThat(store.head(CONTEXT)).isEmpty();
        assertThat(store.find(submission.dedupeKey())).isEmpty();
    }

    @Test void businessCompositionIsRejectedForBestEffortEvents() {
        store = new DynamoEventStore(db, table, MANIFEST, new EventValidator(),
                Clock.fixed(NOW, ZoneOffset.UTC), (submission, sequence, wrapper) ->
                        List.of(TransactWriteItem.builder()
                                .put(Put.builder().tableName(table)
                                        .item(Map.of("pk", text("BIZ#SIM#TX-001"),
                                                "sk", text("META")))
                                        .build())
                                .build()));
        EventSubmission submission = new EventSubmission(CONTEXT,
                payload("EVENT-001", "BOOT-001", "SUCCEEDED", "ACQ-A"),
                EventDurability.TASK_BEST_EFFORT, null, null,
                "2026-11-18T12:00:01Z", "2026-11-18T12:00:02Z", null);

        assertThatThrownBy(() -> store.append(submission))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("business composition");
        assertThat(eventRows()).isEmpty();
    }

    private EventSubmission critical(String eventId, String instanceId, String status) {
        return new EventSubmission(CONTEXT, payload(eventId, instanceId, status, "ACQ-A"),
                EventDurability.CRITICAL_COMMITTED, 1L,
                "2026-11-18T12:00:01Z", "2026-11-18T12:00:01Z", "2026-11-18T12:00:05Z",
                "APPROVED");
    }

    private static EventSubmission bestEffort(String eventId, String collectedAt) {
        return new EventSubmission(CONTEXT,
                payload(eventId, "BOOT-001", "SUCCEEDED", "ACQ-A"),
                EventDurability.TASK_BEST_EFFORT, null, null, collectedAt,
                "2026-11-18T12:00:05Z", null);
    }

    private static CanonicalEvent payload(String eventId, String instanceId, String status,
            String acquirerRef) {
        try {
            ObjectNode node = (ObjectNode) MAPPER.readTree(VECTORS.toFile())
                    .path("events").get(0).path("producerPayload");
            node.put("eventId", eventId);
            node.put("instanceId", instanceId);
            node.put("status", status);
            node.put("acquirerRef", acquirerRef);
            return CanonicalEventJson.parse(node.toString(), MANIFEST);
        } catch (Exception e) {
            throw new IllegalStateException("cannot read event fixture", e);
        }
    }

    private List<Map<String, AttributeValue>> eventRows() {
        var rows = new ArrayList<Map<String, AttributeValue>>();
        Map<String, AttributeValue> cursor = Map.of();
        do {
            QueryRequest.Builder query = QueryRequest.builder().tableName(table)
                    .consistentRead(true)
                    .keyConditionExpression("pk = :pk AND begins_with(sk, :prefix)")
                    .expressionAttributeValues(Map.of(
                            ":pk", text(EventTableKeys.eventPartition(CONTEXT)),
                            ":prefix", text("E#")));
            if (!cursor.isEmpty()) {
                query.exclusiveStartKey(cursor);
            }
            QueryResponse page = db.query(query.build());
            rows.addAll(page.items());
            cursor = page.lastEvaluatedKey();
        } while (!cursor.isEmpty());
        return rows;
    }

    private Map<String, AttributeValue> dedupeRow(EventDedupeKey key) {
        return db.getItem(GetItemRequest.builder().tableName(table)
                .key(Map.of("pk", text(EventTableKeys.dedupePartition(key.environment())),
                        "sk", text(EventTableKeys.dedupeSortKey(key))))
                .consistentRead(true).build()).item();
    }

    private static AttributeValue text(String value) {
        return AttributeValue.builder().s(value).build();
    }

    private static AttributeValue number(long value) {
        return AttributeValue.builder().n(Long.toString(value)).build();
    }
}
