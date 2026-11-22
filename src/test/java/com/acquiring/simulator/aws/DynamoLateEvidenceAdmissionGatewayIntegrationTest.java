package com.acquiring.simulator.aws;

import static org.assertj.core.api.Assertions.assertThat;

import com.acquiring.simulator.adapter.aws.CriticalWriteComposer;
import com.acquiring.simulator.adapter.aws.DynamoAuthorizationRepository;
import com.acquiring.simulator.adapter.aws.DynamoClaimGateway;
import com.acquiring.simulator.adapter.aws.DynamoDispatchIntentGateway;
import com.acquiring.simulator.adapter.aws.DynamoEventStore;
import com.acquiring.simulator.adapter.aws.LateEvidenceAdmissionGateway;
import com.acquiring.simulator.adapter.aws.LateEvidenceCriticalComposer;
import com.acquiring.simulator.adapter.aws.ResultCriticalComposer;
import com.acquiring.simulator.domain.event.CriticalCapacity;
import com.acquiring.simulator.domain.event.EventAppendResult;
import com.acquiring.simulator.domain.event.EventContext;
import com.acquiring.simulator.domain.event.EventDeduplication;
import com.acquiring.simulator.domain.event.EventDedupeKey;
import com.acquiring.simulator.domain.event.EventHead;
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
import com.acquiring.simulator.domain.port.ClaimGateway;
import com.acquiring.simulator.domain.port.DispatchIntentGateway;
import com.acquiring.simulator.domain.port.EventStore;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeDefinition;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.BillingMode;
import software.amazon.awssdk.services.dynamodb.model.CreateTableRequest;
import software.amazon.awssdk.services.dynamodb.model.DeleteItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.KeySchemaElement;
import software.amazon.awssdk.services.dynamodb.model.KeyType;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType;

/**
 * P1b6c late-evidence admission gateway against DynamoDB Local (observation contract O3/P5, R07,
 * OBS-08/OBS-10).
 *
 * <p>Bounded slice only: fresh-terminal-read replay, authorization-before-dedupe ordering,
 * preservation of a contradictory retained result, and unknown/ambiguous outcomes. The
 * {@link DownstreamReply} is injected through the gateway's explicit internal test seam, so a green
 * run is <strong>not</strong> evidence that a reply passed C4 sender/role/key/signature or P6
 * verification; no public endpoint, HTTP route or {@code AuthorizationService}/{@code AwsRuntime}
 * wiring is exercised. Capacity is an explicit test example; the O8 numbers remain PROPOSED.
 */
@Testcontainers
class DynamoLateEvidenceAdmissionGatewayIntegrationTest {

    @Container static final GenericContainer<?> DYNAMO = new GenericContainer<>(
            DockerImageName.parse("amazon/dynamodb-local:2.6.1")).withExposedPorts(8000);

    /** Test-only example capacity; owner approval of the O8 numbers is still pending. */
    private static final CriticalCapacity CAPACITY = new CriticalCapacity(5, 8);
    private static final EventManifest MANIFEST = OperationAManifest.manifest(CAPACITY);
    private static final EventContext CONTEXT =
            new EventContext("SIM", "ACQ-A", "MERCHANT-A", "TX-001");
    private static final String AUTHORIZATION_ID = "AUTH-001";
    private static final String DISPATCH_CORRELATION = "CORR-SEND-001";
    private static final String RECEIPT_ID = "LATE-RECEIPT-001";
    private static final Instant CLAIMED_AT = Instant.parse("2026-11-18T12:00:00Z");
    private static final Instant DISPATCHED_AT = Instant.parse("2026-11-18T12:00:01Z");
    /** Explicit caller-supplied deadline; this test introduces no default timeout. */
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

