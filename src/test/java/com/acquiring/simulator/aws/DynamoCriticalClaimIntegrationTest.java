package com.acquiring.simulator.aws;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.acquiring.simulator.adapter.aws.ClaimCriticalComposer;
import com.acquiring.simulator.adapter.aws.CriticalWriteComposer;
import com.acquiring.simulator.adapter.aws.DynamoAuthorizationRepository;
import com.acquiring.simulator.adapter.aws.DynamoEventStore;
import com.acquiring.simulator.domain.event.*;
import com.acquiring.simulator.domain.model.AuthorizationRecord;
import com.acquiring.simulator.domain.model.AuthorizationState;
import com.acquiring.simulator.domain.model.CvmIndicator;
import com.acquiring.simulator.domain.model.DecisionSource;
import com.acquiring.simulator.domain.model.EntryMode;
import com.acquiring.simulator.domain.model.RiskIndicator;
import com.acquiring.simulator.domain.model.Route;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.*;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

/**
 * Atomic initial-claim composition against DynamoDB Local: business record + business head +
 * critical event + EVENT head + dedupe metadata (+ optional slot reservation) must commit or abort
 * as one transaction. Local emulator behavior only; AWS throttling, Streams, IAM, encryption and TTL
 * timing remain unverified.
 */
@Testcontainers
class DynamoCriticalClaimIntegrationTest {

    @Container static final GenericContainer<?> DYNAMO = new GenericContainer<>(
            DockerImageName.parse("amazon/dynamodb-local:2.6.1")).withExposedPorts(8000);

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Path VECTORS =
            Path.of("docs/ai-sdlc/work-items/WI-ACQ-OBS-001/vectors/event-vectors.json");
    /** Test-only example capacity; owner approval of O8 numbers is still pending. */
    private static final CriticalCapacity CAPACITY = new CriticalCapacity(5, 8);
    private static final EventManifest MANIFEST = OperationAManifest.manifest(CAPACITY);
    private static final EventContext CONTEXT =
            new EventContext("SIM", "ACQ-A", "MERCHANT-A", "TX-001");
    private static final String BUSINESS_KEY = "ACQ-A|MERCHANT-A|TX-001";
    private static final String AUTHORIZATION_ID = "AUTH-001";
    private static final Instant NOW = Instant.parse("2026-11-18T12:00:01Z");

    static DynamoDbClient db;
    String eventTable;
    String journalTable;
    DynamoEventStore store;
    DynamoAuthorizationRepository repository;

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

    @BeforeEach void createTables() {
        eventTable = "events-" + UUID.randomUUID();
        journalTable = "journal-" + UUID.randomUUID();
        createTable(eventTable);
        createTable(journalTable);
        repository = new DynamoAuthorizationRepository(db, journalTable);
        store = new DynamoEventStore(db, eventTable, MANIFEST, new EventValidator(),
                Clock.fixed(NOW, ZoneOffset.UTC), CriticalWriteComposer.NONE);
    }

    @AfterEach void dropTables() {
        db.deleteTable(b -> b.tableName(eventTable));
        db.deleteTable(b -> b.tableName(journalTable));
    }

    @Test void claimCommitsBusinessHeadEventHeadDedupeAndSlotInOneTransaction() {
        AuthorizationRecord claim = claim();
        EventSubmission submission = claimSubmission(claim);

        EventAppendResult result = store.append(submission,
                ClaimCriticalComposer.withSlotAccounting(journalTable, eventTable, claim, CAPACITY));

        assertThat(result.decision()).isEqualTo(EventDeduplication.Decision.NEW);
        assertThat(result.sourceSequence()).isEqualTo(1);

        // Business side is readable through the real repository.
        assertThat(repository.findById(AUTHORIZATION_ID)).contains(claim);
        assertThat(repository.findLatestByBusinessKey(BUSINESS_KEY)).contains(claim);
        assertThat(rawJournalItem("AUTH#" + AUTHORIZATION_ID, "META").get("version").n())
                .isEqualTo("0");

        // Event side: one row, HEAD at 1, dedupe points at the stored sequence.
        EventHead head = store.head(CONTEXT).orElseThrow();
        assertThat(head.lastSequence()).isEqualTo(1);
        assertThat(store.find(submission.dedupeKey()).orElseThrow().sourceSequence()).isEqualTo(1);
        assertThat(eventRows()).hasSize(1);

        // Slot accounting was reserved inside the same transaction.
        assertThat(slotRow().get("usedSlots").n()).isEqualTo("1");
        assertThat(slotRow().get("slotBudget").n()).isEqualTo("8");
    }

