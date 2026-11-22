package com.acquiring.simulator.aws;

import static org.assertj.core.api.Assertions.assertThat;

import com.acquiring.simulator.StubIssuerSimulator;
import com.acquiring.simulator.adapter.aws.CriticalWriteComposer;
import com.acquiring.simulator.adapter.aws.DynamoAuthorizationRepository;
import com.acquiring.simulator.adapter.aws.DynamoClaimGateway;
import com.acquiring.simulator.adapter.aws.DynamoEventStore;
import com.acquiring.simulator.config.SimulatorProperties;
import com.acquiring.simulator.domain.AuthorizationService;
import com.acquiring.simulator.domain.event.CommittedVersionEvidence;
import com.acquiring.simulator.domain.event.CriticalCapacity;
import com.acquiring.simulator.domain.event.EventContext;
import com.acquiring.simulator.domain.event.EventDeduplication;
import com.acquiring.simulator.domain.event.EventManifest;
import com.acquiring.simulator.domain.event.EventTableKeys;
import com.acquiring.simulator.domain.event.EventValidator;
import com.acquiring.simulator.domain.event.OperationAManifest;
import com.acquiring.simulator.domain.model.AuthorizationRecord;
import com.acquiring.simulator.domain.model.AuthorizationResult;
import com.acquiring.simulator.domain.model.AuthorizedContext;
import com.acquiring.simulator.domain.model.CvmIndicator;
import com.acquiring.simulator.domain.model.EntryMode;
import com.acquiring.simulator.domain.model.EvidenceEvent;
import com.acquiring.simulator.domain.model.Outcome;
import com.acquiring.simulator.domain.model.PurchaseRequest;
import com.acquiring.simulator.domain.model.RejectionAudit;
import com.acquiring.simulator.domain.model.Route;
import com.acquiring.simulator.domain.port.AuthorizationRepository;
import com.acquiring.simulator.domain.port.ClaimGateway;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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

/**
 * P1b2d service-level claim-path slice: the selected atomic claim port is called exactly once, the
 * claim response acts on the committed-version evidence read back from the stored claim row, and a
 * failed or unestablished claim cannot dispatch, fall back to the legacy {@code claim()} path or
 * dual-write.
 *
 * <p>The legacy {@code claim()} method is wrapped so any call fails the test outright: the atomic
 * path must not depend on it. This does not activate the runtime: {@code AwsRuntime} still builds
 * the service without a claim gateway.
 */
@Testcontainers
class AtomicClaimServiceWiringIntegrationTest {

    @Container static final GenericContainer<?> DYNAMO = new GenericContainer<>(
            DockerImageName.parse("amazon/dynamodb-local:2.6.1")).withExposedPorts(8000);

    /** Test-only example capacity; owner approval of the O8 numbers is still pending. */
    private static final CriticalCapacity CAPACITY = new CriticalCapacity(5, 8);
    private static final EventManifest MANIFEST = OperationAManifest.manifest(CAPACITY);
    private static final String ENVIRONMENT = "SIM";
    private static final String INSTANCE = "BOOT-P1B2D";
    private static final Instant NOW = Instant.parse("2026-11-18T12:00:01Z");
    private static final String TRANSACTION_ID = "PURCHASE-1";
    private static final EventContext CONTEXT =
            new EventContext(ENVIRONMENT, "ACQ-SIM-001", "MERCH-SIM-001", TRANSACTION_ID);

    private static final AuthorizedContext CALLER = new AuthorizedContext(
            "ACQ-SIM-001", "MERCH-SIM-001", "TEST", Set.of(Route.DOMESTIC));
    private static final SimulatorProperties PROPERTIES = new SimulatorProperties(
            new SimulatorProperties.Authorization(Duration.ofHours(24), Duration.ofSeconds(2)),
            new SimulatorProperties.Profile(
                    List.of(new SimulatorProperties.AcquirerFixture("ACQ-SIM-001",
                            List.of(Route.DOMESTIC))),
                    List.of(new SimulatorProperties.MerchantFixture("MERCH-SIM-001", "ACQ-SIM-001")),
                    List.of(new SimulatorProperties.TerminalFixture("TERM-SIM-001", "MERCH-SIM-001")),
                    List.of(new SimulatorProperties.CredentialRange("SIM-APP-", 
                            com.acquiring.simulator.domain.model.CredentialDecision.APPROVE,
                            Route.DOMESTIC))));