    @Test void newLateEvidenceCommitsThenAFreshReadReplaysWithoutASecondWrite() {
        dispatchedIntent();

        LateEvidenceAdmissionGateway.Decision first = gateway().admit(AUTHORIZATION_ID,
                approved(), DEADLINE, LATE_DELIVERED_AT, RECEIPT_ID);

        assertThat(first.status()).isEqualTo(LateEvidenceAdmissionGateway.Status.COMMITTED);
        assertThat(first.timing()).isEqualTo(LateEvidenceCriticalComposer.Timing.LATE);
        assertThat(first.confirmedState()).isEqualTo(AuthorizationState.APPROVED);
        assertThat(first.recorded()).isTrue();
        assertThat(first.evidence().agreesWith(2L, 3L)).isTrue();
        assertThat(eventRows()).hasSize(3);
        assertThat(store.head(CONTEXT).orElseThrow().lastSequence()).isEqualTo(3L);
        assertThat(slotRow().get("usedSlots").n()).isEqualTo("3");
        assertThat(repository.readLateEvidenceReceipt(AUTHORIZATION_ID).orElseThrow()
                .deliveryReceiptId()).isEqualTo(RECEIPT_ID);

        // A DIFFERENT gateway instance with no caller-held state: the fresh terminal read and the
        // strong receipt/result lookups are the only input.
        LateEvidenceAdmissionGateway.Decision replay = gateway().admit(AUTHORIZATION_ID,
                approved(), DEADLINE, LATE_DELIVERED_AT, "LATE-RECEIPT-REDELIVERED");

        assertThat(replay.status()).isEqualTo(LateEvidenceAdmissionGateway.Status.REPLAY);
        assertThat(replay.recorded()).isTrue();
        assertThat(replay.confirmedState()).isEqualTo(AuthorizationState.APPROVED);
        assertThat(replay.evidence().agreesWith(2L, 3L)).isTrue();
        assertThat(replay.detail()).isNotNull();

        // Replay allocated no sequence, wrote no second receipt and reserved no second slot.
        assertThat(eventRows()).hasSize(3);
        assertThat(store.head(CONTEXT).orElseThrow().lastSequence()).isEqualTo(3L);
        assertThat(slotRow().get("usedSlots").n()).isEqualTo("3");
        assertThat(repository.readLateEvidenceReceipt(AUTHORIZATION_ID).orElseThrow()
                .deliveryReceiptId()).isEqualTo(RECEIPT_ID);
    }

    @Test void unauthorizedCandidatesAreRefusedBeforeAnyDedupeLookup() {
        dispatchedIntent();
        assertThat(gateway().admit(AUTHORIZATION_ID, approved(), DEADLINE, LATE_DELIVERED_AT,
                RECEIPT_ID).status()).isEqualTo(LateEvidenceAdmissionGateway.Status.COMMITTED);

        RecordingStore recording = new RecordingStore(store);
        LateEvidenceAdmissionGateway guarded = new LateEvidenceAdmissionGateway(recording,
                (submission, composer) -> {
                    throw new AssertionError("a refused candidate must never be written");
                }, repository, journalTable, eventTable, CAPACITY, "SIM", "BOOT-P1B6C");

        LateEvidenceAdmissionGateway.Decision malformed = guarded.admit(AUTHORIZATION_ID,
                reply(IssuerDecision.MALFORMED, LATE_EVIDENCE_AT), DEADLINE, LATE_DELIVERED_AT,
                RECEIPT_ID);
        LateEvidenceAdmissionGateway.Decision uncorrelated = guarded.admit(AUTHORIZATION_ID,
                new DownstreamReply("CORR-FOREIGN", IssuerDecision.APPROVED, LATE_EVIDENCE_AT),
                DEADLINE, LATE_DELIVERED_AT, RECEIPT_ID);

        assertThat(malformed.status()).isEqualTo(LateEvidenceAdmissionGateway.Status.REFUSED);
        assertThat(malformed.timing()).isNull();
        assertThat(uncorrelated.status()).isEqualTo(LateEvidenceAdmissionGateway.Status.REFUSED);
        assertThat(uncorrelated.timing()).isNull();
        // Refusal happened before ANY dedupe lookup, even though a compatible result exists.
        assertThat(recording.findCalls).isZero();
        assertThat(recording.appendCalls).isZero();

        // Control: the same wiring DOES reach the dedupe lookup for an authorized candidate, so the
        // zero above is meaningful rather than a vacuous counter.
        LateEvidenceAdmissionGateway.Decision replay = guarded.admit(AUTHORIZATION_ID, approved(),
                DEADLINE, LATE_DELIVERED_AT, RECEIPT_ID);
        assertThat(replay.status()).isEqualTo(LateEvidenceAdmissionGateway.Status.REPLAY);
        assertThat(recording.findCalls).isEqualTo(1);

        // Retained outcome and storage are untouched by the refused candidates.
        assertThat(repository.findById(AUTHORIZATION_ID).orElseThrow().state())
                .isEqualTo(AuthorizationState.APPROVED);
        assertThat(repository.readLateEvidenceReceipt(AUTHORIZATION_ID).orElseThrow()
                .deliveryReceiptId()).isEqualTo(RECEIPT_ID);
        assertThat(eventRows()).hasSize(3);
        assertThat(slotRow().get("usedSlots").n()).isEqualTo("3");
    }