    @Test void claimPersistsCommittedVersionOneBesideUnchangedCasZero() {
        AuthorizationRecord claim = claim();
        EventSubmission submission = claimSubmission(claim);

        store.append(submission,
                ClaimCriticalComposer.withSlotAccounting(journalTable, eventTable, claim, CAPACITY));

        // Two distinct persisted facts: CAS base stays 0, public committed version is 1.
        Map<String, AttributeValue> record =
                rawJournalItem("AUTH#" + AUTHORIZATION_ID, "META");
        assertThat(record.get("version").n()).isEqualTo("0");
        assertThat(record.get("committedJournalVersion").n()).isEqualTo("1");

        CommittedVersionEvidence evidence = repository.readVersionEvidence(AUTHORIZATION_ID);
        assertThat(evidence.storageVersion()).isZero();
        assertThat(evidence.committedJournalVersion()).isEqualTo(1L);
        assertThat(evidence.agreesWith(0L, 1L)).isTrue();

        // Stored event evidence agrees with the persisted committed version.
        StoredEvent stored = store.find(submission.dedupeKey()).orElseThrow();
        assertThat(stored.journalVersion()).isEqualTo(1L);
        assertThat(stored.stateAfter()).isEqualTo(ClaimCriticalComposer.CLAIM_STATE_AFTER);
        assertThat(eventRows().getFirst().get("journalVersion").n()).isEqualTo("1");
    }

    @Test void legacyClaimRowReportsMissingCommittedVersionWithoutBackfill() {
        AuthorizationRecord claim = claim();

        // The pre-existing claim path is unchanged in this slice and writes no committed version.
        repository.claim(claim);

        Map<String, AttributeValue> record =
                rawJournalItem("AUTH#" + AUTHORIZATION_ID, "META");
        assertThat(record.get("version").n()).isEqualTo("0");
        assertThat(record).doesNotContainKey("committedJournalVersion");

        CommittedVersionEvidence evidence = repository.readVersionEvidence(AUTHORIZATION_ID);
        assertThat(evidence.storageVersion()).isZero();
        assertThat(evidence.committedJournalVersion()).isNull();
        assertThat(evidence.committedVersionRecorded()).isFalse();
        assertThat(evidence.agreesWith(0L, 1L)).isFalse();
    }

    @Test void failedBusinessPreconditionLeavesNoEventHeadDedupeSlotOrBusinessRow() {
        db.putItem(PutItemRequest.builder().tableName(journalTable)
                .item(Map.of(
                        "pk", text("AUTH#" + AUTHORIZATION_ID),
                        "sk", text("META"),
                        "payload", text("{}"),
                        "version", AttributeValue.builder().n("0").build()))
                .build());
        AuthorizationRecord claim = claim();
        EventSubmission submission = claimSubmission(claim);

        assertThatThrownBy(() -> store.append(submission,
                ClaimCriticalComposer.withSlotAccounting(journalTable, eventTable, claim, CAPACITY)))
                .isInstanceOf(TransactionCanceledException.class);

        assertThat(eventRows()).isEmpty();
        assertThat(store.head(CONTEXT)).isEmpty();
        assertThat(store.find(submission.dedupeKey())).isEmpty();
        assertThat(slotRow()).isEmpty();
        assertThat(repository.findLatestByBusinessKey(BUSINESS_KEY)).isEmpty();
    }

    @Test void exhaustedSlotBudgetAbortsTheWholeClaim() {
        db.putItem(PutItemRequest.builder().tableName(eventTable)
                .item(Map.of(
                        "pk", text(EventTableKeys.capacityPartition(CONTEXT)),
                        "sk", text(EventTableKeys.capacitySortKey()),
                        "usedSlots", AttributeValue.builder().n("1").build(),
                        "slotBudget", AttributeValue.builder().n("1").build()))
                .build());
        AuthorizationRecord claim = claim();
        EventSubmission submission = claimSubmission(claim);

        assertThatThrownBy(() -> store.append(submission,
                ClaimCriticalComposer.withSlotAccounting(journalTable, eventTable, claim,
                        new CriticalCapacity(1, 1))))
                .isInstanceOf(TransactionCanceledException.class);

        assertThat(eventRows()).isEmpty();
        assertThat(store.head(CONTEXT)).isEmpty();
        assertThat(store.find(submission.dedupeKey())).isEmpty();
        assertThat(repository.findById(AUTHORIZATION_ID)).isEmpty();
        assertThat(repository.findLatestByBusinessKey(BUSINESS_KEY)).isEmpty();
    }

    @Test void replayedClaimWritesNoSecondBusinessRowOrSlotReservation() {
        AuthorizationRecord claim = claim();
        EventSubmission submission = claimSubmission(claim);
        ClaimCriticalComposer composer =
                ClaimCriticalComposer.withSlotAccounting(journalTable, eventTable, claim, CAPACITY);

        store.append(submission, composer);
        EventAppendResult replay = store.append(submission, composer);

        assertThat(replay.decision()).isEqualTo(EventDeduplication.Decision.EXACT_REPLAY);
        assertThat(replay.sourceSequence()).isEqualTo(1);
        assertThat(eventRows()).hasSize(1);
        assertThat(store.head(CONTEXT).orElseThrow().lastSequence()).isEqualTo(1);
        assertThat(slotRow().get("usedSlots").n()).isEqualTo("1");
        assertThat(rawJournalItem("AUTH#" + AUTHORIZATION_ID, "META").get("version").n())
                .isEqualTo("0");
    }

