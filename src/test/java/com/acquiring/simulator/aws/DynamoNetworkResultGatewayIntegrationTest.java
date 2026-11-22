package com.acquiring.simulator.aws;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.acquiring.simulator.adapter.aws.CriticalWriteComposer;
import com.acquiring.simulator.adapter.aws.DynamoAuthorizationRepository;
import com.acquiring.simulator.adapter.aws.DynamoClaimGateway;
import com.acquiring.simulator.adapter.aws.DynamoDispatchIntentGateway;
import com.acquiring.simulator.adapter.aws.DynamoEventStore;
import com.acquiring.simulator.adapter.aws.DynamoNetworkResultGateway;
import com.acquiring.simulator.adapter.aws.NetworkRejectedCriticalComposer;
import com.acquiring.simulator.domain.event.CriticalCapacity;
import com.acquiring.simulator.domain.event.EventContext;
import com.acquiring.simulator.domain.event.EventManifest;
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
import com.acquiring.simulator.domain.model.Outcome;
import com.acquiring.simulator.domain.model.RiskIndicator;
import com.acquiring.simulator.domain.model.Route;
import com.acquiring.simulator.domain.port.ClaimGateway;
import com.acquiring.simulator.domain.port.DispatchIntentGateway;
import com.acquiring.simulator.domain.port.NetworkResultGateway;
import java.lang.reflect.Method;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
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

/**
 * P1b5b network-result adapter against DynamoDB Local: one atomic explicit-network-rejection
 * transition through the narrow {@link NetworkResultGateway} port, with network provenance and the
 * committed version read back from persistence.
 *
 * <p>An explicit correlated {@code NETWORK_REJECTED} reply commits {@code NETWORK_REJECTED} with
 * {@link DecisionSource#NETWORK} and the existing caller mapping {@link Outcome#PROCESSOR_REJECTED};
 * no issuer task or issuer decision is fabricated. The adapter never resends, never reverses, never
 * calls the legacy outcome path and is not activated in {@code AwsRuntime}. These tests prove local
 * transaction and recovery mechanics only; service coupling and real AWS effects are unverified.
 */
@Testcontainers
class DynamoNetworkResultGatewayIntegrationTest {

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

    @Test void committedNetworkRejectionRequiresPersistedNetworkProvenance() {
        AuthorizationRecord intent = dispatchedIntent();

        NetworkResultGateway.Decision decision = gateway(store)
                .recordNetworkRejection(intent, networkRejected());

        assertThat(decision.status()).isEqualTo(NetworkResultGateway.Status.COMMITTED);
        assertThat(decision.recorded()).isTrue();
        assertThat(decision.confirmedState()).isEqualTo(AuthorizationState.NETWORK_REJECTED);
        assertThat(decision.evidence().agreesWith(2L, 3L)).isTrue();
        // OBS-03: the channel shows a processor rejection, never an issuer decline.
        assertThat(decision.callerOutcome()).isEqualTo(Outcome.PROCESSOR_REJECTED);

        AuthorizationRecord persisted = repository.findById(AUTHORIZATION_ID).orElseThrow();
        assertThat(persisted.state()).isEqualTo(AuthorizationState.NETWORK_REJECTED);
        assertThat(persisted.decisionSource()).isEqualTo(DecisionSource.NETWORK);
        assertThat(persisted.reasonCategory())
                .isEqualTo(NetworkRejectedCriticalComposer.NETWORK_REJECTION_REASON);
        assertThat(persisted.outcomeDeterminedAt()).isEqualTo(EVIDENCE_AT);
        assertThat(persisted.dispatchedAt()).isEqualTo(DISPATCHED_AT);
        assertThat(persisted.dispatchCorrelationId()).isEqualTo(DISPATCH_CORRELATION);

        assertThat(eventRows()).hasSize(3);
        assertThat(store.head(CONTEXT).orElseThrow().lastSequence()).isEqualTo(3);
        assertThat(slotRow().get("usedSlots").n()).isEqualTo("3");
        assertThat(slotRow().get("slotBudget").n()).isEqualTo("8");

        StoredEvent stored = store.find(NetworkRejectedCriticalComposer
                .submission(intent, networkRejected(), "SIM", "BOOT-P1B5B").dedupeKey())
                .orElseThrow();
        assertThat(stored.journalVersion()).isEqualTo(3L);
        assertThat(stored.stateAfter())
                .isEqualTo(NetworkRejectedCriticalComposer.NETWORK_REJECTED_STATE_AFTER);
        assertThat(eventRows().get(2).get("eventId").s())
                .isEqualTo("NETWORK_REJECTED-AUTH-001");
    }

