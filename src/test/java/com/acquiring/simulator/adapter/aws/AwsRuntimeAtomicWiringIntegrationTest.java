package com.acquiring.simulator.adapter.aws;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.acquiring.simulator.StubIssuerSimulator;
import com.acquiring.simulator.MutableClock;
import com.acquiring.simulator.channel.SyntheticInstructionMaterializer;
import com.acquiring.simulator.config.SimulatorProperties;
import com.acquiring.simulator.domain.AuthorizationService;
import com.acquiring.simulator.domain.event.CriticalCapacity;
import com.acquiring.simulator.domain.event.EventContext;
import com.acquiring.simulator.domain.event.EventTableKeys;
import com.acquiring.simulator.domain.model.AuthorizationRecord;
import com.acquiring.simulator.domain.model.AuthorizationResult;
import com.acquiring.simulator.domain.model.AuthorizationState;
import com.acquiring.simulator.domain.model.AuthorizedContext;
import com.acquiring.simulator.domain.model.CredentialDecision;
import com.acquiring.simulator.domain.model.CvmIndicator;
import com.acquiring.simulator.domain.model.DecisionSource;
import com.acquiring.simulator.domain.model.DownstreamReply;
import com.acquiring.simulator.domain.model.EntryMode;
import com.acquiring.simulator.domain.model.IssuerDecision;
import com.acquiring.simulator.domain.model.LateEvidenceAdmission;
import com.acquiring.simulator.domain.model.LateEvidenceOutcome;
import com.acquiring.simulator.domain.model.Outcome;
import com.acquiring.simulator.domain.model.PurchaseRequest;
import com.acquiring.simulator.domain.model.Route;
import com.acquiring.simulator.domain.port.DispatchIntentGateway;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
 * T3 atomic runtime wiring: the composition used by {@code AwsRuntime.processor()} is executed
 * against DynamoDB Local with a stub issuer.
 *
 * <p>These tests assert the wired graph, not only a unit seam: the service holds every atomic
 * gateway (so the legacy branch cannot be selected), one claim event is composed with its business
 * row/HEAD/dedupe/slot, only a freshly committed intent sends, a recovered intent never resends, a
 * failed intent writes no event, and late evidence admits through the wired late gateway without a
 * second send. Capacity {@code (5,8)} and {@code (1,1)} are explicit test examples; the O8 numbers
 * remain unapproved and are never defaults. No AWS call, deployment or credential is involved.</p>
 */
@Testcontainers
class AwsRuntimeAtomicWiringIntegrationTest {

    @Container static final GenericContainer<?> DYNAMO = new GenericContainer<>(
            DockerImageName.parse("amazon/dynamodb-local:2.6.1")).withExposedPorts(8000);

    /** Explicit test-only capacities; owner approval of the O8 numbers is still pending. */
    private static final CriticalCapacity CAPACITY = new CriticalCapacity(5, 8);
    private static final CriticalCapacity SINGLE_SLOT = new CriticalCapacity(1, 1);

    private static final String ENVIRONMENT = "SIM";
    private static final String INSTANCE = "BOOT-T3-ATOMIC";
    private static final Instant NOW = Instant.parse("2026-11-22T12:00:01Z");

    private static final AuthorizedContext CALLER = new AuthorizedContext(
            "ACQ-A", "MERCH-A", "TEST", Set.of(Route.DOMESTIC));
    private static final SimulatorProperties PROPERTIES = new SimulatorProperties(
            new SimulatorProperties.Authorization(Duration.ofHours(24), Duration.ofSeconds(2)),
            new SimulatorProperties.Profile(
                    List.of(new SimulatorProperties.AcquirerFixture("ACQ-A",
                            List.of(Route.DOMESTIC))),
                    List.of(new SimulatorProperties.MerchantFixture("MERCH-A", "ACQ-A")),
                    List.of(new SimulatorProperties.TerminalFixture("TERM-A", "MERCH-A")),
                    List.of(new SimulatorProperties.CredentialRange("CRED-",
                            CredentialDecision.APPROVE, Route.DOMESTIC))));