    @Test void contradictoryTerminalEvidencePreservesTheOriginalResult() {
        dispatchedIntent();
        assertThat(gateway().admit(AUTHORIZATION_ID, approved(), DEADLINE, LATE_DELIVERED_AT,
                RECEIPT_ID).status()).isEqualTo(LateEvidenceAdmissionGateway.Status.COMMITTED);

        LateEvidenceAdmissionGateway.Decision conflict = gateway().admit(AUTHORIZATION_ID,
                declined(), DEADLINE, LATE_DELIVERED_AT, "LATE-RECEIPT-CONFLICT");

        assertThat(conflict.status())
                .isEqualTo(LateEvidenceAdmissionGateway.Status.CONTENT_CONFLICT);
        assertThat(conflict.confirmedState()).isNull();
        assertThat(conflict.detail()).isNotNull();

        // The first terminal evidence is the retained one: no override, no extra event or slot.
        AuthorizationRecord persisted = repository.findById(AUTHORIZATION_ID).orElseThrow();
        assertThat(persisted.state()).isEqualTo(AuthorizationState.APPROVED);
        assertThat(persisted.decisionSource()).isEqualTo(DecisionSource.ISSUER);
        assertThat(persisted.outcomeDeterminedAt()).isEqualTo(LATE_EVIDENCE_AT);
        assertThat(repository.readVersionEvidence(AUTHORIZATION_ID).agreesWith(2L, 3L)).isTrue();
        assertThat(repository.readLateEvidenceReceipt(AUTHORIZATION_ID).orElseThrow()
                .deliveryReceiptId()).isEqualTo(RECEIPT_ID);
        assertThat(eventRows()).hasSize(3);
        assertThat(store.head(CONTEXT).orElseThrow().lastSequence()).isEqualTo(3L);
        assertThat(slotRow().get("usedSlots").n()).isEqualTo("3");
        assertThat(eventRows()).filteredOn(row -> "DECLINED-AUTH-001"
                .equals(row.get("eventId").s())).isEmpty();
    }

    @Test void confirmedRecordWithoutALateReceiptIsAConflictThatPreservesTheOutcome() {
        AuthorizationRecord intent = dispatchedIntent();

        // The outcome was confirmed through the NORMAL verified-result admission, so this record
        // carries no late-evidence receipt at all: a terminal outcome from a different evidence
        // origin. No data is deleted to create this precondition.
        EventAppendResult normal = store.append(
                ResultCriticalComposer.submission(intent, approved(), "SIM", "BOOT-P1B4A"),
                ResultCriticalComposer.withSlotAccounting(journalTable, eventTable, intent,
                        approved(), CAPACITY));
        assertThat(normal.decision()).isEqualTo(EventDeduplication.Decision.NEW);
        assertThat(repository.readLateEvidenceReceipt(AUTHORIZATION_ID)).isEmpty();

        LateEvidenceAdmissionGateway.Decision conflict = gateway().admit(AUTHORIZATION_ID,
                approved(), DEADLINE, LATE_DELIVERED_AT, RECEIPT_ID);

        assertThat(conflict.status())
                .isEqualTo(LateEvidenceAdmissionGateway.Status.CONTENT_CONFLICT);
        assertThat(conflict.timing()).isEqualTo(LateEvidenceCriticalComposer.Timing.LATE);
        assertThat(conflict.confirmedState()).isNull();
        assertThat(conflict.recorded()).isFalse();
        assertThat(conflict.detail()).isNotBlank();

        // The earlier confirmed outcome and its version evidence are preserved; no receipt is
        // invented and no second result, sequence or slot is written.
        AuthorizationRecord persisted = repository.findById(AUTHORIZATION_ID).orElseThrow();
        assertThat(persisted.state()).isEqualTo(AuthorizationState.APPROVED);
        assertThat(persisted.decisionSource()).isEqualTo(DecisionSource.ISSUER);
        assertThat(persisted.outcomeDeterminedAt()).isEqualTo(LATE_EVIDENCE_AT);
        assertThat(repository.readVersionEvidence(AUTHORIZATION_ID).agreesWith(2L, 3L)).isTrue();
        assertThat(repository.readLateEvidenceReceipt(AUTHORIZATION_ID)).isEmpty();
        assertThat(eventRows()).hasSize(3);
        assertThat(store.head(CONTEXT).orElseThrow().lastSequence()).isEqualTo(3L);
        assertThat(slotRow().get("usedSlots").n()).isEqualTo("3");
    }