    @Test void exactReplayAddsNoSequenceEventOrSlot() {
        AuthorizationRecord intent = dispatchedIntent();
        DynamoNetworkResultGateway gateway = gateway(store);

        NetworkResultGateway.Decision first =
                gateway.recordNetworkRejection(intent, networkRejected());
        NetworkResultGateway.Decision second =
                gateway.recordNetworkRejection(intent, networkRejected());

        assertThat(first.status()).isEqualTo(NetworkResultGateway.Status.COMMITTED);
        assertThat(second.status()).isEqualTo(NetworkResultGateway.Status.REPLAY);
        assertThat(second.recorded()).isTrue();
        assertThat(second.confirmedState()).isEqualTo(AuthorizationState.NETWORK_REJECTED);
        assertThat(second.evidence().agreesWith(2L, 3L)).isTrue();

        assertThat(eventRows()).hasSize(3);
        assertThat(store.head(CONTEXT).orElseThrow().lastSequence()).isEqualTo(3);
        assertThat(slotRow().get("usedSlots").n()).isEqualTo("3");
        assertThat(rawJournalItem("AUTH#" + AUTHORIZATION_ID, "META").get("version").n())
                .isEqualTo("2");
        assertThat(rawJournalItem("AUTH#" + AUTHORIZATION_ID, "META")
                .get("committedJournalVersion").n()).isEqualTo("3");
    }

    @Test void lostResponseIsRecoveredOnceWithNetworkProvenance() {
        AuthorizationRecord intent = dispatchedIntent();
        DynamoEventStore ambiguousStore = new DynamoEventStore(ambiguousAfterCommitClient(),
                eventTable, MANIFEST, new EventValidator(), Clock.fixed(EVIDENCE_AT,
                        ZoneOffset.UTC), CriticalWriteComposer.NONE);

        NetworkResultGateway.Decision decision = gateway(ambiguousStore)
                .recordNetworkRejection(intent, networkRejected());

        assertThat(decision.status()).isEqualTo(NetworkResultGateway.Status.RECOVERED);
        assertThat(decision.recorded()).isTrue();
        assertThat(decision.confirmedState()).isEqualTo(AuthorizationState.NETWORK_REJECTED);
        assertThat(decision.evidence().agreesWith(2L, 3L)).isTrue();
        assertThat(decision.callerOutcome()).isEqualTo(Outcome.PROCESSOR_REJECTED);

        // Exactly one committed rejection: one event, one HEAD advance and one slot.
        assertThat(eventRows()).hasSize(3);
        assertThat(store.head(CONTEXT).orElseThrow().lastSequence()).isEqualTo(3);
        assertThat(slotRow().get("usedSlots").n()).isEqualTo("3");
        assertThat(eventRows()).filteredOn(row -> "NETWORK_REJECTED-AUTH-001"
                .equals(row.get("eventId").s())).hasSize(1);
        AuthorizationRecord persisted = repository.findById(AUTHORIZATION_ID).orElseThrow();
        assertThat(persisted.state()).isEqualTo(AuthorizationState.NETWORK_REJECTED);
        assertThat(persisted.decisionSource()).isEqualTo(DecisionSource.NETWORK);
        assertThat(repository.readVersionEvidence(AUTHORIZATION_ID).agreesWith(2L, 3L)).isTrue();
    }

