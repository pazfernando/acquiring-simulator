package com.acquiring.simulator.aws;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.acquiring.simulator.adapter.aws.ClaimCriticalComposer;
import com.acquiring.simulator.adapter.aws.CriticalWriteComposer;
import com.acquiring.simulator.adapter.aws.DynamoAuthorizationRepository;
import com.acquiring.simulator.adapter.aws.DynamoEventStore;
import com.acquiring.simulator.adapter.aws.PossibleSendCriticalComposer;
import com.acquiring.simulator.adapter.aws.ResultCriticalComposer;
import com.acquiring.simulator.domain.event.CommittedVersionEvidence;
import com.acquiring.simulator.domain.event.CriticalCapacity;
import com.acquiring.simulator.domain.event.EventAppendResult;
import com.acquiring.simulator.domain.event.EventContext;
import com.acquiring.simulator.domain.event.EventDeduplication;
import com.acquiring.simulator.domain.event.EventDurability;
import com.acquiring.simulator.domain.event.EventManifest;
import com.acquiring.simulator.domain.event.EventSubmission;
import com.acquiring.simulator.domain.event.EventTableKeys;
import com.acquiring.simulator.domain.event.EventValidator;
import com.acquiring.simulator.domain.event.OperationAManifest;
import com.acquiring.simulator.domain.event.StoredEvent;
import com.acquiring.simulator.domain.model.AuthorizationRecord;
import com.acquiring.simulator.domain.model.AuthorizationState;
import com.acquiring.simulator.domain.model.CvmIndicator;
import com.acquiring.simulator.domain.model.DecisionSource;
import com.acquiring.simulator.domain.model.DownstreamReply;
import com.acquiring.simulator.domain.model.EntryMode;
import com.acquiring.simulator.domain.model.IssuerDecision;
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
 * P1b4a verified-result (APPROVED/DECLINED) composer against DynamoDB Local: one conditioned
 * business transition, an explicitly advanced committed journal version, the immutable critical
 * result event and EVENT head/dedupe metadata, and the explicit slot reservation commit or abort
 * together.
 *
 * <p>This proves local transaction mechanics only. No downstream call, no result gateway, no
 * ambiguous-result resolution and no production wiring are exercised; real AWS effects stay
 * unverified.
 */
@Testcontainers
class DynamoResultTransitionIntegrationTest {

