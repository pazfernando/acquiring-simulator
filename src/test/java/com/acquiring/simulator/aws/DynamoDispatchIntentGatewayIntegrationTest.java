package com.acquiring.simulator.aws;

import static org.assertj.core.api.Assertions.assertThat;

import com.acquiring.simulator.adapter.aws.CriticalWriteComposer;
import com.acquiring.simulator.adapter.aws.DynamoAuthorizationRepository;
import com.acquiring.simulator.adapter.aws.DynamoClaimGateway;
import com.acquiring.simulator.adapter.aws.DynamoDispatchIntentGateway;
import com.acquiring.simulator.adapter.aws.DynamoEventStore;
import com.acquiring.simulator.adapter.aws.PossibleSendCriticalComposer;
import com.acquiring.simulator.domain.event.CriticalCapacity;
import com.acquiring.simulator.domain.event.EventContext;
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
import com.acquiring.simulator.domain.port.ClaimGateway;
import com.acquiring.simulator.domain.port.DispatchIntentGateway;
import java.lang.reflect.Method;
import java.net.URI;
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

/**
 * P1b3c dispatch-intent adapter against DynamoDB Local: one atomic {@code POSSIBLE_SEND} transition
 * through the narrow {@link DispatchIntentGateway} port, with the committed version read back from
 * persistence.
 *
 * <p>The adapter never dispatches payment, never calls the legacy {@code markDispatched} path and is
 * not activated in {@code AwsRuntime}. These tests prove local transaction mechanics and the send
 * gate classification only; result coupling and real AWS effects are unverified.
 */
@Testcontainers
class DynamoDispatchIntentGatewayIntegrationTest {

    @Container static final GenericContainer<?> DYNAMO = new GenericContainer<>(
            DockerImageName.parse("amazon/dynamodb-local:2.6.1")).withExposedPorts(8000);

    /** Test-only example capacity; owner approval of the O8 numbers is still pending. */
    private static final CriticalCapacity CAPACITY = new CriticalCapacity(5, 8);
    private static final EventManifest MANIFEST = OperationAManifest.manifest(CAPACITY);
    private static final String ENVIRONMENT = "SIM";
    private static final String INSTANCE = "BOOT-P1B3C";
    private static final String AUTHORIZATION_ID = "AUTH-001";
    private static final String BUSINESS_KEY = "ACQ-A|MERCHANT-A|TX-001";
    private static final String DISPATCH_CORRELATION = "CORR-SEND-001";
    private static final Instant NOW = Instant.parse("2026-11-18T12:00:01Z");
    private static final Instant DISPATCHED_AT = NOW.plusSeconds(1);
    private static final EventContext CONTEXT =
            new EventContext(ENVIRONMENT, "ACQ-A", "MERCHANT-A", "TX-001");

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

    @Test void newIntentPersistsExactTransitionAndCommittedVersion() {
        AuthorizationRecord claim = committedClaim();

        DispatchIntentGateway.Decision decision = gateway(store).dispatchIntent(
                claim, DISPATCH_CORRELATION, DISPATCHED_AT);

        assertThat(decision.status()).isEqualTo(DispatchIntentGateway.Status.COMMITTED);
        assertThat(decision.permitsSend()).isTrue();
        assertThat(decision.evidence().storageVersion()).isEqualTo(1L);
        assertThat(decision.evidence().committedJournalVersion()).isEqualTo(2L);

        AuthorizationRecord persisted = repository.findById(AUTHORIZATION_ID).orElseThrow();
        assertThat(persisted.state()).isEqualTo(AuthorizationState.AWAITING);
        assertThat(persisted.decisionSource()).isEqualTo(DecisionSource.NONE);
        assertThat(persisted.dispatchedAt()).isEqualTo(DISPATCHED_AT);
        assertThat(persisted.dispatchCorrelationId()).isEqualTo(DISPATCH_CORRELATION);

        assertThat(eventRows()).hasSize(2);
        assertThat(store.head(CONTEXT).orElseThrow().lastSequence()).isEqualTo(2);
        assertThat(slotRow().get("usedSlots").n()).isEqualTo("2");

        StoredEvent stored = store.find(submission(claim).dedupeKey()).orElseThrow();
        assertThat(stored.sourceSequence()).isEqualTo(2);
        assertThat(stored.journalVersion()).isEqualTo(2L);
        assertThat(stored.stateAfter())
                .isEqualTo(PossibleSendCriticalComposer.POSSIBLE_SEND_STATE_AFTER);
    }