    @Test void ambiguousBeforeCommitStaysUnknownWithNoPartialWrite() {
        AuthorizationRecord intent = dispatchedIntent();
        DynamoEventStore ambiguousStore = new DynamoEventStore(ambiguousBeforeCommitClient(),
                eventTable, MANIFEST, new EventValidator(), Clock.fixed(EVIDENCE_AT,
                        ZoneOffset.UTC), CriticalWriteComposer.NONE);

        NetworkResultGateway.Decision decision = gateway(ambiguousStore)
                .recordNetworkRejection(intent, networkRejected());

        assertThat(decision.status()).isEqualTo(NetworkResultGateway.Status.UNKNOWN);
        assertThat(decision.recorded()).isFalse();
        assertThat(decision.confirmedState()).isNull();
        assertThat(decision.evidence()).isNull();
        assertThat(decision.callerOutcome()).isEqualTo(Outcome.NOT_CONFIRMED);

        // No partial rejection: the dispatch intent is untouched and no rejection row/slot exists.
        assertThat(eventRows()).hasSize(2);
        assertThat(store.head(CONTEXT).orElseThrow().lastSequence()).isEqualTo(2);
        assertThat(slotRow().get("usedSlots").n()).isEqualTo("2");
        AuthorizationRecord persisted = repository.findById(AUTHORIZATION_ID).orElseThrow();
        assertThat(persisted.state()).isEqualTo(AuthorizationState.AWAITING);
        assertThat(persisted.decisionSource()).isEqualTo(DecisionSource.NONE);
        assertThat(persisted.outcomeDeterminedAt()).isNull();
        assertThat(persisted.dispatchCorrelationId()).isEqualTo(DISPATCH_CORRELATION);
    }

    @Test void missingLookupStaysUnknownAndIsNeverNoRejection() {
        AuthorizationRecord intent = dispatchedIntent();
        DynamoEventStore unreadableStore = new DynamoEventStore(readFailureClient(), eventTable,
                MANIFEST, new EventValidator(), Clock.fixed(EVIDENCE_AT, ZoneOffset.UTC),
                CriticalWriteComposer.NONE);

        NetworkResultGateway.Decision decision = gateway(unreadableStore)
                .recordNetworkRejection(intent, networkRejected());

        assertThat(decision.status()).isEqualTo(NetworkResultGateway.Status.UNKNOWN);
        assertThat(decision.recorded()).isFalse();
        assertThat(decision.confirmedState()).isNull();
        assertThat(decision.evidence()).isNull();

        // The ambiguous write is unresolved, not proven absent: the attempt wrote nothing.
        assertThat(eventRows()).hasSize(2);
        assertThat(store.head(CONTEXT).orElseThrow().lastSequence()).isEqualTo(2);
        assertThat(slotRow().get("usedSlots").n()).isEqualTo("2");
        assertThat(repository.findById(AUTHORIZATION_ID).orElseThrow().outcomeDeterminedAt())
                .isNull();
    }

    @Test void issuerConfirmedOutcomeIsRetainedAndNeverOverwritten() {
        AuthorizationRecord intent = dispatchedIntent();
        // A competing writer records the issuer decision after the intent was read.
        repository.updateOutcome(AUTHORIZATION_ID, AuthorizationState.APPROVED.name(),
                DecisionSource.ISSUER.name(), null, EVIDENCE_AT);

        NetworkResultGateway.Decision decision = gateway(store)
                .recordNetworkRejection(intent, networkRejected());

        assertThat(decision.status()).isEqualTo(NetworkResultGateway.Status.NOT_COMMITTED);
        assertThat(decision.recorded()).isFalse();
        assertThat(decision.callerOutcome()).isEqualTo(Outcome.NOT_CONFIRMED);

        // The issuer decision is retained: no overwrite, no extra event, no extra slot.
        assertThat(eventRows()).hasSize(2);
        assertThat(store.head(CONTEXT).orElseThrow().lastSequence()).isEqualTo(2);
        assertThat(slotRow().get("usedSlots").n()).isEqualTo("2");
        AuthorizationRecord persisted = repository.findById(AUTHORIZATION_ID).orElseThrow();
        assertThat(persisted.state()).isEqualTo(AuthorizationState.APPROVED);
        assertThat(persisted.decisionSource()).isEqualTo(DecisionSource.ISSUER);
        assertThat(persisted.outcomeDeterminedAt()).isEqualTo(EVIDENCE_AT);
        assertThat(persisted.dispatchedAt()).isEqualTo(DISPATCHED_AT);
    }

    @Test void issuerDecisionEvidenceIsRefusedBeforeAnyWrite() {
        AuthorizationRecord intent = dispatchedIntent();

        assertThatIllegalArgumentException().isThrownBy(() -> gateway(store)
                .recordNetworkRejection(intent, new DownstreamReply(DISPATCH_CORRELATION,
                        IssuerDecision.APPROVED, EVIDENCE_AT)));

        assertThat(eventRows()).hasSize(2);
        assertThat(store.head(CONTEXT).orElseThrow().lastSequence()).isEqualTo(2);
        assertThat(slotRow().get("usedSlots").n()).isEqualTo("2");
        AuthorizationRecord persisted = repository.findById(AUTHORIZATION_ID).orElseThrow();
        assertThat(persisted.state()).isEqualTo(AuthorizationState.AWAITING);
        assertThat(persisted.outcomeDeterminedAt()).isNull();
    }

