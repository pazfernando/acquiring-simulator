package com.acquiring.simulator.aws;

import static org.assertj.core.api.Assertions.assertThat;

import com.acquiring.simulator.StubIssuerSimulator;
import com.acquiring.simulator.adapter.aws.CriticalWriteComposer;
import com.acquiring.simulator.adapter.aws.DynamoAuthorizationRepository;
import com.acquiring.simulator.adapter.aws.DynamoClaimGateway;
import com.acquiring.simulator.adapter.aws.DynamoDispatchIntentGateway;
import com.acquiring.simulator.adapter.aws.DynamoEventStore;
import com.acquiring.simulator.adapter.aws.DynamoResultGateway;
import com.acquiring.simulator.config.SimulatorProperties;
import com.acquiring.simulator.domain.AuthorizationService;
import com.acquiring.simulator.domain.IdentifierSource;
import com.acquiring.simulator.domain.event.CommittedVersionEvidence;
import com.acquiring.simulator.domain.event.CriticalCapacity;
import com.acquiring.simulator.domain.event.EventContext;
import com.acquiring.simulator.domain.event.EventManifest;
import com.acquiring.simulator.domain.event.EventTableKeys;
import com.acquiring.simulator.domain.event.EventValidator;
import com.acquiring.simulator.domain.event.OperationAManifest;
import com.acquiring.simulator.domain.model.AuthorizationRecord;
import com.acquiring.simulator.domain.model.AuthorizationResult;
import com.acquiring.simulator.domain.model.AuthorizationState;
import com.acquiring.simulator.domain.model.AuthorizedContext;
import com.acquiring.simulator.domain.model.CvmIndicator;
import com.acquiring.simulator.domain.model.DecisionSource;
import com.acquiring.simulator.domain.model.EntryMode;
import com.acquiring.simulator.domain.model.EvidenceEvent;
import com.acquiring.simulator.domain.model.Outcome;
import com.acquiring.simulator.domain.model.PurchaseRequest;
import com.acquiring.simulator.domain.model.RejectionAudit;
import com.acquiring.simulator.domain.model.Route;
import com.acquiring.simulator.domain.port.AuthorizationRepository;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.KeySchemaElement;
import software.amazon.awssdk.services.dynamodb.model.KeyType;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType;

/**
 * P1 closure acceptance scenarios OBS-20 and OBS-22(b), executed end to end through the real
 * adapters and {@link AuthorizationService} against DynamoDB Local.
 *
 * <p>OBS-20: with the required journal and EVENT table healthy and no notifier / WebSocket / query
 * adapter / ADOT collaborator in the construction (those backends are structurally absent in this
 * phase, not simulated), one explicit authorization commits ONE payment record and its critical
 * consultable events atomically and returns APPROVED in the same invocation, with no duplicate
 * projection or outbox payload.
 *
 * <p>OBS-22(b): when the required atomic write before send cannot commit, the current invocation is
 * NOT_CONFIRMED, nothing is dispatched, no result evidence is invented and no resend occurs. The
 * failure is produced by the real composed transaction (an explicit test-only capacity budget the
 * send-intent slot reservation cannot satisfy), not by a stub gateway.
 *
 * <p>This is a local DynamoDB Local test. It does not activate {@code AwsRuntime}, performs no real
 * AWS call, and proves nothing about C4 cryptography or P5 IAM/JWT. The principal bound by the test
 * is the existing context-only seam. Capacity {@code (5,8)} / {@code (1,1)} are test examples, not
 * approved production values.
 */
@Testcontainers
class ObsAcceptanceAtomicChainIntegrationTest {

    @Container static final GenericContainer<?> DYNAMO = new GenericContainer<>(
            DockerImageName.parse("amazon/dynamodb-local:2.6.1")).withExposedPorts(8000);

