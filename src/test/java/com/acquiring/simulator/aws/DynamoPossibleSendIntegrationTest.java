package com.acquiring.simulator.aws;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.acquiring.simulator.adapter.aws.ClaimCriticalComposer;
import com.acquiring.simulator.adapter.aws.CriticalWriteComposer;
import com.acquiring.simulator.adapter.aws.DynamoAuthorizationRepository;
import com.acquiring.simulator.adapter.aws.DynamoEventStore;
import com.acquiring.simulator.adapter.aws.PossibleSendCriticalComposer;
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
import com.acquiring.simulator.domain.model.EntryMode;
import com.acquiring.simulator.domain.model.RiskIndicator;
import com.acquiring.simulator.domain.model.Route;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
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
import software.amazon.awssdk.core.exception.SdkClientException;
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
 * P1b3a dispatch-intent (POSSIBLE_SEND) composer against DynamoDB Local: one conditioned business
 * transition, an explicitly advanced committed journal version, the immutable critical event and
 * EVENT head/dedupe metadata, and the explicit slot reservation commit or abort together.
 *
 * <p>No payment is sent, no result is coupled and the composer is not production-wired. The tests
 * prove local transaction mechanics only; service behaviour and real AWS effects are unverified.
 */
@Testcontainers
class DynamoPossibleSendIntegrationTest {

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
    private static final Instant NOW = Instant.parse("2026-11-18T12:00:01Z");
    private static final Instant DISPATCHED_AT = NOW.plusSeconds(1);

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

    @Test void dispatchIntentCommitsBusinessTransitionEventHeadDedupeAndSlotInOneTransaction() {
        AuthorizationRecord claim = committedClaim();

        EventAppendResult result = store.append(sendSubmission(claim), sendComposer(claim, CAPACITY));

        assertThat(result.decision()).isEqualTo(EventDeduplication.Decision.NEW);
        assertThat(result.sourceSequence()).isEqualTo(2);

        // The business record advances to the dispatch intent without changing the payment meaning.
        AuthorizationRecord persisted = repository.findById(AUTHORIZATION_ID).orElseThrow();
        assertThat(persisted.state()).isEqualTo(AuthorizationState.AWAITING);
        assertThat(persisted.decisionSource()).isEqualTo(DecisionSource.NONE);
        assertThat(persisted.dispatchedAt()).isEqualTo(DISPATCHED_AT);
        assertThat(persisted.dispatchCorrelationId()).isEqualTo(DISPATCH_CORRELATION);
        assertThat(persisted.outcomeDeterminedAt()).isNull();
        assertThat(persisted.paymentFingerprint()).isEqualTo(claim.paymentFingerprint());
        assertThat(persisted.amountMinor()).isEqualTo(claim.amountMinor());

        // One claim event plus one dispatch-intent event; HEAD advanced exactly once.
        assertThat(eventRows()).hasSize(2);
        assertThat(store.head(CONTEXT).orElseThrow().lastSequence()).isEqualTo(2);
        assertThat(slotRow().get("usedSlots").n()).isEqualTo("2");
        assertThat(slotRow().get("slotBudget").n()).isEqualTo("8");

        StoredEvent stored = store.find(sendSubmission(claim).dedupeKey()).orElseThrow();
        assertThat(stored.sourceSequence()).isEqualTo(2);
        assertThat(stored.journalVersion()).isEqualTo(2L);
        assertThat(stored.stateAfter())
                .isEqualTo(PossibleSendCriticalComposer.POSSIBLE_SEND_STATE_AFTER);

        // Two persisted facts: CAS advanced to 1, committed version advanced explicitly to 2.
        Map<String, AttributeValue> record =
                rawJournalItem("AUTH#" + AUTHORIZATION_ID, "META");
        assertThat(record.get("version").n()).isEqualTo("1");
        assertThat(record.get("committedJournalVersion").n()).isEqualTo("2");
        CommittedVersionEvidence evidence = repository.readVersionEvidence(AUTHORIZATION_ID);
        assertThat(evidence.storageVersion()).isEqualTo(1);
        assertThat(evidence.committedJournalVersion()).isEqualTo(2L);
        assertThat(evidence.agreesWith(1L, 2L)).isTrue();
        assertThat(evidence.agreesWith(0L, 1L)).isFalse();
        assertThat(eventRows().get(1).get("journalVersion").n()).isEqualTo("2");
        assertThat(eventRows().get(1).get("stateAfter").s()).isEqualTo("POSSIBLE_SEND");
    }