    @Test void claimEventThatDescribesAnotherTransitionIsRejectedBeforeAnyWrite() {
        AuthorizationRecord claim = claim();
        EventSubmission mislabelled = new EventSubmission(CONTEXT, payload("EVENT-001", "BOOT-001"),
                EventDurability.CRITICAL_COMMITTED, 2L,
                "2026-11-18T12:00:01Z", "2026-11-18T12:00:01Z", "2026-11-18T12:00:01Z",
                "POSSIBLE_SEND");

        assertThatThrownBy(() -> store.append(mislabelled,
                ClaimCriticalComposer.withoutSlotAccounting(journalTable, eventTable, claim)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("journal version");

        assertThat(eventRows()).isEmpty();
        assertThat(store.head(CONTEXT)).isEmpty();
        assertThat(repository.findById(AUTHORIZATION_ID)).isEmpty();
    }

    @Test void compositionWithoutExplicitCapacityWritesNoSlotRow() {
        AuthorizationRecord claim = claim();

        store.append(claimSubmission(claim),
                ClaimCriticalComposer.withoutSlotAccounting(journalTable, eventTable, claim));

        assertThat(slotRow()).isEmpty();
        assertThat(repository.findById(AUTHORIZATION_ID)).contains(claim);
        assertThat(store.head(CONTEXT).orElseThrow().lastSequence()).isEqualTo(1);
    }

    private AuthorizationRecord claim() {
        return new AuthorizationRecord(
                AUTHORIZATION_ID,
                BUSINESS_KEY,
                "ACQ-A",
                "MERCHANT-A",
                "TX-001",
                1250L,
                "USD",
                "CRED-A",
                "TERMINAL-A",
                EntryMode.SIMULATED_CONTACTLESS,
                CvmIndicator.SIMULATED_VERIFIED,
                true,
                RiskIndicator.PASS,
                Route.DOMESTIC,
                "fingerprint-001",
                1,
                AuthorizationState.AWAITING,
                DecisionSource.NONE,
                null,
                null,
                NOW,
                null,
                null,
                NOW.plusSeconds(300));
    }

    private EventSubmission claimSubmission(AuthorizationRecord claim) {
        return new EventSubmission(CONTEXT, payload("EVENT-001", "BOOT-001"),
                EventDurability.CRITICAL_COMMITTED, ClaimCriticalComposer.CLAIM_JOURNAL_VERSION,
                "2026-11-18T12:00:01Z", "2026-11-18T12:00:01Z", "2026-11-18T12:00:01Z",
                ClaimCriticalComposer.CLAIM_STATE_AFTER);
    }

    private static CanonicalEvent payload(String eventId, String instanceId) {
        try {
            ObjectNode node = (ObjectNode) MAPPER.readTree(VECTORS.toFile())
                    .path("events").get(0).path("producerPayload");
            node.put("eventId", eventId);
            node.put("instanceId", instanceId);
            node.put("transactionId", "TX-001");
            node.put("taskName", "business-persist");
            node.put("status", "SUCCEEDED");
            return CanonicalEventJson.parse(node.toString(), MANIFEST);
        } catch (Exception e) {
            throw new IllegalStateException("cannot read event fixture", e);
        }
    }

    private List<Map<String, AttributeValue>> eventRows() {
        List<Map<String, AttributeValue>> rows = new ArrayList<>();
        Map<String, AttributeValue> cursor = Map.of();
        do {
            QueryRequest.Builder query = QueryRequest.builder().tableName(eventTable)
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
        } while (cursor != null && !cursor.isEmpty());
        return rows;
    }

    private Map<String, AttributeValue> slotRow() {
        var item = db.getItem(GetItemRequest.builder().tableName(eventTable)
                .key(Map.of(
                        "pk", text(EventTableKeys.capacityPartition(CONTEXT)),
                        "sk", text(EventTableKeys.capacitySortKey())))
                .consistentRead(true).build()).item();
        return item == null ? Map.of() : item;
    }

    private Map<String, AttributeValue> rawJournalItem(String pk, String sk) {
        return db.getItem(GetItemRequest.builder().tableName(journalTable)
                .key(Map.of("pk", text(pk), "sk", text(sk)))
                .consistentRead(true).build()).item();
    }

    private void createTable(String name) {
        db.createTable(CreateTableRequest.builder()
                .tableName(name)
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
        db.waiter().waitUntilTableExists(b -> b.tableName(name));
    }

    private static AttributeValue text(String value) {
        return AttributeValue.builder().s(value).build();
    }
}