    @Test void confirmedLateRecordWithMissingRetainedResultEventIsUnknownAndPreserved() {
        dispatchedIntent();
        assertThat(gateway().admit(AUTHORIZATION_ID, approved(), DEADLINE, LATE_DELIVERED_AT,
                RECEIPT_ID).status()).isEqualTo(LateEvidenceAdmissionGateway.Status.COMMITTED);

        // Isolated per-test seam: remove only the retained-result dedupe identity row, so the
        // gateway's strong retention lookup proves nothing is retained. The record, receipt,
        // version evidence, event row, HEAD and slot accounting stay untouched.
        EventDedupeKey resultKey = new EventDedupeKey("SIM",
                OperationAManifest.PRODUCER_PROCESSOR, "APPROVED-" + AUTHORIZATION_ID);
        db.deleteItem(DeleteItemRequest.builder().tableName(eventTable)
                .key(Map.of(
                        "pk", text(EventTableKeys.dedupePartition("SIM")),
                        "sk", text(EventTableKeys.dedupeSortKey(resultKey))))
                .build());
        assertThat(store.find(resultKey)).isEmpty();

        LateEvidenceAdmissionGateway.Decision unknown = gateway().admit(AUTHORIZATION_ID,
                approved(), DEADLINE, LATE_DELIVERED_AT, RECEIPT_ID);

        assertThat(unknown.status()).isEqualTo(LateEvidenceAdmissionGateway.Status.UNKNOWN);
        assertThat(unknown.timing()).isEqualTo(LateEvidenceCriticalComposer.Timing.LATE);
        assertThat(unknown.confirmedState()).isNull();
        assertThat(unknown.recorded()).isFalse();
        assertThat(unknown.detail()).contains("no retained result event");

        // Unknown is not "no result" and changes nothing: the confirmed outcome, receipt,
        // committed version, event row, HEAD and slot accounting are all preserved.
        AuthorizationRecord persisted = repository.findById(AUTHORIZATION_ID).orElseThrow();
        assertThat(persisted.state()).isEqualTo(AuthorizationState.APPROVED);
        assertThat(persisted.decisionSource()).isEqualTo(DecisionSource.ISSUER);
        assertThat(persisted.outcomeDeterminedAt()).isEqualTo(LATE_EVIDENCE_AT);
        assertThat(repository.readVersionEvidence(AUTHORIZATION_ID).agreesWith(2L, 3L)).isTrue();
        assertThat(repository.readLateEvidenceReceipt(AUTHORIZATION_ID).orElseThrow()
                .deliveryReceiptId()).isEqualTo(RECEIPT_ID);
        assertThat(eventRows()).hasSize(3);
        assertThat(store.head(CONTEXT).orElseThrow().lastSequence()).isEqualTo(3L);
        assertThat(slotRow().get("usedSlots").n()).isEqualTo("3");
    }

    @Test void ambiguousCommitRecoversByOneLookupAndAnUnreadableRecordStaysUnknown() {
        dispatchedIntent();

        // Commit succeeds in storage, then the response is lost. The committer throws AFTER a real
        // append, which is the only way to reach the ambiguous-commit branch without a second write.
        LateEvidenceAdmissionGateway.Committer lostResponse = (submission, composer) -> {
            store.append(submission, composer);
            throw SdkClientException.create("simulated lost response after commit");
        };
        LateEvidenceAdmissionGateway ambiguous = new LateEvidenceAdmissionGateway(store,
                lostResponse, repository, journalTable, eventTable, CAPACITY, "SIM", "BOOT-P1B6C");

        LateEvidenceAdmissionGateway.Decision recovered = ambiguous.admit(AUTHORIZATION_ID,
                approved(), DEADLINE, LATE_DELIVERED_AT, RECEIPT_ID);

        // Recovery is knowledge only: no resend, no retry, no second sequence or slot.
        assertThat(recovered.status()).isEqualTo(LateEvidenceAdmissionGateway.Status.RECOVERED);
        assertThat(recovered.recorded()).isTrue();
        assertThat(recovered.confirmedState()).isEqualTo(AuthorizationState.APPROVED);
        assertThat(recovered.evidence().agreesWith(2L, 3L)).isTrue();
        assertThat(eventRows()).hasSize(3);
        assertThat(store.head(CONTEXT).orElseThrow().lastSequence()).isEqualTo(3L);
        assertThat(slotRow().get("usedSlots").n()).isEqualTo("3");
        assertThat(repository.readVersionEvidence(AUTHORIZATION_ID).agreesWith(2L, 3L)).isTrue();

        // An unreadable journal is UNKNOWN, never "no result" and never a fabricated outcome.
        DynamoAuthorizationRepository unreadable = new DynamoAuthorizationRepository(db,
                "missing-" + UUID.randomUUID());
        LateEvidenceAdmissionGateway unreadableGateway = new LateEvidenceAdmissionGateway(store,
                store::append, unreadable, journalTable, eventTable, CAPACITY, "SIM",
                "BOOT-P1B6C");

        LateEvidenceAdmissionGateway.Decision unknown = unreadableGateway.admit(AUTHORIZATION_ID,
                approved(), DEADLINE, LATE_DELIVERED_AT, RECEIPT_ID);

        assertThat(unknown.status()).isEqualTo(LateEvidenceAdmissionGateway.Status.UNKNOWN);
        assertThat(unknown.timing()).isNull();
        assertThat(unknown.recorded()).isFalse();
        assertThat(unknown.detail()).isNotBlank();
        // Unknown changed nothing: the recovered result is still the one retained.
        assertThat(eventRows()).hasSize(3);
        assertThat(repository.findById(AUTHORIZATION_ID).orElseThrow().state())
                .isEqualTo(AuthorizationState.APPROVED);
    }