    @Test void replayedDispatchIntentDoesNotDuplicateSequenceSlotOrBusinessTransition() {
        AuthorizationRecord claim = committedClaim();
        EventSubmission submission = sendSubmission(claim);
        PossibleSendCriticalComposer composer = sendComposer(claim, CAPACITY);

        store.append(submission, composer);
        EventAppendResult replay = store.append(submission, composer);

        assertThat(replay.decision()).isEqualTo(EventDeduplication.Decision.EXACT_REPLAY);
        assertThat(replay.sourceSequence()).isEqualTo(2);
        assertThat(eventRows()).hasSize(2);
        assertThat(store.head(CONTEXT).orElseThrow().lastSequence()).isEqualTo(2);
        assertThat(slotRow().get("usedSlots").n()).isEqualTo("2");
        assertThat(repository.findById(AUTHORIZATION_ID).orElseThrow().dispatchedAt())
                .isEqualTo(DISPATCHED_AT);
    }

    @Test void ambiguousTransportAfterCommitIsResolvedAsReplayNotASecondSendIntent() {
        AuthorizationRecord claim = committedClaim();
        EventSubmission submission = sendSubmission(claim);
        PossibleSendCriticalComposer composer = sendComposer(claim, CAPACITY);
        DynamoEventStore ambiguousStore = new DynamoEventStore(ambiguousClient(true), eventTable,
                MANIFEST, new EventValidator(), Clock.fixed(NOW, ZoneOffset.UTC),
                CriticalWriteComposer.NONE);

        assertThatThrownBy(() -> ambiguousStore.append(submission, composer))
                .isInstanceOf(SdkClientException.class);

        // The outcome is ambiguous, not "not sent": a strong lookup sees the committed intent.
        StoredEvent recovered = store.find(submission.dedupeKey()).orElseThrow();
        assertThat(recovered.sourceSequence()).isEqualTo(2);
        assertThat(recovered.journalVersion()).isEqualTo(2L);
        assertThat(recovered.stateAfter()).isEqualTo("POSSIBLE_SEND");
        assertThat(repository.findById(AUTHORIZATION_ID).orElseThrow().dispatchedAt())
                .isEqualTo(DISPATCHED_AT);

        // Retrying the same stable transition is a replay, never another slot, sequence or intent.
        EventAppendResult replay = store.append(submission, composer);
        assertThat(replay.decision()).isEqualTo(EventDeduplication.Decision.EXACT_REPLAY);
        assertThat(replay.sourceSequence()).isEqualTo(2);
        assertThat(eventRows()).hasSize(2);
        assertThat(slotRow().get("usedSlots").n()).isEqualTo("2");
    }

    @Test void cancelledDispatchIntentLeavesNoPartialBusinessTransitionEventOrSlot() {
        AuthorizationRecord claim = claim();

        assertThatThrownBy(() -> store.append(sendSubmission(claim),
                sendComposer(claim, CAPACITY)))
                .isInstanceOf(TransactionCanceledException.class);

        assertThat(eventRows()).isEmpty();
        assertThat(store.head(CONTEXT)).isEmpty();
        assertThat(slotRow()).isEmpty();
        assertThat(repository.findById(AUTHORIZATION_ID)).isEmpty();
        assertThat(repository.findLatestByBusinessKey(BUSINESS_KEY)).isEmpty();
    }

    @Test void legacyClaimWithoutCommittedVersionCannotAdvanceToPossibleSend() {
        AuthorizationRecord claim = claim();
        // The legacy path persists CAS 0 but records no committed journal version.
        repository.claim(claim);

        assertThatThrownBy(() -> store.append(sendSubmission(claim),
                sendComposer(claim, CAPACITY)))
                .isInstanceOf(TransactionCanceledException.class);

        assertThat(eventRows()).isEmpty();
        assertThat(slotRow()).isEmpty();
        Map<String, AttributeValue> record =
                rawJournalItem("AUTH#" + AUTHORIZATION_ID, "META");
        assertThat(record.get("version").n()).isEqualTo("0");
        assertThat(record).doesNotContainKey("committedJournalVersion");
        assertThat(repository.findById(AUTHORIZATION_ID).orElseThrow().dispatchedAt()).isNull();
    }