    static DynamoDbClient db;
    String journalTable;
    String eventTable;
    DynamoAuthorizationRepository repository;
    DynamoEventStore store;
    StubIssuerSimulator issuer;
    NoLegacyClaimRepository guarded;

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
        store = new DynamoEventStore(db, eventTable, MANIFEST, new EventValidator(),
                Clock.fixed(NOW, ZoneOffset.UTC), CriticalWriteComposer.NONE);
        issuer = new StubIssuerSimulator();
        guarded = new NoLegacyClaimRepository(repository);
    }

    @AfterEach void dropTables() {
        db.deleteTable(b -> b.tableName(eventTable));
        db.deleteTable(b -> b.tableName(journalTable));
    }

    @Test void atomicClaimPathCommitsOnceDispatchesOnceAndReportsPersistedCommittedVersion() {
        AuthorizationService service = service(gateway(store));

        AuthorizationResult result = service.authorize(CALLER, request("SIM-APP-001"));

        assertThat(result.outcome()).isEqualTo(Outcome.APPROVED);
        assertThat(result.reference()).startsWith("AUTH-");
        assertThat(guarded.claimCalls).isZero();
        assertThat(issuer.dispatchCount()).isEqualTo(1);

        // One claim event, one HEAD advance and one slot reservation: no second business write.
        assertThat(eventRows()).hasSize(1);
        assertThat(store.head(CONTEXT).orElseThrow().lastSequence()).isEqualTo(1);
        assertThat(slotRow().get("usedSlots").n()).isEqualTo("1");

        AuthorizationRecord persisted = repository.findById(result.reference()).orElseThrow();
        assertThat(persisted.state()).isEqualTo(com.acquiring.simulator.domain.model.AuthorizationState.APPROVED);
        assertThat(persisted.dispatchedAt()).isEqualTo(NOW);
        assertThat(persisted.dispatchCorrelationId()).isNotBlank();

        // The claim response consumed the stored committed version; CAS base and committed version
        // remain separate persisted facts.
        CommittedVersionEvidence evidence = repository.readVersionEvidence(result.reference());
        assertThat(evidence.committedVersionRecorded()).isTrue();
        assertThat(evidence.committedJournalVersion()).isEqualTo(1L);
        assertThat(evidence.storageVersion()).isNotNegative();
    }

    @Test void ambiguousCommitAfterWriteIsRecoveredAndReadsPersistedCommittedVersion() {
        DynamoDbClient ambiguous = ambiguousClient(true);
        DynamoEventStore ambiguousStore = new DynamoEventStore(ambiguous, eventTable, MANIFEST,
                new EventValidator(), Clock.fixed(NOW, ZoneOffset.UTC), CriticalWriteComposer.NONE);
        AuthorizationService service = service(gateway(ambiguousStore));

        AuthorizationResult result = service.authorize(CALLER, request("SIM-APP-001"));

        assertThat(result.outcome()).isEqualTo(Outcome.APPROVED);
        assertThat(guarded.claimCalls).isZero();
        assertThat(issuer.dispatchCount()).isEqualTo(1);
        // Recovery must not duplicate the claim event, HEAD or slot.
        assertThat(eventRows()).hasSize(1);
        assertThat(store.head(CONTEXT).orElseThrow().lastSequence()).isEqualTo(1);
        assertThat(slotRow().get("usedSlots").n()).isEqualTo("1");
        // The recovered record reports committed version 1 read from persistence.
        assertThat(repository.readVersionEvidence(result.reference()).committedJournalVersion())
                .isEqualTo(1L);
        assertThat(repository.findById(result.reference()).orElseThrow().dispatchedAt())
                .isEqualTo(NOW);
    }

    @Test void unknownClaimOutcomeCannotDispatchOrFallBackToLegacyClaimOrWrite() {
        AuthorizationService service = service((record, correlationId, now) ->
                new ClaimGateway.Decision(ClaimGateway.Status.UNKNOWN, null, "lookup failed"));

        AuthorizationResult result = service.authorize(CALLER, request("SIM-APP-001"));

        assertThat(result.outcome()).isEqualTo(Outcome.NOT_CONFIRMED);
        assertThat(guarded.claimCalls).isZero();
        assertThat(issuer.dispatchCount()).isZero();
        assertThat(repository.findById(result.reference())).isEmpty();
        assertThat(repository.findLatestByBusinessKey("ACQ-SIM-001|MERCH-SIM-001|" + TRANSACTION_ID))
                .isEmpty();
        assertThat(eventRows()).isEmpty();
        assertThat(slotRow()).isEmpty();
        assertThat(repository.eventsFor(result.reference())).isEmpty();
    }

    @Test void contentConflictCannotDispatchOrWriteAndIsRejectedAsPaymentDataConflict() {
        AuthorizationService service = service((record, correlationId, now) ->
                new ClaimGateway.Decision(ClaimGateway.Status.CONTENT_CONFLICT, null,
                        "identity already stored with different content"));

        AuthorizationResult result = service.authorize(CALLER, request("SIM-APP-001"));

        assertThat(result.outcome()).isEqualTo(Outcome.PROCESSOR_REJECTED);
        assertThat(result.reference()).isNull();
        assertThat(guarded.claimCalls).isZero();
        assertThat(issuer.dispatchCount()).isZero();
        assertThat(repository.findLatestByBusinessKey("ACQ-SIM-001|MERCH-SIM-001|" + TRANSACTION_ID))
                .isEmpty();
        assertThat(eventRows()).isEmpty();
        assertThat(guarded.rejections).hasSize(1);
        assertThat(guarded.rejections.get(0).reason().name()).isEqualTo("PAYMENT_DATA_CONFLICT");
    }

    @Test void rejectedClaimWithoutACompetingWinnerLeavesNoWriteAndDoesNotReclaim() {
        AuthorizationService service = service((record, correlationId, now) ->
                new ClaimGateway.Decision(ClaimGateway.Status.NOT_COMMITTED, null,
                        "transaction cancelled"));

        AuthorizationResult result = service.authorize(CALLER, request("SIM-APP-001"));

        assertThat(result.outcome()).isEqualTo(Outcome.NOT_CONFIRMED);
        assertThat(guarded.claimCalls).isZero();
        // One generation lookup before the claim, one read to establish whether a claim won.
        assertThat(guarded.businessKeyReads).isEqualTo(2);
        assertThat(issuer.dispatchCount()).isZero();
        assertThat(repository.findById(result.reference())).isEmpty();
        assertThat(eventRows()).isEmpty();
        assertThat(slotRow()).isEmpty();
    }

    @Test void claimedWithoutPersistedCommittedEvidenceDoesNotDispatch() {
        AuthorizationService service = service((record, correlationId, now) ->
                new ClaimGateway.Decision(ClaimGateway.Status.CLAIMED, null, null));

        AuthorizationResult result = service.authorize(CALLER, request("SIM-APP-001"));

        assertThat(result.outcome()).isEqualTo(Outcome.NOT_CONFIRMED);
        assertThat(guarded.claimCalls).isZero();
        assertThat(guarded.dispatches).isZero();
        assertThat(issuer.dispatchCount()).isZero();
        assertThat(eventRows()).isEmpty();
    }

    private AuthorizationService service(ClaimGateway gateway) {
        return new AuthorizationService(guarded, issuer, PROPERTIES, Clock.fixed(NOW, ZoneOffset.UTC),
                gateway);
    }

    private DynamoClaimGateway gateway(DynamoEventStore eventStore) {
        return new DynamoClaimGateway(eventStore, repository, journalTable, eventTable, CAPACITY,
                ENVIRONMENT, INSTANCE);
    }

    private PurchaseRequest request(String credential) {
        return new PurchaseRequest("MERCH-SIM-001", "TERM-SIM-001", TRANSACTION_ID,
                new BigDecimal("10.00"), "USD", credential, EntryMode.SIMULATED_CONTACT,
                CvmIndicator.SIMULATED_VERIFIED, true, null, null);
    }

    /** Delegating repository that fails the test if the legacy claim path is reached. */
    private static final class NoLegacyClaimRepository implements AuthorizationRepository {
        private final DynamoAuthorizationRepository delegate;
        int claimCalls;
        int businessKeyReads;
        int dispatches;
        final List<RejectionAudit> rejections = new ArrayList<>();

        NoLegacyClaimRepository(DynamoAuthorizationRepository delegate) {
            this.delegate = delegate;
        }

        @Override public void claim(AuthorizationRecord record) {
            claimCalls++;
            throw new AssertionError("legacy claim() must not run on the atomic claim path");
        }

        @Override public void markDispatched(String authorizationId, String correlationId,
                Instant dispatchedAt) {
            dispatches++;
            delegate.markDispatched(authorizationId, correlationId, dispatchedAt);
        }

        @Override public void updateOutcome(String authorizationId, String state, String decisionSource,
                String reasonCategory, Instant outcomeDeterminedAt) {
            delegate.updateOutcome(authorizationId, state, decisionSource, reasonCategory,
                    outcomeDeterminedAt);
        }

        @Override public Optional<AuthorizationRecord> findById(String id) {
            return delegate.findById(id);
        }

        @Override public Optional<AuthorizationRecord> findLatestByBusinessKey(String businessKey) {
            businessKeyReads++;
            return delegate.findLatestByBusinessKey(businessKey);
        }

        @Override public List<EvidenceEvent> eventsFor(String authorizationId) {
            return delegate.eventsFor(authorizationId);
        }

        @Override public void appendEvent(EvidenceEvent event) {
            delegate.appendEvent(event);
        }

        @Override public void appendRejection(RejectionAudit audit) {
            rejections.add(audit);
            delegate.appendRejection(audit);
        }
    }

    private Map<String, AttributeValue> journalRow(String pk, String sk) {
        return db.getItem(GetItemRequest.builder().tableName(journalTable)
                .key(Map.of("pk", text(pk), "sk", text(sk))).consistentRead(true).build()).item();
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

    /** Delegating client whose first transactWriteItems reports an ambiguous transport outcome. */
    private static DynamoDbClient ambiguousClient(boolean commitBeforeFailing) {
        AtomicInteger calls = new AtomicInteger();
        return (DynamoDbClient) java.lang.reflect.Proxy.newProxyInstance(
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