    private DynamoNetworkResultGateway gateway(DynamoEventStore eventStore) {
        return new DynamoNetworkResultGateway(eventStore, repository, journalTable, eventTable,
                CAPACITY, "SIM", "BOOT-P1B5B");
    }

    /** Claim, then commit the durable dispatch intent; returns the in-memory dispatched intent. */
    private AuthorizationRecord dispatchedIntent() {
        AuthorizationRecord claim = claim();
        ClaimGateway.Decision claimed = new DynamoClaimGateway(store, repository, journalTable,
                eventTable, CAPACITY, "SIM", "BOOT-P1B5B")
                .claim(claim, "CORR-CLAIM-001", CLAIMED_AT);
        assertThat(claimed.status()).isEqualTo(ClaimGateway.Status.CLAIMED);

        DispatchIntentGateway.Decision intent = new DynamoDispatchIntentGateway(store, repository,
                journalTable, eventTable, CAPACITY, "SIM", "BOOT-P1B5B")
                .dispatchIntent(claim, DISPATCH_CORRELATION, DISPATCHED_AT);
        assertThat(intent.status()).isEqualTo(DispatchIntentGateway.Status.COMMITTED);
        assertThat(intent.evidence().agreesWith(1L, 2L)).isTrue();
        return claim.withDispatch(DISPATCH_CORRELATION, DISPATCHED_AT);
    }

    private static DownstreamReply networkRejected() {
        return new DownstreamReply(DISPATCH_CORRELATION, IssuerDecision.NETWORK_REJECTED,
                EVIDENCE_AT);
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

    /** Commits the transaction and then reports an ambiguous transport outcome. */
    private static DynamoDbClient ambiguousAfterCommitClient() {
        AtomicInteger calls = new AtomicInteger();
        return proxy((method, args) -> {
            if ("transactWriteItems".equals(method.getName())
                    && calls.getAndIncrement() == 0) {
                invoke(db, method, args);
                throw SdkClientException.create("simulated lost response after commit");
            }
            return null;
        });
    }

    /** Reports an ambiguous outcome without committing anything. */
    private static DynamoDbClient ambiguousBeforeCommitClient() {
        AtomicInteger calls = new AtomicInteger();
        return proxy((method, args) -> {
            if ("transactWriteItems".equals(method.getName())
                    && calls.getAndIncrement() == 0) {
                throw SdkClientException.create("simulated ambiguous outcome");
            }
            return null;
        });
    }

    /** Ambiguous before commit, then fails the strongly consistent dedupe lookup. */
    private static DynamoDbClient readFailureClient() {
        AtomicBoolean failReads = new AtomicBoolean();
        return proxy((method, args) -> {
            if ("transactWriteItems".equals(method.getName())) {
                failReads.set(true);
                throw SdkClientException.create("simulated ambiguous outcome");
            }
            if (failReads.get() && "getItem".equals(method.getName())) {
                throw SdkClientException.create("simulated read failure");
            }
            return null;
        });
    }

    @FunctionalInterface private interface Fault {
        /** Return {@code null} to delegate to the real client; throw to simulate a fault. */
        Object apply(Method method, Object[] args) throws Throwable;
    }

    /** Delegating client whose named operations can be intercepted for one simulated fault. */
    private static DynamoDbClient proxy(Fault fault) {
        return (DynamoDbClient) java.lang.reflect.Proxy.newProxyInstance(
                DynamoDbClient.class.getClassLoader(),
                new Class<?>[] {DynamoDbClient.class},
                (proxy, method, args) -> {
                    Object intercepted = fault.apply(method, args);
                    return intercepted != null ? intercepted : invoke(db, method, args);
                });
    }

    private static Object invoke(Object target, Method method, Object[] args) throws Throwable {
        try {
            return method.invoke(target, args);
        } catch (java.lang.reflect.InvocationTargetException e) {
            throw e.getCause();
        }
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