    @Test void concurrentConfirmedOutcomeIsNotOverwrittenByAStaleDispatchIntent() {
        AuthorizationRecord claim = committedClaim();
        repository.updateOutcome(AUTHORIZATION_ID, "DECLINED", "ISSUER", "TEST", NOW);

        assertThatThrownBy(() -> store.append(sendSubmission(claim),
                sendComposer(claim, CAPACITY)))
                .isInstanceOf(TransactionCanceledException.class);

        assertThat(eventRows()).hasSize(1);
        assertThat(store.head(CONTEXT).orElseThrow().lastSequence()).isEqualTo(1);
        assertThat(slotRow().get("usedSlots").n()).isEqualTo("1");
        AuthorizationRecord persisted = repository.findById(AUTHORIZATION_ID).orElseThrow();
        assertThat(persisted.state()).isEqualTo(AuthorizationState.DECLINED);
        assertThat(persisted.dispatchedAt()).isNull();
    }

    @Test void submissionLabelledWithAnotherStateOrVersionIsRejectedBeforeAnyWrite() {
        AuthorizationRecord claim = committedClaim();
        PossibleSendCriticalComposer composer = sendComposer(claim, CAPACITY);

        EventSubmission wrongVersion = new EventSubmission(CONTEXT,
                sendSubmission(claim).event(), EventDurability.CRITICAL_COMMITTED, 3L,
                "2026-11-18T12:00:02Z", "2026-11-18T12:00:02Z", "2026-11-18T12:00:02Z",
                "POSSIBLE_SEND");
        assertThatThrownBy(() -> store.append(wrongVersion, composer))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("journal version");

        EventSubmission wrongState = new EventSubmission(CONTEXT,
                sendSubmission(claim).event(), EventDurability.CRITICAL_COMMITTED, 2L,
                "2026-11-18T12:00:02Z", "2026-11-18T12:00:02Z", "2026-11-18T12:00:02Z",
                "APPROVED");
        assertThatThrownBy(() -> store.append(wrongState, composer))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("stateAfter");

        assertThat(eventRows()).hasSize(1);
        assertThat(store.head(CONTEXT).orElseThrow().lastSequence()).isEqualTo(1);
        assertThat(slotRow().get("usedSlots").n()).isEqualTo("1");
        assertThat(repository.findById(AUTHORIZATION_ID).orElseThrow().dispatchedAt()).isNull();
    }

    private AuthorizationRecord committedClaim() {
        AuthorizationRecord claim = claim();
        store.append(claimSubmission(), ClaimCriticalComposer.withSlotAccounting(
                journalTable, eventTable, claim, CAPACITY));
        assertThat(repository.readVersionEvidence(AUTHORIZATION_ID)
                .agreesWith(0L, 1L)).isTrue();
        return claim;
    }

    private PossibleSendCriticalComposer sendComposer(AuthorizationRecord claim,
            CriticalCapacity capacity) {
        return PossibleSendCriticalComposer.withSlotAccounting(journalTable, eventTable, claim,
                DISPATCH_CORRELATION, DISPATCHED_AT, capacity);
    }

    private EventSubmission sendSubmission(AuthorizationRecord claim) {
        return PossibleSendCriticalComposer.submission(claim, DISPATCH_CORRELATION, "SIM",
                "BOOT-P1B3A", DISPATCHED_AT);
    }

    private EventSubmission claimSubmission() {
        return new EventSubmission(CONTEXT, payloadEvent("CLAIM-AUTH-001", "BOOT-P1B3A"),
                EventDurability.CRITICAL_COMMITTED, ClaimCriticalComposer.CLAIM_JOURNAL_VERSION,
                "2026-11-18T12:00:01Z", "2026-11-18T12:00:01Z", "2026-11-18T12:00:01Z",
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
                NOW,
                null,
                null,
                NOW.plusSeconds(300));
    }

    private static DynamoDbClient ambiguousClient(boolean commitBeforeFailing) {
        AtomicInteger calls = new AtomicInteger();
        return (DynamoDbClient) Proxy.newProxyInstance(
                DynamoDbClient.class.getClassLoader(),
                new Class<?>[] {DynamoDbClient.class},
                (proxy, method, args) -> {
                    if ("transactWriteItems".equals(method.getName())
                            && calls.getAndIncrement() == 0) {
                        if (commitBeforeFailing) {
                            invoke(db, method, args);
                        }
                        throw SdkClientException.create("simulated ambiguous outcome");
                    }
                    return invoke(db, method, args);
                });
    }

    private static Object invoke(Object target, Method method, Object[] args) throws Throwable {
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
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