    private static final String ENVIRONMENT = "SIM";
    private static final String INSTANCE = "BOOT-P1CLOSE";
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
    NoLegacyWriteRepository guarded;

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
        store = new DynamoEventStore(db, eventTable, manifest(capacity(5, 8)),
                new EventValidator(), Clock.fixed(NOW, ZoneOffset.UTC), CriticalWriteComposer.NONE);
        issuer = new StubIssuerSimulator();
        guarded = new NoLegacyWriteRepository(repository);
    }

    @AfterEach void dropTables() {
        db.deleteTable(b -> b.tableName(eventTable));
        db.deleteTable(b -> b.tableName(journalTable));
    }

    @Test
    void obs20OnePaymentAndCriticalEventsCommitAtomicallyAndApproveWithoutAsyncBackends() {
        CriticalCapacity capacity = capacity(5, 8);
        AuthorizationService service = service(capacity);

        AuthorizationResult result = service.authorize(CALLER, request("SIM-APP-001"));

        // APPROVED is returned by this invocation itself: no callback, notifier or catch-up step
        // is involved in producing the outcome, and the payment was dispatched exactly once.
        assertThat(result.outcome()).isEqualTo(Outcome.APPROVED);
        assertThat(issuer.dispatchCount()).isEqualTo(1);
        assertThat(guarded.claimCalls).isZero();
        assertThat(guarded.dispatches).isZero();

        // ONE payment record, with the outcome derived from persisted state.
        AuthorizationRecord persisted = repository.findById(result.reference()).orElseThrow();
        assertThat(persisted.state()).isEqualTo(AuthorizationState.APPROVED);
        assertThat(persisted.decisionSource()).isEqualTo(DecisionSource.ISSUER);
        assertThat(persisted.dispatchedAt()).isEqualTo(NOW);
        assertThat(persisted.dispatchCorrelationId()).isNotBlank();

        // Three critical consultable events (claim, possible send, verified result), one copy each.
        List<Map<String, AttributeValue>> events = eventRows();
        assertThat(events).hasSize(3);
        assertThat(events).extracting(row -> row.get("sk").s())
                .containsExactly("E#000000000001", "E#000000000002", "E#000000000003");
        Set<String> hashes = new HashSet<>();
        for (Map<String, AttributeValue> row : events) {
            assertThat(hashes.add(row.get("eventSha256").s()))
                    .as("each canonical payload is stored once").isTrue();
        }

        // Atomic single HEAD advance and slot accounting in the same transaction.
        assertThat(store.head(CONTEXT).orElseThrow().lastSequence()).isEqualTo(3);
        Map<String, AttributeValue> slots = slotRow();
        assertThat(slots.get("usedSlots").n()).isEqualTo("3");
        assertThat(slots.get("slotBudget").n()).isEqualTo("8");

        // No duplicate projection / outbox payload: the dedupe partition keeps identity metadata
        // only, one row per critical event, and each row binds the same hash as the event row.
        List<Map<String, AttributeValue>> dedupeRows = rows(
                EventTableKeys.dedupePartition(ENVIRONMENT), "P#");
        assertThat(dedupeRows).hasSize(3);
        for (Map<String, AttributeValue> row : dedupeRows) {
            assertThat(row).doesNotContainKeys("payload", "event", "canonicalEvent");
            assertThat(row.get("eventSha256").s()).isIn(hashes);
        }

        // No legacy (journal-table) evidence event was written beside the event source.
        assertThat(repository.eventsFor(result.reference())).isEmpty();

        // A later independent observation read is catch-up, not a new payment action.
        assertThat(eventRows()).hasSize(3);
        assertThat(store.head(CONTEXT).orElseThrow().lastSequence()).isEqualTo(3);
        assertThat(issuer.dispatchCount()).isEqualTo(1);
        assertThat(guarded.dispatches).isZero();
    }

    @Test
    void obs22RequiredAtomicWriteFailureBeforeSendDoesNotDispatchOrResend() {
        // Explicit test-only budget: the claim consumes the single slot, so the required
        // POSSIBLE_SEND transaction cannot commit. This is the real composed write failing,
        // not a gateway stub returning a fabricated status.
        CriticalCapacity capacity = capacity(1, 1);
        AuthorizationService service = service(capacity);

        AuthorizationResult result = service.authorize(CALLER, request("SIM-APP-002"));

        // Conservative outcome for the current invocation; never a fabricated confirmation.
        assertThat(result.outcome()).isEqualTo(Outcome.NOT_CONFIRMED);

        // Storage failure before send: zero downstream calls, zero legacy dispatch writes.
        assertThat(issuer.dispatchCount()).isZero();
        assertThat(guarded.dispatches).isZero();
        assertThat(guarded.claimCalls).isZero();

        // Only the claim is durable; no send-intent or result event and no result evidence.
        List<Map<String, AttributeValue>> events = eventRows();
        assertThat(events).hasSize(1);
        assertThat(events.get(0).get("sk").s()).isEqualTo("E#000000000001");
        assertThat(store.head(CONTEXT).orElseThrow().lastSequence()).isEqualTo(1);
        assertThat(slotRow().get("usedSlots").n()).isEqualTo("1");
        assertThat(rows(EventTableKeys.dedupePartition(ENVIRONMENT), "P#")).hasSize(1);

        // The business record stays unsent and undetermined; no result was invented.
        AuthorizationRecord persisted = repository.findById(result.reference()).orElseThrow();
        assertThat(persisted.state()).isEqualTo(AuthorizationState.AWAITING);
        assertThat(persisted.dispatchedAt()).isNull();
        assertThat(persisted.dispatchCorrelationId()).isNull();
        assertThat(persisted.outcomeDeterminedAt()).isNull();

        // No resend: repeating the observation read does not dispatch or write anything.
        assertThat(eventRows()).hasSize(1);
        assertThat(issuer.dispatchCount()).isZero();
    }

    private AuthorizationService service(CriticalCapacity capacity) {
        return new AuthorizationService(guarded, issuer, PROPERTIES,
                Clock.fixed(NOW, ZoneOffset.UTC),
                new DynamoClaimGateway(store, repository, journalTable, eventTable, capacity,
                        ENVIRONMENT, INSTANCE),
                new DynamoDispatchIntentGateway(store, repository, journalTable, eventTable,
                        capacity, ENVIRONMENT, INSTANCE),
                new DynamoResultGateway(store, repository, journalTable, eventTable, capacity,
                        ENVIRONMENT, INSTANCE),
                IdentifierSource.randomHex());
    }

    private static CriticalCapacity capacity(int reserved, int budget) {
        return new CriticalCapacity(reserved, budget);
    }

    private static EventManifest manifest(CriticalCapacity capacity) {
        return OperationAManifest.manifest(capacity);
    }

    private PurchaseRequest request(String credential) {
        return new PurchaseRequest("MERCH-SIM-001", "TERM-SIM-001", TRANSACTION_ID,
                new BigDecimal("10.00"), "USD", credential, EntryMode.SIMULATED_CONTACT,
                CvmIndicator.SIMULATED_VERIFIED, true, null, null);
    }

    private List<Map<String, AttributeValue>> eventRows() {
        return rows(EventTableKeys.eventPartition(CONTEXT), "E#");
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

    private Map<String, AttributeValue> slotRow() {
        var item = db.getItem(GetItemRequest.builder().tableName(eventTable)
                .key(Map.of(
                        "pk", text(EventTableKeys.capacityPartition(CONTEXT)),
                        "sk", text(EventTableKeys.capacitySortKey())))
                .consistentRead(true).build()).item();
        return item == null ? Map.of() : item;
    }

    /** Delegating repository that fails the test if a legacy claim or dispatch write occurs. */
    private static final class NoLegacyWriteRepository implements AuthorizationRepository {
        private final DynamoAuthorizationRepository delegate;
        int claimCalls;
        int dispatches;

        NoLegacyWriteRepository(DynamoAuthorizationRepository delegate) {
            this.delegate = delegate;
        }

        @Override public void claim(AuthorizationRecord record) {
            claimCalls++;
            throw new AssertionError("legacy claim() must not run on the atomic path");
        }

        @Override public void markDispatched(String authorizationId, String correlationId,
                Instant dispatchedAt) {
            dispatches++;
            throw new AssertionError("legacy markDispatched() must not run on the atomic path");
        }

        @Override public void updateOutcome(String authorizationId, String state,
                String decisionSource, String reasonCategory, Instant outcomeDeterminedAt) {
            throw new AssertionError("legacy updateOutcome() must not run on the atomic path");
        }

        @Override public Optional<AuthorizationRecord> findById(String id) {
            return delegate.findById(id);
        }

        @Override public Optional<AuthorizationRecord> findLatestByBusinessKey(String businessKey) {
            return delegate.findLatestByBusinessKey(businessKey);
        }

        @Override public List<EvidenceEvent> eventsFor(String authorizationId) {
            return delegate.eventsFor(authorizationId);
        }

        @Override public void appendEvent(EvidenceEvent event) {
            throw new AssertionError("legacy appendEvent() must not run on the atomic path");
        }

        @Override public void appendRejection(RejectionAudit audit) {
            throw new AssertionError("legacy appendRejection() must not run on the atomic path");
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