    static DynamoDbClient db;
    String journalTable;
    String eventTable;
    DynamoAuthorizationRepository repository;
    StubIssuerSimulator issuer;
    MutableClock clock;
    /** Bound by {@link #request(String)}: the EVENT partition follows the transaction under test. */
    EventContext context;

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
        journalTable = "journal-" + UUID.randomUUID();
        eventTable = "events-" + UUID.randomUUID();
        createTable(journalTable);
        createTable(eventTable);
        repository = new DynamoAuthorizationRepository(db, journalTable);
        issuer = new StubIssuerSimulator();
        clock = new MutableClock(NOW);
    }

    @AfterEach void dropTables() {
        db.deleteTable(b -> b.tableName(eventTable));
        db.deleteTable(b -> b.tableName(journalTable));
    }

    @Test
    void wiredRuntimeInstallsEveryAtomicGatewayAndServesApprovedThroughIt() {
        AuthorizationService service = service(CAPACITY, db);

        assertAtomicGraph(service);

        AuthorizationResult result = service.authorize(CALLER, request("PURCHASE-T3-1"));

        assertThat(result.outcome()).isEqualTo(Outcome.APPROVED);
        assertThat(result.reference()).startsWith("AUTH-");
        assertThat(issuer.dispatchCount()).isEqualTo(1);

        // One claim event, one dispatch intent, one result: no legacy journal evidence row.
        String id = result.reference();
        assertThat(eventIds()).containsExactly("CLAIM-" + id, "POSSIBLE_SEND-" + id,
                "APPROVED-" + id);
        assertThat(storeHead()).isEqualTo(3L);
        assertThat(slotRow().get("usedSlots").n()).isEqualTo("3");
        assertThat(repository.eventsFor(id)).isEmpty();

        AuthorizationRecord persisted = repository.findById(id).orElseThrow();
        assertThat(persisted.state()).isEqualTo(AuthorizationState.APPROVED);
        assertThat(persisted.decisionSource()).isEqualTo(DecisionSource.ISSUER);
        assertThat(persisted.dispatchedAt()).isEqualTo(NOW);
        assertThat(repository.readVersionEvidence(id).committedJournalVersion()).isEqualTo(3L);

        // The dispatch intent persisted the exact original 0100 octets in the same transaction.
        assertThat(new DynamoOriginalInstructionReader(db, journalTable)
                .readPersistedOriginal0100(id)).isPresent();
    }

    @Test
    void identicalUnlinkedRetryReplaysWithoutASecondClaimOrSend() {
        AuthorizationService service = service(CAPACITY, db);
        AuthorizationResult first = service.authorize(CALLER, request("PURCHASE-T3-RETRY"));
        assertThat(first.outcome()).isEqualTo(Outcome.APPROVED);
        assertThat(issuer.dispatchCount()).isEqualTo(1);
        assertThat(eventIds()).hasSize(3);

        AuthorizationResult retry = service.authorize(CALLER, request("PURCHASE-T3-RETRY"));

        assertThat(retry.outcome()).isEqualTo(Outcome.APPROVED);
        assertThat(retry.reference()).isEqualTo(first.reference());
        assertThat(issuer.dispatchCount()).isEqualTo(1);
        assertThat(eventIds()).hasSize(3);
        assertThat(storeHead()).isEqualTo(3L);
    }

    @Test
    void recoveredDispatchIntentIsKnowledgeOnlyAndNeverResends() {
        // The second transactWriteItems is the dispatch intent: commit it, then report the
        // transport as ambiguous. The wired gateway must recover the intent, not send again.
        AuthorizationService service = service(CAPACITY, failingOnTransaction(2));

        AuthorizationResult result = service.authorize(CALLER, request("PURCHASE-T3-RECOVERED"));

        assertThat(result.outcome()).isEqualTo(Outcome.NOT_CONFIRMED);
        assertThat(issuer.dispatchCount()).isZero();

        AuthorizationRecord persisted = repository.findById(result.reference()).orElseThrow();
        assertThat(persisted.state()).isEqualTo(AuthorizationState.AWAITING);
        assertThat(persisted.dispatchedAt()).isEqualTo(NOW);
        assertThat(persisted.dispatchCorrelationId()).isNotBlank();
        // Claim and intent exist; no result event and no second send were invented.
        assertThat(eventIds()).hasSize(2);
        assertThat(storeHead()).isEqualTo(2L);
    }

    @Test
    void failedDispatchIntentWritesNoEventAndSendsNothing() {
        // The explicit single-slot budget is consumed by the claim, so the intent transaction is
        // rejected by the real composed write before any downstream call.
        AuthorizationService service = service(SINGLE_SLOT, db);

        AuthorizationResult result = service.authorize(CALLER, request("PURCHASE-T3-NOINTENT"));

        assertThat(result.outcome()).isEqualTo(Outcome.NOT_CONFIRMED);
        assertThat(issuer.dispatchCount()).isZero();

        AuthorizationRecord persisted = repository.findById(result.reference()).orElseThrow();
        assertThat(persisted.state()).isEqualTo(AuthorizationState.AWAITING);
        assertThat(persisted.dispatchedAt()).isNull();
        assertThat(persisted.dispatchCorrelationId()).isNull();
        // Only the atomic claim remains; no orphan event, HEAD or slot exists.
        assertThat(eventIds()).containsExactly("CLAIM-" + result.reference());
        assertThat(storeHead()).isEqualTo(1L);
        assertThat(slotRow().get("usedSlots").n()).isEqualTo("1");
    }

    @Test
    void failedAtomicClaimIsNeitherRetriedNorFallenBackToTheLegacyClaim() {
        // A definitively rejected claim transaction must not be retried and must not fall back to
        // repository.claim(), which would write a legacy journal row and dispatch. Exactly one
        // transactWriteItems attempt is allowed; nothing else may be written or sent.
        AtomicInteger transactions = new AtomicInteger();
        AuthorizationService service = service(CAPACITY, rejectingFirstTransaction(transactions));

        AuthorizationResult result = service.authorize(CALLER, request("PURCHASE-T3-NOCLAIM"));

        assertThat(result.outcome()).isEqualTo(Outcome.NOT_CONFIRMED);
        assertThat(result.reference()).isNull();
        assertThat(transactions.get()).isEqualTo(1);
        assertThat(issuer.dispatchCount()).isZero();
        assertThat(eventIds()).isEmpty();
        assertThat(storeHead()).isZero();
        assertThat(slotRow()).isEmpty();
        assertThat(repository.findLatestByBusinessKey(
                "ACQ-A|MERCH-A|PURCHASE-T3-NOCLAIM")).isEmpty();
    }

    @Test
    void wiredRuntimeRecordsIssuerDeclineFromPersistedState() {
        issuer.behaveAs(request -> new DownstreamReply(request.correlationId(),
                IssuerDecision.DECLINED, request.dispatchedAt().plusMillis(100)));
        AuthorizationService service = service(CAPACITY, db);

        AuthorizationResult result = service.authorize(CALLER, request("PURCHASE-T3-DECLINE"));

        assertThat(result.outcome()).isEqualTo(Outcome.ISSUER_DECLINED);
        AuthorizationRecord persisted = repository.findById(result.reference()).orElseThrow();
        assertThat(persisted.state()).isEqualTo(AuthorizationState.DECLINED);
        assertThat(persisted.decisionSource()).isEqualTo(DecisionSource.ISSUER);
        assertThat(eventIds()).containsExactly("CLAIM-" + result.reference(),
                "POSSIBLE_SEND-" + result.reference(), "DECLINED-" + result.reference());
        assertThat(issuer.dispatchCount()).isEqualTo(1);
    }

    @Test
    void wiredRuntimeRecordsExplicitNetworkRejectionWithoutIssuerResult() {
        issuer.behaveAs(request -> new DownstreamReply(request.correlationId(),
                IssuerDecision.NETWORK_REJECTED, request.dispatchedAt().plusMillis(100)));
        AuthorizationService service = service(CAPACITY, db);

        AuthorizationResult result = service.authorize(CALLER, request("PURCHASE-T3-NETWORK"));

        assertThat(result.outcome()).isEqualTo(Outcome.PROCESSOR_REJECTED);
        AuthorizationRecord persisted = repository.findById(result.reference()).orElseThrow();
        assertThat(persisted.state()).isEqualTo(AuthorizationState.NETWORK_REJECTED);
        assertThat(persisted.decisionSource()).isEqualTo(DecisionSource.NETWORK);
        assertThat(eventIds()).containsExactly("CLAIM-" + result.reference(),
                "POSSIBLE_SEND-" + result.reference(),
                "NETWORK_REJECTED-" + result.reference());
        assertThat(issuer.dispatchCount()).isEqualTo(1);
    }

    @Test
    void lateEvidenceAdmitsThroughWiredGatewayWithoutResendOrReversal() {
        Instant lateAt = NOW.plusSeconds(5);
        issuer.behaveAs(request -> new DownstreamReply(request.correlationId(),
                IssuerDecision.APPROVED, lateAt));
        AuthorizationService service = service(CAPACITY, db);

        AuthorizationResult first = service.authorize(CALLER, request("PURCHASE-T3-LATE"));

        assertThat(first.outcome()).isEqualTo(Outcome.NOT_CONFIRMED);
        assertThat(issuer.dispatchCount()).isEqualTo(1);
        assertThat(eventIds()).hasSize(2);
        AuthorizationRecord dispatched = repository.findById(first.reference()).orElseThrow();
        assertThat(dispatched.state()).isEqualTo(AuthorizationState.AWAITING);

        clock.setInstant(NOW.plusSeconds(6));
        DownstreamReply late = new DownstreamReply(dispatched.dispatchCorrelationId(),
                IssuerDecision.APPROVED, lateAt);

        LateEvidenceOutcome admitted = service.submitVerifiedEvidence(first.reference(), late);

        assertThat(admitted.outcome()).isEqualTo(Outcome.APPROVED);
        assertThat(admitted.status()).isEqualTo(LateEvidenceAdmission.Status.COMMITTED);
        assertThat(admitted.recorded()).isTrue();
        // One original send only: admission never resends and never reverses the payment.
        assertThat(issuer.dispatchCount()).isEqualTo(1);
        assertThat(eventIds()).hasSize(3);
        assertThat(storeHead()).isEqualTo(3L);
        AuthorizationRecord persisted = repository.findById(first.reference()).orElseThrow();
        assertThat(persisted.state()).isEqualTo(AuthorizationState.APPROVED);
        assertThat(persisted.decisionSource()).isEqualTo(DecisionSource.ISSUER);
        assertThat(repository.readLateEvidenceReceipt(first.reference())).isPresent();

        LateEvidenceOutcome replay = service.submitVerifiedEvidence(first.reference(), late);
        assertThat(replay.status()).isEqualTo(LateEvidenceAdmission.Status.REPLAY);
        assertThat(replay.outcome()).isEqualTo(Outcome.APPROVED);
        assertThat(issuer.dispatchCount()).isEqualTo(1);
        assertThat(eventIds()).hasSize(3);
    }

    @Test
    void wiredDispatchIntentReplayGrantsNoSendPermission() {
        // The port requires the undispatched result of the atomic initial claim. The first intent
        // attempt is made ambiguous before commit, so the durable claim row stays exactly in that
        // state; the two direct attempts below then exercise the wired gateway's real replay path
        // instead of handing it a finalized record (which the composer correctly rejects).
        AuthorizationService service = service(CAPACITY, ambiguousBeforeCommit(2));

        AuthorizationResult result = service.authorize(CALLER, request("PURCHASE-T3-REPLAY"));

        assertThat(result.outcome()).isEqualTo(Outcome.NOT_CONFIRMED);
        AuthorizationRecord claim = repository.findById(result.reference()).orElseThrow();
        assertThat(claim.state()).isEqualTo(AuthorizationState.AWAITING);
        assertThat(claim.dispatchedAt()).isNull();
        assertThat(claim.dispatchCorrelationId()).isNull();
        assertThat(eventIds()).containsExactly("CLAIM-" + claim.id());

        String correlation = "CORR-T3-REPLAY";
        DispatchIntentGateway gateway = gateway(CAPACITY, db);
        DispatchIntentGateway.Decision committed = gateway.dispatchIntent(claim, correlation, NOW);

        assertThat(committed.status()).isEqualTo(DispatchIntentGateway.Status.COMMITTED);
        assertThat(committed.permitsSend()).isTrue();

        DispatchIntentGateway.Decision replay = gateway.dispatchIntent(claim, correlation, NOW);

        assertThat(replay.status()).isEqualTo(DispatchIntentGateway.Status.REPLAY);
        assertThat(replay.permitsSend()).isFalse();
        // One claim and one intent; the replay wrote no event, HEAD or slot.
        assertThat(eventIds()).containsExactly("CLAIM-" + claim.id(),
                "POSSIBLE_SEND-" + claim.id());
        assertThat(storeHead()).isEqualTo(2L);
        assertThat(issuer.dispatchCount()).isZero();
    }

    @Test
    void atomicRuntimeInputsFailClosedInsteadOfInventingDefaults() {
        Map<String, String> complete = new HashMap<>(Map.of(
                "EVENT_TABLE", "events",
                "ENVIRONMENT", ENVIRONMENT,
                "BOOT_INSTANCE_ID", INSTANCE,
                "CRITICAL_MAX_EVENTS_PER_OPERATION", "5",
                "CRITICAL_SLOT_BUDGET", "8"));

        for (String name : complete.keySet()) {
            Map<String, String> missing = new HashMap<>(complete);
            missing.remove(name);
            assertThatThrownBy(() -> AwsRuntime.AtomicConfig.from(missing::get))
                    .as("missing %s must fail closed", name)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining(name);
        }

        Map<String, String> invalid = new HashMap<>(complete);
        invalid.put("CRITICAL_MAX_EVENTS_PER_OPERATION", "five");
        assertThatThrownBy(() -> AwsRuntime.AtomicConfig.from(invalid::get))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CRITICAL_MAX_EVENTS_PER_OPERATION");

        Map<String, String> insufficientBudget = new HashMap<>(complete);
        insufficientBudget.put("CRITICAL_SLOT_BUDGET", "4");
        assertThatThrownBy(() -> AwsRuntime.AtomicConfig.from(insufficientBudget::get))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private AuthorizationService service(CriticalCapacity capacity, DynamoDbClient client) {
        return AwsRuntime.atomicService(client, repository, issuer, journalTable, PROPERTIES,
                clock, config(capacity));
    }

    private DynamoDispatchIntentGateway gateway(CriticalCapacity capacity, DynamoDbClient client) {
        DynamoEventStore store = new DynamoEventStore(client, eventTable,
                com.acquiring.simulator.domain.event.OperationAManifest.manifest(capacity),
                new com.acquiring.simulator.domain.event.EventValidator(), clock,
                CriticalWriteComposer.NONE);
        return new DynamoDispatchIntentGateway(store, repository, journalTable, eventTable, capacity,
                ENVIRONMENT, INSTANCE, new SyntheticInstructionMaterializer());
    }

    private AwsRuntime.AtomicConfig config(CriticalCapacity capacity) {
        return new AwsRuntime.AtomicConfig(eventTable, ENVIRONMENT, INSTANCE, capacity);
    }

    private void assertAtomicGraph(AuthorizationService service) {
        for (String field : List.of("claimGateway", "dispatchIntentGateway", "resultGateway",
                "networkResultGateway", "lateEvidenceGateway")) {
            assertThat(readField(service, field)).as("atomic field %s", field).isNotNull();
        }
    }

    private static Object readField(Object target, String name) {
        try {
            Field field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return field.get(target);
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError("cannot read " + name, failure);
        }
    }

    private PurchaseRequest request(String transactionId) {
        // The functional EVENT partition and the capacity counter are keyed by environment +
        // acquirer + merchant + channel payment reference. Bind the partition to the transaction
        // this request actually carries, so the assertion helpers cannot read a stale partition.
        context = new EventContext(ENVIRONMENT, "ACQ-A", "MERCH-A", transactionId);
        return new PurchaseRequest("MERCH-A", "TERM-A", transactionId,
                new BigDecimal("10.00"), "USD", "CRED-001", EntryMode.SIMULATED_CONTACT,
                CvmIndicator.SIMULATED_VERIFIED, true, null, null);
    }

    private long storeHead() {
        var item = db.getItem(GetItemRequest.builder().tableName(eventTable)
                .key(Map.of(
                        "pk", text(EventTableKeys.eventPartition(context)),
                        "sk", text(EventTableKeys.headSortKey())))
                .consistentRead(true).build()).item();
        // DynamoDB Local returns an empty map (not null) when the HEAD row does not exist.
        return item == null || item.isEmpty() ? 0L
                : Long.parseLong(item.get("lastSequence").n());
    }

    private List<String> eventIds() {
        List<String> ids = new ArrayList<>();
        for (Map<String, AttributeValue> row : rows(EventTableKeys.eventPartition(context), "E#")) {
            ids.add(row.get("eventId").s());
        }
        return ids;
    }

    private Map<String, AttributeValue> slotRow() {
        var item = db.getItem(GetItemRequest.builder().tableName(eventTable)
                .key(Map.of(
                        "pk", text(EventTableKeys.capacityPartition(context)),
                        "sk", text(EventTableKeys.capacitySortKey())))
                .consistentRead(true).build()).item();
        return item == null ? Map.of() : item;
    }

    private List<Map<String, AttributeValue>> rows(String partition, String sortPrefix) {
        List<Map<String, AttributeValue>> rows = new ArrayList<>();
        Map<String, AttributeValue> cursor = Map.of();
        do {
            QueryRequest.Builder query = QueryRequest.builder().tableName(eventTable)
                    .consistentRead(true)
                    .keyConditionExpression("pk = :pk AND begins_with(sk, :prefix)")
                    .expressionAttributeValues(Map.of(
                            ":pk", text(partition),
                            ":prefix", text(sortPrefix)));
            if (!cursor.isEmpty()) {
                query.exclusiveStartKey(cursor);
            }
            QueryResponse page = db.query(query.build());
            rows.addAll(page.items());
            cursor = page.lastEvaluatedKey();
        } while (cursor != null && !cursor.isEmpty());
        return rows;
    }

    /**
     * Delegating client whose indicated {@code transactWriteItems} call commits and then reports an
     * ambiguous transport outcome, exercising the real gateway recovery path.
     */
    private static DynamoDbClient failingOnTransaction(int failAt) {
        AtomicInteger calls = new AtomicInteger();
        return (DynamoDbClient) java.lang.reflect.Proxy.newProxyInstance(
                DynamoDbClient.class.getClassLoader(),
                new Class<?>[] {DynamoDbClient.class},
                (proxy, method, args) -> {
                    if ("transactWriteItems".equals(method.getName())
                            && calls.incrementAndGet() == failAt) {
                        invoke(db, method, args);
                        throw SdkClientException.create("simulated ambiguous outcome");
                    }
                    return invoke(db, method, args);
                });
    }

    /**
     * Delegating client whose indicated {@code transactWriteItems} call fails as an ambiguous
     * transport error BEFORE the request reaches DynamoDB, so no intent is committed.
     */
    private static DynamoDbClient ambiguousBeforeCommit(int failAt) {
        AtomicInteger calls = new AtomicInteger();
        return (DynamoDbClient) java.lang.reflect.Proxy.newProxyInstance(
                DynamoDbClient.class.getClassLoader(),
                new Class<?>[] {DynamoDbClient.class},
                (proxy, method, args) -> {
                    if ("transactWriteItems".equals(method.getName())
                            && calls.incrementAndGet() == failAt) {
                        throw SdkClientException.create("simulated ambiguous outcome before commit");
                    }
                    return invoke(db, method, args);
                });
    }

    /**
     * Delegating client that definitively rejects the first {@code transactWriteItems} call without
     * touching DynamoDB, and counts every such attempt.
     */
    private static DynamoDbClient rejectingFirstTransaction(AtomicInteger transactions) {
        return (DynamoDbClient) java.lang.reflect.Proxy.newProxyInstance(
                DynamoDbClient.class.getClassLoader(),
                new Class<?>[] {DynamoDbClient.class},
                (proxy, method, args) -> {
                    if ("transactWriteItems".equals(method.getName())) {
                        transactions.incrementAndGet();
                        throw TransactionCanceledException.builder()
                                .message("simulated conditional rejection").build();
                    }
                    return invoke(db, method, args);
                });
    }

    private static Object invoke(Object target, Method method, Object[] args) throws Throwable {        try {
            return method.invoke(target, args);
        } catch (java.lang.reflect.InvocationTargetException e) {
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