    @Test void replayedIntentIsKnowledgeOnlyAndWritesNothingNew() {
        AuthorizationRecord claim = committedClaim();
        DynamoDispatchIntentGateway gateway = gateway(store);

        DispatchIntentGateway.Decision first = gateway.dispatchIntent(
                claim, DISPATCH_CORRELATION, DISPATCHED_AT);
        DispatchIntentGateway.Decision second = gateway.dispatchIntent(
                claim, DISPATCH_CORRELATION, DISPATCHED_AT);

        assertThat(first.status()).isEqualTo(DispatchIntentGateway.Status.COMMITTED);
        assertThat(second.status()).isEqualTo(DispatchIntentGateway.Status.REPLAY);
        assertThat(second.permitsSend()).isFalse();
        assertThat(eventRows()).hasSize(2);
        assertThat(store.head(CONTEXT).orElseThrow().lastSequence()).isEqualTo(2);
        assertThat(slotRow().get("usedSlots").n()).isEqualTo("2");
    }

    @Test void cancelledIntentIsNotCommittedAndLeavesTheClaimUntouched() {
        AuthorizationRecord claim = committedClaim();
        repository.updateOutcome(AUTHORIZATION_ID, AuthorizationState.DECLINED.name(),
                DecisionSource.ISSUER.name(), "TEST", NOW);

        DispatchIntentGateway.Decision decision = gateway(store).dispatchIntent(
                claim, DISPATCH_CORRELATION, DISPATCHED_AT);

        assertThat(decision.status()).isEqualTo(DispatchIntentGateway.Status.NOT_COMMITTED);
        assertThat(decision.permitsSend()).isFalse();
        assertThat(eventRows()).hasSize(1);
        assertThat(slotRow().get("usedSlots").n()).isEqualTo("1");
        AuthorizationRecord persisted = repository.findById(AUTHORIZATION_ID).orElseThrow();
        assertThat(persisted.state()).isEqualTo(AuthorizationState.DECLINED);
        assertThat(persisted.dispatchedAt()).isNull();
    }

    @Test void ambiguousIntentWithoutCommitStaysUnknownAndNeverImpliesNoSend() {
        AuthorizationRecord claim = committedClaim();
        DynamoEventStore ambiguousStore = new DynamoEventStore(ambiguousClient(false), eventTable,
                MANIFEST, new EventValidator(), Clock.fixed(NOW, ZoneOffset.UTC),
                CriticalWriteComposer.NONE);

        DispatchIntentGateway.Decision decision = gateway(ambiguousStore).dispatchIntent(
                claim, DISPATCH_CORRELATION, DISPATCHED_AT);

        assertThat(decision.status()).isEqualTo(DispatchIntentGateway.Status.UNKNOWN);
        assertThat(decision.permitsSend()).isFalse();
        assertThat(decision.evidence()).isNull();
        // The claim remains undispatched and no intent row was written; uncertainty is preserved.
        assertThat(eventRows()).hasSize(1);
        assertThat(slotRow().get("usedSlots").n()).isEqualTo("1");
        assertThat(repository.findById(AUTHORIZATION_ID).orElseThrow().dispatchedAt()).isNull();
    }

    private DynamoDispatchIntentGateway gateway(DynamoEventStore eventStore) {
        return new DynamoDispatchIntentGateway(eventStore, repository, journalTable, eventTable,
                CAPACITY, ENVIRONMENT, INSTANCE);
    }

    private AuthorizationRecord committedClaim() {
        AuthorizationRecord claim = claim();
        ClaimGateway.Decision decision = new DynamoClaimGateway(store, repository, journalTable,
                eventTable, CAPACITY, ENVIRONMENT, INSTANCE)
                .claim(claim, "CORR-CLAIM-001", NOW);
        assertThat(decision.status()).isEqualTo(ClaimGateway.Status.CLAIMED);
        assertThat(decision.evidence().agreesWith(0L, 1L)).isTrue();
        return claim;
    }

    private EventSubmission submission(AuthorizationRecord claim) {
        return PossibleSendCriticalComposer.submission(claim, DISPATCH_CORRELATION, ENVIRONMENT,
                INSTANCE, DISPATCHED_AT);
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

    /** Delegating client whose first transactWriteItems reports an ambiguous outcome. */
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