    @Container static final GenericContainer<?> DYNAMO = new GenericContainer<>(
            DockerImageName.parse("amazon/dynamodb-local:2.6.1")).withExposedPorts(8000);

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Path VECTORS =
            Path.of("docs/ai-sdlc/work-items/WI-ACQ-OBS-001/vectors/event-vectors.json");
    /** Test-only example capacity; owner approval of the O8 numbers is still pending. */
    private static final CriticalCapacity CAPACITY = new CriticalCapacity(5, 8);
    private static final EventManifest MANIFEST = OperationAManifest.manifest(CAPACITY);
    private static final EventContext CONTEXT =
            new EventContext("SIM", "ACQ-A", "MERCHANT-A", "TX-001");
    private static final String BUSINESS_KEY = "ACQ-A|MERCHANT-A|TX-001";
    private static final String AUTHORIZATION_ID = "AUTH-001";
    private static final String DISPATCH_CORRELATION = "CORR-SEND-001";
    private static final Instant CLAIMED_AT = Instant.parse("2026-11-18T12:00:00Z");
    private static final Instant DISPATCHED_AT = Instant.parse("2026-11-18T12:00:01Z");
    private static final Instant EVIDENCE_AT = Instant.parse("2026-11-18T12:00:02Z");

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
                Clock.fixed(EVIDENCE_AT, ZoneOffset.UTC), CriticalWriteComposer.NONE);
    }

    @AfterEach void dropTables() {
        db.deleteTable(b -> b.tableName(eventTable));
        db.deleteTable(b -> b.tableName(journalTable));
    }

    @Test void approvedResultCommitsConfirmedOutcomeEventHeadDedupeAndSlotInOneTransaction() {
        AuthorizationRecord intent = dispatchedIntent();

        EventAppendResult result = store.append(resultSubmission(intent, approved()),
                resultComposer(intent, approved(), CAPACITY));

        assertThat(result.decision()).isEqualTo(EventDeduplication.Decision.NEW);
        assertThat(result.sourceSequence()).isEqualTo(3);

        // The persisted business record carries the issuer-confirmed outcome and provenance.
        AuthorizationRecord persisted = repository.findById(AUTHORIZATION_ID).orElseThrow();
        assertThat(persisted.state()).isEqualTo(AuthorizationState.APPROVED);
        assertThat(persisted.decisionSource()).isEqualTo(DecisionSource.ISSUER);
        assertThat(persisted.reasonCategory()).isNull();
        assertThat(persisted.outcomeDeterminedAt()).isEqualTo(EVIDENCE_AT);
        assertThat(persisted.dispatchedAt()).isEqualTo(DISPATCHED_AT);
        assertThat(persisted.dispatchCorrelationId()).isEqualTo(DISPATCH_CORRELATION);
        assertThat(persisted.paymentFingerprint()).isEqualTo(intent.paymentFingerprint());

        // One claim, one dispatch intent and one result event; HEAD advanced exactly once.
        assertThat(eventRows()).hasSize(3);
        assertThat(store.head(CONTEXT).orElseThrow().lastSequence()).isEqualTo(3);
        assertThat(slotRow().get("usedSlots").n()).isEqualTo("3");
        assertThat(slotRow().get("slotBudget").n()).isEqualTo("8");

        StoredEvent stored = store.find(resultSubmission(intent, approved()).dedupeKey())
                .orElseThrow();
        assertThat(stored.sourceSequence()).isEqualTo(3);
        assertThat(stored.journalVersion()).isEqualTo(3L);
        assertThat(stored.stateAfter()).isEqualTo(ResultCriticalComposer.APPROVED_STATE_AFTER);
        assertThat(eventRows().get(2).get("eventId").s()).isEqualTo("APPROVED-AUTH-001");

        // CAS advanced to 2; the committed journal version advanced explicitly to 3.
        Map<String, AttributeValue> record = rawJournalItem("AUTH#" + AUTHORIZATION_ID, "META");
        assertThat(record.get("version").n()).isEqualTo("2");
        assertThat(record.get("committedJournalVersion").n()).isEqualTo("3");
        CommittedVersionEvidence evidence = repository.readVersionEvidence(AUTHORIZATION_ID);
        assertThat(evidence.storageVersion()).isEqualTo(2);
        assertThat(evidence.committedJournalVersion()).isEqualTo(3L);
        assertThat(evidence.agreesWith(2L, 3L)).isTrue();
    }

    @Test void declinedResultCommitsDeclinedStateWithTheSameAtomicShape() {
        AuthorizationRecord intent = dispatchedIntent();

        store.append(resultSubmission(intent, declined()),
                resultComposer(intent, declined(), CAPACITY));

        AuthorizationRecord persisted = repository.findById(AUTHORIZATION_ID).orElseThrow();
        assertThat(persisted.state()).isEqualTo(AuthorizationState.DECLINED);
        assertThat(persisted.decisionSource()).isEqualTo(DecisionSource.ISSUER);
        assertThat(persisted.outcomeDeterminedAt()).isEqualTo(EVIDENCE_AT);
        assertThat(eventRows()).hasSize(3);
        assertThat(store.head(CONTEXT).orElseThrow().lastSequence()).isEqualTo(3);
        assertThat(slotRow().get("usedSlots").n()).isEqualTo("3");
        StoredEvent stored = store.find(resultSubmission(intent, declined()).dedupeKey())
                .orElseThrow();
        assertThat(stored.stateAfter()).isEqualTo(ResultCriticalComposer.DECLINED_STATE_AFTER);
        assertThat(eventRows().get(2).get("eventId").s()).isEqualTo("DECLINED-AUTH-001");
    }

    @Test void exactReplayOfTheResultDoesNotDuplicateEventSequenceOrSlot() {
        AuthorizationRecord intent = dispatchedIntent();
        EventSubmission submission = resultSubmission(intent, approved());
        ResultCriticalComposer composer = resultComposer(intent, approved(), CAPACITY);

        store.append(submission, composer);
        EventAppendResult replay = store.append(submission, composer);

        assertThat(replay.decision()).isEqualTo(EventDeduplication.Decision.EXACT_REPLAY);
        assertThat(replay.sourceSequence()).isEqualTo(3);
        assertThat(eventRows()).hasSize(3);
        assertThat(store.head(CONTEXT).orElseThrow().lastSequence()).isEqualTo(3);
        assertThat(slotRow().get("usedSlots").n()).isEqualTo("3");
        assertThat(rawJournalItem("AUTH#" + AUTHORIZATION_ID, "META").get("version").n())
                .isEqualTo("2");
        assertThat(repository.findById(AUTHORIZATION_ID).orElseThrow().state())
                .isEqualTo(AuthorizationState.APPROVED);
    }

    @Test void concurrentDeterminedOutcomeIsNotOverwrittenAndCommitsNothing() {
        AuthorizationRecord intent = dispatchedIntent();
        // A competing writer records a determined outcome after the intent was read.
        repository.updateOutcome(AUTHORIZATION_ID, AuthorizationState.DECLINED.name(),
                DecisionSource.ISSUER.name(), null, EVIDENCE_AT);

        assertThatThrownBy(() -> store.append(resultSubmission(intent, approved()),
                resultComposer(intent, approved(), CAPACITY)))
                .isInstanceOf(TransactionCanceledException.class);

        // The competing result is retained: no overwrite, no extra event and no extra slot.
        assertThat(eventRows()).hasSize(2);
        assertThat(store.head(CONTEXT).orElseThrow().lastSequence()).isEqualTo(2);
        assertThat(slotRow().get("usedSlots").n()).isEqualTo("2");
        AuthorizationRecord persisted = repository.findById(AUTHORIZATION_ID).orElseThrow();
        assertThat(persisted.state()).isEqualTo(AuthorizationState.DECLINED);
        assertThat(persisted.outcomeDeterminedAt()).isEqualTo(EVIDENCE_AT);
        assertThat(persisted.decisionSource()).isEqualTo(DecisionSource.ISSUER);

        // A composer built from the already-determined record is refused before any write.
        assertThatThrownBy(() -> resultComposer(persisted, approved(), CAPACITY))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("undispatched-outcome");
    }

    @Test void resultWithoutADurableDispatchIntentLeavesNoPartialWrites() {
        AuthorizationRecord claim = claim();
        store.append(claimSubmission(), ClaimCriticalComposer.withSlotAccounting(
                journalTable, eventTable, claim, CAPACITY));

        // The claim is not dispatched and has no committed-version evidence for a result.
        AuthorizationRecord notDispatched = repository.findById(AUTHORIZATION_ID).orElseThrow();
        assertThatThrownBy(() -> store.append(resultSubmission(notDispatched, approved()),
                resultComposer(notDispatched, approved(), CAPACITY)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("undispatched-outcome");

        assertThat(eventRows()).hasSize(1);
        assertThat(store.head(CONTEXT).orElseThrow().lastSequence()).isEqualTo(1);
        assertThat(slotRow().get("usedSlots").n()).isEqualTo("1");
        AuthorizationRecord persisted = repository.findById(AUTHORIZATION_ID).orElseThrow();
        assertThat(persisted.state()).isEqualTo(AuthorizationState.AWAITING);
        assertThat(persisted.outcomeDeterminedAt()).isNull();
        assertThat(persisted.dispatchedAt()).isNull();
    }

    @Test void submissionLabelledWithAnotherStateOrVersionIsRejectedBeforeAnyWrite() {
        AuthorizationRecord intent = dispatchedIntent();
        ResultCriticalComposer composer = resultComposer(intent, approved(), CAPACITY);
        EventSubmission correct = resultSubmission(intent, approved());

        EventSubmission wrongVersion = new EventSubmission(CONTEXT, correct.event(),
                EventDurability.CRITICAL_COMMITTED, 2L, "2026-11-18T12:00:02Z",
                "2026-11-18T12:00:02Z", "2026-11-18T12:00:02Z",
                ResultCriticalComposer.APPROVED_STATE_AFTER);
        assertThatThrownBy(() -> store.append(wrongVersion, composer))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("journal version");

        EventSubmission wrongState = new EventSubmission(CONTEXT, correct.event(),
                EventDurability.CRITICAL_COMMITTED, 3L, "2026-11-18T12:00:02Z",
                "2026-11-18T12:00:02Z", "2026-11-18T12:00:02Z",
                ResultCriticalComposer.DECLINED_STATE_AFTER);
        assertThatThrownBy(() -> store.append(wrongState, composer))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("stateAfter");

        assertThat(eventRows()).hasSize(2);
        assertThat(store.head(CONTEXT).orElseThrow().lastSequence()).isEqualTo(2);
        assertThat(slotRow().get("usedSlots").n()).isEqualTo("2");
        AuthorizationRecord persisted = repository.findById(AUTHORIZATION_ID).orElseThrow();
        assertThat(persisted.state()).isEqualTo(AuthorizationState.AWAITING);
        assertThat(persisted.outcomeDeterminedAt()).isNull();
    }

    @Test void uncorrelatedOrNonConfirmedEvidenceIsRejectedBeforeAnyWrite() {
        AuthorizationRecord intent = dispatchedIntent();

        assertThatThrownBy(() -> resultComposer(intent,
                new DownstreamReply("CORR-OTHER", IssuerDecision.APPROVED, EVIDENCE_AT), CAPACITY))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("correlation");
        assertThatThrownBy(() -> resultComposer(intent,
                new DownstreamReply(DISPATCH_CORRELATION, IssuerDecision.NO_RESPONSE, EVIDENCE_AT),
                CAPACITY))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("APPROVED or DECLINED");
        assertThatThrownBy(() -> resultComposer(intent,
                new DownstreamReply(DISPATCH_CORRELATION, IssuerDecision.MALFORMED, EVIDENCE_AT),
                CAPACITY))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("APPROVED or DECLINED");
        assertThatThrownBy(() -> resultComposer(intent,
                new DownstreamReply(DISPATCH_CORRELATION, IssuerDecision.NETWORK_REJECTED,
                        EVIDENCE_AT),
                CAPACITY))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("APPROVED or DECLINED");
        assertThatThrownBy(() -> resultComposer(intent,
                new DownstreamReply(DISPATCH_CORRELATION, IssuerDecision.APPROVED,
                        DISPATCHED_AT.minusSeconds(1)),
                CAPACITY))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("predate");

        assertThat(eventRows()).hasSize(2);
        assertThat(store.head(CONTEXT).orElseThrow().lastSequence()).isEqualTo(2);
        assertThat(slotRow().get("usedSlots").n()).isEqualTo("2");
        assertThat(repository.findById(AUTHORIZATION_ID).orElseThrow().outcomeDeterminedAt())
                .isNull();
    }

    private AuthorizationRecord dispatchedIntent() {
        AuthorizationRecord claim = claim();
        store.append(claimSubmission(), ClaimCriticalComposer.withSlotAccounting(
                journalTable, eventTable, claim, CAPACITY));
        AuthorizationRecord intent = claim.withDispatch(DISPATCH_CORRELATION, DISPATCHED_AT);
        store.append(PossibleSendCriticalComposer.submission(claim, DISPATCH_CORRELATION, "SIM",
                        "BOOT-P1B4A", DISPATCHED_AT),
                PossibleSendCriticalComposer.withSlotAccounting(journalTable, eventTable, claim,
                        DISPATCH_CORRELATION, DISPATCHED_AT, CAPACITY));
        assertThat(repository.readVersionEvidence(AUTHORIZATION_ID).agreesWith(1L, 2L)).isTrue();
        return intent;
    }

    private ResultCriticalComposer resultComposer(AuthorizationRecord intent,
            DownstreamReply evidence, CriticalCapacity capacity) {
        return ResultCriticalComposer.withSlotAccounting(journalTable, eventTable, intent, evidence,
                capacity);
    }

    private EventSubmission resultSubmission(AuthorizationRecord intent, DownstreamReply evidence) {
        return ResultCriticalComposer.submission(intent, evidence, "SIM", "BOOT-P1B4A");
    }

    private static DownstreamReply approved() {
        return new DownstreamReply(DISPATCH_CORRELATION, IssuerDecision.APPROVED, EVIDENCE_AT);
    }

    private static DownstreamReply declined() {
        return new DownstreamReply(DISPATCH_CORRELATION, IssuerDecision.DECLINED, EVIDENCE_AT);
    }

    private EventSubmission claimSubmission() {
        return new EventSubmission(CONTEXT, payloadEvent("CLAIM-AUTH-001", "BOOT-P1B4A"),
                EventDurability.CRITICAL_COMMITTED, ClaimCriticalComposer.CLAIM_JOURNAL_VERSION,
                "2026-11-18T12:00:00Z", "2026-11-18T12:00:00Z", "2026-11-18T12:00:00Z",
                ClaimCriticalComposer.CLAIM_STATE_AFTER);
    }

    private static com.acquiring.simulator.domain.event.CanonicalEvent payloadEvent(
            String eventId, String instanceId) {
        try {
            ObjectNode node = (ObjectNode) MAPPER.readTree(VECTORS.toFile())
                    .path("events").get(0).path("producerPayload");
            node.put("eventId", eventId);
            node.put("instanceId", instanceId);
            node.put("transactionId", "TX-001");
            node.put("operationId", "AUTH-001");
            node.put("taskName", "business-persist");
            node.put("status", "SUCCEEDED");
            return com.acquiring.simulator.domain.event.CanonicalEventJson.parse(node.toString(),
                    MANIFEST);
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
