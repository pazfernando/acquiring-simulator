package com.acquiring.simulator.aws;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.acquiring.simulator.adapter.aws.CriticalWriteComposer;
import com.acquiring.simulator.adapter.aws.DynamoAuthorizationRepository;
import com.acquiring.simulator.adapter.aws.DynamoClaimGateway;
import com.acquiring.simulator.adapter.aws.DynamoDispatchIntentGateway;
import com.acquiring.simulator.adapter.aws.DynamoEventStore;
import com.acquiring.simulator.adapter.aws.LateEvidenceCriticalComposer;
import com.acquiring.simulator.domain.event.CriticalCapacity;
import com.acquiring.simulator.domain.event.EventAppendResult;
import com.acquiring.simulator.domain.event.EventContext;
import com.acquiring.simulator.domain.event.EventDeduplication;
import com.acquiring.simulator.domain.event.EventManifest;
import com.acquiring.simulator.domain.event.EventTableKeys;
import com.acquiring.simulator.domain.event.EventValidator;
import com.acquiring.simulator.domain.event.OperationAManifest;
import com.acquiring.simulator.domain.model.AuthorizationRecord;
import com.acquiring.simulator.domain.model.AuthorizationState;
import com.acquiring.simulator.domain.model.CvmIndicator;
import com.acquiring.simulator.domain.model.DecisionSource;
import com.acquiring.simulator.domain.model.DownstreamReply;
import com.acquiring.simulator.domain.model.EntryMode;
import com.acquiring.simulator.domain.model.IssuerDecision;
import com.acquiring.simulator.domain.model.RiskIndicator;
import com.acquiring.simulator.domain.model.Route;
import com.acquiring.simulator.domain.port.ClaimGateway;
import com.acquiring.simulator.domain.port.DispatchIntentGateway;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.KeySchemaElement;
import software.amazon.awssdk.services.dynamodb.model.KeyType;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType;
import software.amazon.awssdk.services.dynamodb.model.TransactionCanceledException;

/**
 * P1b6b late verified-evidence composer against DynamoDB Local (observation contract O3/P5,
 * OBS-08, R07/R20).
 *
 * <p>These tests exercise one bounded slice: the atomic composer for a confirmed issuer decision
 * whose evidence time is strictly after the dispatch deadline. They prove local composition and
 * precedence mechanics only. Evidence authentication (C4 signatures, sender/relay roles) is NOT
 * exercised here: the composer receives an already-verified reply through an explicit internal
 * test seam, so a passing test does not mean a {@code DownstreamReply} is authenticated production
 * proof. The late handler, service branch, runtime wiring and AWS service behaviour remain
 * unverified.
 */
@Testcontainers
class DynamoLateEvidenceTransitionIntegrationTest {

    @Container static final GenericContainer<?> DYNAMO = new GenericContainer<>(
            DockerImageName.parse("amazon/dynamodb-local:2.6.1")).withExposedPorts(8000);