    @Test void evidenceAvailableByTheDeadlineIsNormalAdmissionAndWritesNothing() {
        dispatchedIntent();

        LateEvidenceAdmissionGateway.Decision normal = gateway().admit(AUTHORIZATION_ID,
                reply(IssuerDecision.APPROVED, DEADLINE), DEADLINE, LATE_DELIVERED_AT, RECEIPT_ID);

        assertThat(normal.status())
                .isEqualTo(LateEvidenceAdmissionGateway.Status.NORMAL_RESULT_ADMISSION);
        assertThat(normal.timing()).isEqualTo(
                LateEvidenceCriticalComposer.Timing.DELIVERY_DELAY);
        assertThat(normal.confirmedState()).isNull();
        assertThat(normal.recorded()).isFalse();

        // Nothing written and nothing declared lost by this gateway.
        assertThat(eventRows()).hasSize(2);
        assertThat(store.head(CONTEXT).orElseThrow().lastSequence()).isEqualTo(2L);
        assertThat(slotRow().get("usedSlots").n()).isEqualTo("2");
        assertThat(repository.findById(AUTHORIZATION_ID).orElseThrow().state())
                .isEqualTo(AuthorizationState.AWAITING);
        assertThat(repository.readLateEvidenceReceipt(AUTHORIZATION_ID)).isEmpty();
    }

    /** Production-shaped binding with the store as the one atomic append seam. */
    private LateEvidenceAdmissionGateway gateway() {
        return LateEvidenceAdmissionGateway.over(store, repository, journalTable, eventTable,
                CAPACITY, "SIM", "BOOT-P1B6C");
    }

    /** Claim, then commit the durable dispatch intent; returns the dispatched intent shape. */

    /** Claim, then commit the durable dispatch intent; returns the dispatched intent shape. */
    private AuthorizationRecord dispatchedIntent() {
        AuthorizationRecord claim = claim();
        ClaimGateway.Decision claimed = new DynamoClaimGateway(store, repository, journalTable,
                eventTable, CAPACITY, "SIM", "BOOT-P1B6C")
                .claim(claim, "CORR-CLAIM-001", CLAIMED_AT);
        assertThat(claimed.status()).isEqualTo(ClaimGateway.Status.CLAIMED);

        DispatchIntentGateway.Decision intent = new DynamoDispatchIntentGateway(store, repository,
                journalTable, eventTable, CAPACITY, "SIM", "BOOT-P1B6C")
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

    /** Records whether the gateway consulted dedupe before authorization refused a candidate. */
    private static final class RecordingStore implements EventStore {
        private final EventStore delegate;
        int findCalls;
        int appendCalls;

        RecordingStore(EventStore delegate) {
            this.delegate = delegate;
        }

        @Override public EventAppendResult append(EventSubmission submission) {
            appendCalls++;
            return delegate.append(submission);
        }

        @Override public Optional<StoredEvent> find(EventDedupeKey dedupeKey) {
            findCalls++;
            return delegate.find(dedupeKey);
        }

        @Override public Optional<EventHead> head(EventContext context) {
            return delegate.head(context);
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

    private AuthorizationRecord claim() {
        return new AuthorizationRecord(
                AUTHORIZATION_ID,
                "ACQ-A|MERCHANT-A|TX-001",
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
