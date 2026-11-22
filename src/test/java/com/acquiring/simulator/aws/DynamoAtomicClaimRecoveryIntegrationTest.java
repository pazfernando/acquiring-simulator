package com.acquiring.simulator.aws;

import static org.assertj.core.api.Assertions.assertThat;

import com.acquiring.simulator.adapter.aws.AtomicClaimGateway;
import com.acquiring.simulator.adapter.aws.AtomicClaimGateway.ClaimCommit;
import com.acquiring.simulator.adapter.aws.AtomicClaimGateway.Outcome;
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
import org.junit.jupiter.api.*;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.*;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

/**
 * P1b2b safe adapter coverage against DynamoDB Local: exactly one composed claim transaction,
 * strongly consistent ambiguous-commit recovery, and no duplicate reservation on retry/replay.
 *
 * <p>The gateway is not production-wired. Production defaulting, slot-budget approval and the
 * public journalVersion/internal version relation remain open (see version-conflict.md).
 */
@Testcontainers
class DynamoAtomicClaimRecoveryIntegrationTest {

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

    @Test void ambiguousOutcomeAfterActualCommitIsRecoveredByStableTokenLookup() {
        AuthorizationRecord claim = claim();
        EventSubmission submission = claimSubmission();
        AtomicClaimGateway gateway = gateway(ambiguousClient(true), claim);

        ClaimCommit commit = gateway.commit(submission);

        assertThat(commit.outcome()).isEqualTo(Outcome.RECOVERED);
        assertThat(commit.committed()).isTrue();
        assertThat(commit.appendResult().decision())
                .isEqualTo(EventDeduplication.Decision.EXACT_REPLAY);
        assertThat(commit.recoveredEvent().sourceSequence()).isEqualTo(1);
        // Ambiguous recovery reads the committed version recorded with the stored event.
        assertThat(commit.recoveredEvent().journalVersion()).isEqualTo(1L);
        assertThat(commit.recoveredEvent().stateAfter())
                .isEqualTo(ClaimCriticalComposer.CLAIM_STATE_AFTER);
        assertThat(repository.readVersionEvidence(AUTHORIZATION_ID).agreesWith(0L, 1L)).isTrue();

        // Exactly one claim exists, with one slot reservation and no dispatch side effect.
        assertThat(eventRows()).hasSize(1);
        assertThat(store.head(CONTEXT).orElseThrow().lastSequence()).isEqualTo(1);
        assertThat(slotRow().get("usedSlots").n()).isEqualTo("1");
        AuthorizationRecord persisted = repository.findById(AUTHORIZATION_ID).orElseThrow();
        assertThat(persisted.state()).isEqualTo(AuthorizationState.AWAITING);
        assertThat(persisted.dispatchedAt()).isNull();
        // No silent version migration: the persisted CAS base is untouched by this slice.
        assertThat(rawJournalItem("AUTH#" + AUTHORIZATION_ID, "META").get("version").n())
                .isEqualTo("0");
    }

    @Test void ambiguousOutcomeWithoutACommitIsUnknownAndWritesNothing() {
        AuthorizationRecord claim = claim();
        EventSubmission submission = claimSubmission();
        AtomicClaimGateway gateway = gateway(ambiguousClient(false), claim);

        ClaimCommit commit = gateway.commit(submission);

        assertThat(commit.outcome()).isEqualTo(Outcome.UNKNOWN);
        assertThat(commit.committed()).isFalse();
        assertThat(commit.appendResult()).isNull();

        assertThat(eventRows()).isEmpty();
        assertThat(store.head(CONTEXT)).isEmpty();
        assertThat(store.find(submission.dedupeKey())).isEmpty();
        assertThat(slotRow()).isEmpty();
        assertThat(repository.findById(AUTHORIZATION_ID)).isEmpty();
        assertThat(repository.findLatestByBusinessKey(BUSINESS_KEY)).isEmpty();
    }

    @Test void retryAfterRecoveryDoesNotReserveASecondSlotOrDuplicateTheClaim() {
        AuthorizationRecord claim = claim();
        EventSubmission submission = claimSubmission();
        AtomicClaimGateway gateway = gateway(ambiguousClient(true), claim);

        assertThat(gateway.commit(submission).outcome()).isEqualTo(Outcome.RECOVERED);
        ClaimCommit retry = gateway.commit(submission);

        assertThat(retry.outcome()).isEqualTo(Outcome.REPLAY);
        assertThat(retry.appendResult().sourceSequence()).isEqualTo(1);
        assertThat(eventRows()).hasSize(1);
        assertThat(store.head(CONTEXT).orElseThrow().lastSequence()).isEqualTo(1);
        assertThat(slotRow().get("usedSlots").n()).isEqualTo("1");
        assertThat(repository.findById(AUTHORIZATION_ID)).isPresent();
        assertThat(repository.findById(AUTHORIZATION_ID).orElseThrow().dispatchedAt()).isNull();
    }

    @Test void abortedBusinessPreconditionIsNotAmbiguousAndLeavesNoReservation() {
        db.putItem(PutItemRequest.builder().tableName(journalTable)
                .item(Map.of(
                        "pk", text("AUTH#" + AUTHORIZATION_ID),
                        "sk", text("META"),
                        "payload", text("{}"),
                        "version", AttributeValue.builder().n("0").build()))
                .build());
        AuthorizationRecord claim = claim();
        EventSubmission submission = claimSubmission();
        AtomicClaimGateway gateway = gateway(db, claim);

        ClaimCommit commit = gateway.commit(submission);

        assertThat(commit.outcome()).isEqualTo(Outcome.NOT_COMMITTED);
        assertThat(commit.committed()).isFalse();
        assertThat(eventRows()).isEmpty();
        assertThat(store.head(CONTEXT)).isEmpty();
        assertThat(store.find(submission.dedupeKey())).isEmpty();
        assertThat(slotRow()).isEmpty();
        assertThat(repository.findLatestByBusinessKey(BUSINESS_KEY)).isEmpty();
    }

    private AtomicClaimGateway gateway(DynamoDbClient client, AuthorizationRecord claim) {
        DynamoEventStore eventStore = new DynamoEventStore(client, eventTable, MANIFEST,
                new EventValidator(), Clock.fixed(NOW, ZoneOffset.UTC), CriticalWriteComposer.NONE);
        return AtomicClaimGateway.forClaim(eventStore, journalTable, eventTable, claim, CAPACITY);
    }

    /**
     * Delegating client whose first {@code transactWriteItems} reports a simulated ambiguous
     * transport outcome, either after delegating (commit really happened) or before (nothing did).
     */
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

    private EventSubmission claimSubmission() {
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