    /** Test-only example capacity; owner approval of the O8 numbers is still pending. */
    private static final CriticalCapacity CAPACITY = new CriticalCapacity(5, 8);
    private static final EventManifest MANIFEST = OperationAManifest.manifest(CAPACITY);
    private static final EventContext CONTEXT =
            new EventContext("SIM", "ACQ-A", "MERCHANT-A", "TX-001");
    private static final String BUSINESS_KEY = "ACQ-A|MERCHANT-A|TX-001";
    private static final String AUTHORIZATION_ID = "AUTH-001";
    private static final String DISPATCH_CORRELATION = "CORR-SEND-001";
    private static final String RECEIPT_ID = "LATE-RECEIPT-001";
    private static final Instant CLAIMED_AT = Instant.parse("2026-11-18T12:00:00Z");
    private static final Instant DISPATCHED_AT = Instant.parse("2026-11-18T12:00:01Z");
    /** Explicit caller-supplied deadline; this test does not introduce a default timeout. */
    private static final Instant DEADLINE = Instant.parse("2026-11-18T12:00:03Z");
    private static final Instant LATE_EVIDENCE_AT = Instant.parse("2026-11-18T12:00:05Z");
    private static final Instant LATE_DELIVERED_AT = Instant.parse("2026-11-18T12:00:20Z");

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
                Clock.fixed(LATE_DELIVERED_AT, ZoneOffset.UTC), CriticalWriteComposer.NONE);
    }

    @AfterEach void dropTables() {
        db.deleteTable(b -> b.tableName(eventTable));
        db.deleteTable(b -> b.tableName(journalTable));
    }

    @Test void lateApprovalAfterTheBoundaryCommitsOneConfirmedResult() {
        AuthorizationRecord intent = dispatchedIntent();

        EventAppendResult result = store.append(
                LateEvidenceCriticalComposer.submission(intent, approved(), "SIM", "BOOT-P1B6B"),
                lateApproval(intent));

        assertThat(result.stored()).isTrue();
        assertThat(result.sourceSequence()).isEqualTo(3L);

        AuthorizationRecord persisted = repository.findById(AUTHORIZATION_ID).orElseThrow();
        assertThat(persisted.state()).isEqualTo(AuthorizationState.APPROVED);
        assertThat(persisted.decisionSource()).isEqualTo(DecisionSource.ISSUER);
        // The confirmed time is the evidence time, not the later delivery time.
        assertThat(persisted.outcomeDeterminedAt()).isEqualTo(LATE_EVIDENCE_AT);
        assertThat(persisted.dispatchedAt()).isEqualTo(DISPATCHED_AT);
        assertThat(persisted.dispatchCorrelationId()).isEqualTo(DISPATCH_CORRELATION);
        assertThat(repository.readVersionEvidence(AUTHORIZATION_ID).agreesWith(2L, 3L)).isTrue();

        assertThat(eventRows()).hasSize(3);
        assertThat(store.head(CONTEXT).orElseThrow().lastSequence()).isEqualTo(3);
        assertThat(slotRow().get("usedSlots").n()).isEqualTo("3");
        assertThat(eventRows().get(2).get("eventId").s()).isEqualTo("APPROVED-AUTH-001");

        // The late distinction lives in receipt metadata, not in a new event task or reason.
        Map<String, AttributeValue> meta = recordItem();
        assertThat(meta.get("lateEvidence").bool()).isTrue();
        assertThat(meta.get("evidenceReceiptId").s()).isEqualTo(RECEIPT_ID);
        assertThat(meta.get("evidenceAvailableAt").s()).isEqualTo("2026-11-18T12:00:05Z");
        assertThat(meta.get("evidenceDeliveredAt").s()).isEqualTo("2026-11-18T12:00:20Z");
        assertThat(meta.get("evidenceDeadline").s()).isEqualTo("2026-11-18T12:00:03Z");
    }

    @Test void lateDeclineAfterTheBoundaryCommitsOneConfirmedResult() {
        AuthorizationRecord intent = dispatchedIntent();

        EventAppendResult result = store.append(
                LateEvidenceCriticalComposer.submission(intent, declined(), "SIM", "BOOT-P1B6B"),
                lateDeclined(intent));

        assertThat(result.stored()).isTrue();
        assertThat(result.sourceSequence()).isEqualTo(3L);

        AuthorizationRecord persisted = repository.findById(AUTHORIZATION_ID).orElseThrow();
        assertThat(persisted.state()).isEqualTo(AuthorizationState.DECLINED);
        assertThat(persisted.decisionSource()).isEqualTo(DecisionSource.ISSUER);
        assertThat(persisted.outcomeDeterminedAt()).isEqualTo(LATE_EVIDENCE_AT);

        assertThat(eventRows()).hasSize(3);
        assertThat(slotRow().get("usedSlots").n()).isEqualTo("3");
        assertThat(eventRows().get(2).get("eventId").s()).isEqualTo("DECLINED-AUTH-001");
    }

    @Test void exactReplayOfIdenticalLateEvidenceAddsNoVersionEventOrSlot() {
        AuthorizationRecord intent = dispatchedIntent();
        store.append(LateEvidenceCriticalComposer.submission(intent, approved(), "SIM",
                "BOOT-P1B6B"), lateApproval(intent));

        EventAppendResult replay = store.append(
                LateEvidenceCriticalComposer.submission(intent, approved(), "SIM", "BOOT-P1B6B"),
                lateApproval(intent));

        assertThat(replay.decision()).isEqualTo(EventDeduplication.Decision.EXACT_REPLAY);
        assertThat(replay.sourceSequence()).isEqualTo(3L);

        // Applied once: one result event, one HEAD advance, one slot, one committed version.
        assertThat(eventRows()).hasSize(3);
        assertThat(store.head(CONTEXT).orElseThrow().lastSequence()).isEqualTo(3L);
        assertThat(slotRow().get("usedSlots").n()).isEqualTo("3");
        assertThat(repository.readVersionEvidence(AUTHORIZATION_ID).agreesWith(2L, 3L)).isTrue();
        assertThat(recordItem().get("evidenceReceiptId").s()).isEqualTo(RECEIPT_ID);
    }

    @Test void contradictoryLateEvidenceIsRetainedAndNeverOverrides() {
        AuthorizationRecord intent = dispatchedIntent();
        store.append(LateEvidenceCriticalComposer.submission(intent, approved(), "SIM",
                "BOOT-P1B6B"), lateApproval(intent));

        // A contradicting late decline targets a different stable identity and must be refused.
        assertThatThrownBy(() -> store.append(
                LateEvidenceCriticalComposer.submission(intent, declined(), "SIM", "BOOT-P1B6B"),
                lateDeclined(intent)))
                .isInstanceOf(TransactionCanceledException.class);

        // The first confirmed result and its evidence are retained: no override, no extra write.
        AuthorizationRecord persisted = repository.findById(AUTHORIZATION_ID).orElseThrow();
        assertThat(persisted.state()).isEqualTo(AuthorizationState.APPROVED);
        assertThat(persisted.decisionSource()).isEqualTo(DecisionSource.ISSUER);
        assertThat(persisted.outcomeDeterminedAt()).isEqualTo(LATE_EVIDENCE_AT);
        assertThat(eventRows()).hasSize(3);
        assertThat(store.head(CONTEXT).orElseThrow().lastSequence()).isEqualTo(3L);
        assertThat(slotRow().get("usedSlots").n()).isEqualTo("3");
        assertThat(repository.readVersionEvidence(AUTHORIZATION_ID).agreesWith(2L, 3L)).isTrue();
        assertThat(eventRows()).filteredOn(row -> "APPROVED-AUTH-001"
                .equals(row.get("eventId").s())).hasSize(1);
        assertThat(eventRows()).filteredOn(row -> "DECLINED-AUTH-001"
                .equals(row.get("eventId").s())).isEmpty();
    }

    @Test void legacyRowWithoutCommittedVersionIsRefusedWithoutFabrication() {
        AuthorizationRecord intent = dispatchedIntent();
        // A legacy row never received the additive committed version attribute.
        db.updateItem(b -> b.tableName(journalTable)
                .key(recordKey())
                .updateExpression("REMOVE committedJournalVersion"));

        assertThatThrownBy(() -> store.append(
                LateEvidenceCriticalComposer.submission(intent, approved(), "SIM", "BOOT-P1B6B"),
                lateApproval(intent)))
                .isInstanceOf(TransactionCanceledException.class);

        // No fabricated version, no migration and no partial result.
        Map<String, AttributeValue> meta = recordItem();
        assertThat(meta.get("committedJournalVersion")).isNull();
        assertThat(meta.get("version").n()).isEqualTo("1");
        AuthorizationRecord persisted = repository.findById(AUTHORIZATION_ID).orElseThrow();
        assertThat(persisted.state()).isEqualTo(AuthorizationState.AWAITING);
        assertThat(persisted.outcomeDeterminedAt()).isNull();
        assertThat(eventRows()).hasSize(2);
        assertThat(store.head(CONTEXT).orElseThrow().lastSequence()).isEqualTo(2L);
        assertThat(slotRow().get("usedSlots").n()).isEqualTo("2");
    }

    @Test void boundaryIsStrictlyAfterTheDeadline() {
        AuthorizationRecord intent = dispatchedIntent();

        // Just before the deadline, delivered by the deadline: on time.
        assertThat(LateEvidenceCriticalComposer.classify(
                DEADLINE.minusMillis(1), DEADLINE, DEADLINE))
                .isEqualTo(LateEvidenceCriticalComposer.Timing.ON_TIME);
        // Exactly at the deadline: A24 keeps it confirmed, so it is still not late.
        assertThat(LateEvidenceCriticalComposer.classify(DEADLINE, DEADLINE, DEADLINE))
                .isEqualTo(LateEvidenceCriticalComposer.Timing.ON_TIME);
        // Strictly after: late.
        assertThat(LateEvidenceCriticalComposer.classify(
                DEADLINE.plusMillis(1), DEADLINE.plusMillis(1), DEADLINE))
                .isEqualTo(LateEvidenceCriticalComposer.Timing.LATE);

        assertThatThrownBy(() -> LateEvidenceCriticalComposer.withoutSlotAccounting(
                journalTable, eventTable, intent,
                reply(IssuerDecision.APPROVED, DEADLINE.minusMillis(1)),
                DEADLINE, DEADLINE.plusSeconds(5), RECEIPT_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not late evidence");
        assertThatThrownBy(() -> LateEvidenceCriticalComposer.withoutSlotAccounting(
                journalTable, eventTable, intent, reply(IssuerDecision.APPROVED, DEADLINE),
                DEADLINE, DEADLINE, RECEIPT_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not late evidence");
        assertThat(LateEvidenceCriticalComposer.withoutSlotAccounting(
                journalTable, eventTable, intent,
                reply(IssuerDecision.APPROVED, DEADLINE.plusMillis(1)),
                DEADLINE, DEADLINE.plusMillis(1), RECEIPT_ID).timing())
                .isEqualTo(LateEvidenceCriticalComposer.Timing.LATE);
    }

    @Test void deliveryDelayIsNotLateEvidence() {
        AuthorizationRecord intent = dispatchedIntent();
        Instant evidenceAt = DEADLINE.minusSeconds(1);

        LateEvidenceCriticalComposer.Timing timing = LateEvidenceCriticalComposer.classify(
                evidenceAt, LATE_DELIVERED_AT, DEADLINE);
        assertThat(timing).isEqualTo(LateEvidenceCriticalComposer.Timing.DELIVERY_DELAY);

        // Even a long delivery delay cannot relabel on-time evidence as late evidence.
        assertThatThrownBy(() -> LateEvidenceCriticalComposer.withoutSlotAccounting(
                journalTable, eventTable, intent,
                reply(IssuerDecision.APPROVED, evidenceAt),
                DEADLINE, LATE_DELIVERED_AT, RECEIPT_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not late evidence");
    }

    @Test void deliveryCannotPrecedeEvidenceAvailability() {
        AuthorizationRecord intent = dispatchedIntent();

        assertThatThrownBy(() -> LateEvidenceCriticalComposer.classify(
                LATE_EVIDENCE_AT, LATE_EVIDENCE_AT.minusSeconds(1), DEADLINE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("delivery cannot precede");
        assertThatThrownBy(() -> LateEvidenceCriticalComposer.withoutSlotAccounting(
                journalTable, eventTable, intent, approved(), DEADLINE,
                LATE_EVIDENCE_AT.minusSeconds(1), RECEIPT_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("delivery cannot precede");
    }

    @Test void nonConfirmedOrUncorrelatedEvidenceIsRefusedBeforeAdmission() {
        AuthorizationRecord intent = dispatchedIntent();

        assertThatThrownBy(() -> LateEvidenceCriticalComposer.withoutSlotAccounting(
                journalTable, eventTable, intent,
                reply(IssuerDecision.MALFORMED, LATE_EVIDENCE_AT),
                DEADLINE, LATE_DELIVERED_AT, RECEIPT_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("APPROVED or DECLINED");
        assertThatThrownBy(() -> LateEvidenceCriticalComposer.withoutSlotAccounting(
                journalTable, eventTable, intent,
                reply(IssuerDecision.NO_RESPONSE, LATE_EVIDENCE_AT),
                DEADLINE, LATE_DELIVERED_AT, RECEIPT_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("APPROVED or DECLINED");
        assertThatThrownBy(() -> LateEvidenceCriticalComposer.withoutSlotAccounting(
                journalTable, eventTable, intent,
                new DownstreamReply("CORR-FOREIGN", IssuerDecision.APPROVED, LATE_EVIDENCE_AT),
                DEADLINE, LATE_DELIVERED_AT, RECEIPT_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("correlation");
        assertThatThrownBy(() -> LateEvidenceCriticalComposer.withoutSlotAccounting(
                journalTable, eventTable, intent,
                reply(IssuerDecision.APPROVED, DISPATCHED_AT.minusSeconds(1)),
                DEADLINE, LATE_DELIVERED_AT, RECEIPT_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("predate the durable dispatch");
        assertThatThrownBy(() -> LateEvidenceCriticalComposer.withoutSlotAccounting(
                journalTable, eventTable, intent, approved(), DEADLINE, LATE_DELIVERED_AT, ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("deliveryReceiptId");

        // Nothing was composed for any rejected candidate.
        assertThat(eventRows()).hasSize(2);
        assertThat(slotRow().get("usedSlots").n()).isEqualTo("2");
    }

    @Test void aPersistedLegacyUncertaintyRowIsNotTransitioned() {
        AuthorizationRecord legacy = dispatchedIntent().withOutcome(
                AuthorizationState.NOT_CONFIRMED, DecisionSource.NONE, null, DISPATCHED_AT);

        assertThatThrownBy(() -> LateEvidenceCriticalComposer.withoutSlotAccounting(
                journalTable, eventTable, legacy, approved(), DEADLINE, LATE_DELIVERED_AT,
                RECEIPT_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("legacy uncertainty");
    }

    @Test void aConfirmedOutcomeIsNeverOverriddenByLateEvidence() {
        AuthorizationRecord intent = dispatchedIntent();
        AuthorizationRecord confirmed = intent.withOutcome(AuthorizationState.DECLINED,
                DecisionSource.ISSUER, null, DEADLINE);

        assertThatThrownBy(() -> LateEvidenceCriticalComposer.withoutSlotAccounting(
                journalTable, eventTable, confirmed, approved(), DEADLINE, LATE_DELIVERED_AT,
                RECEIPT_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("never overrides");
    }

    private LateEvidenceCriticalComposer lateApproval(AuthorizationRecord intent) {
        return LateEvidenceCriticalComposer.withSlotAccounting(journalTable, eventTable, intent,
                approved(), DEADLINE, LATE_DELIVERED_AT, RECEIPT_ID, CAPACITY);
    }

    private LateEvidenceCriticalComposer lateDeclined(AuthorizationRecord intent) {
        return LateEvidenceCriticalComposer.withSlotAccounting(journalTable, eventTable, intent,
                declined(), DEADLINE, LATE_DELIVERED_AT, RECEIPT_ID, CAPACITY);
    }

    /** Claim, then commit the durable dispatch intent; returns the in-memory dispatched intent. */
    private AuthorizationRecord dispatchedIntent() {
        AuthorizationRecord claim = claim();
        ClaimGateway.Decision claimed = new DynamoClaimGateway(store, repository, journalTable,
                eventTable, CAPACITY, "SIM", "BOOT-P1B6B")
                .claim(claim, "CORR-CLAIM-001", CLAIMED_AT);
        assertThat(claimed.status()).isEqualTo(ClaimGateway.Status.CLAIMED);

        DispatchIntentGateway.Decision intent = new DynamoDispatchIntentGateway(store, repository,
                journalTable, eventTable, CAPACITY, "SIM", "BOOT-P1B6B")
                .dispatchIntent(claim, DISPATCH_CORRELATION, DISPATCHED_AT);
        assertThat(intent.status()).isEqualTo(DispatchIntentGateway.Status.COMMITTED);
        assertThat(intent.evidence().agreesWith(1L, 2L)).isTrue();
        return claim.withDispatch(DISPATCH_CORRELATION, DISPATCHED_AT);
    }

    private static DownstreamReply approved() {
        return reply(IssuerDecision.APPROVED, LATE_EVIDENCE_AT);
    }

    private static DownstreamReply declined() {
        return reply(IssuerDecision.DECLINED, LATE_EVIDENCE_AT);
    }

    /** Explicit internal test seam: an already-verified reply, not a production authentication. */
    private static DownstreamReply reply(IssuerDecision decision, Instant availableAt) {
        return new DownstreamReply(DISPATCH_CORRELATION, decision, availableAt);
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

    private Map<String, AttributeValue> recordItem() {
        return db.getItem(GetItemRequest.builder().tableName(journalTable)
                .key(recordKey())
                .consistentRead(true).build()).item();
    }

    /** Journal record key; mirrors the shared scheme without importing a package-private helper. */
    private static Map<String, AttributeValue> recordKey() {
        return Map.of("pk", text("AUTH#" + AUTHORIZATION_ID), "sk", text("META"));
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
                CLAIMED_AT,
                null,
                null,
                CLAIMED_AT.plusSeconds(300));
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
