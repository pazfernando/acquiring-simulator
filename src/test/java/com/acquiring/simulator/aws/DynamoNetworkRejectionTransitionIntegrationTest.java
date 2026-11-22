package com.acquiring.simulator.aws;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.acquiring.simulator.adapter.aws.ClaimCriticalComposer;
import com.acquiring.simulator.adapter.aws.CriticalWriteComposer;
import com.acquiring.simulator.adapter.aws.DynamoAuthorizationRepository;
import com.acquiring.simulator.adapter.aws.DynamoEventStore;
import com.acquiring.simulator.adapter.aws.NetworkRejectedCriticalComposer;
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

/**
 * P1b5a explicit NETWORK_REJECTED composer against DynamoDB Local: one conditioned business
 * transition to {@code NETWORK_REJECTED}/{@code NETWORK} provenance, the immutable critical
 * business-persist event and EVENT head/dedupe metadata, and the explicit slot reservation commit
 * together — with no fabricated issuer task.
 *
 * <p>Approved rules this test grounds: guide NET-01 (report the network/processor rejection with
 * provenance, never as an issuer decline), acceptance OBS-03 (no issuer task/decision fabricated),
 * requirements R05/R10/R22 (preserve processor/issuer separation and validate correlation before
 * yielding a result).
 *
 * <p>This proves local transaction mechanics only. No service coupling, no result gateway, no
 * downstream call and no production wiring are exercised; real AWS effects stay unverified.
 */
@Testcontainers
class DynamoNetworkRejectionTransitionIntegrationTest {

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

    @Test void correlatedNetworkRejectionCommitsNetworkProvenanceWithoutIssuerDispatch() {
        AuthorizationRecord intent = dispatchedIntent();

        EventAppendResult result = store.append(rejectionSubmission(intent, networkRejected()),
                rejectionComposer(intent, networkRejected()));

        assertThat(result.decision()).isEqualTo(EventDeduplication.Decision.NEW);
        assertThat(result.sourceSequence()).isEqualTo(3);

        // NET-01 / R05 / R10: the persisted outcome is a network rejection, not an issuer decline.
        AuthorizationRecord persisted = repository.findById(AUTHORIZATION_ID).orElseThrow();
        assertThat(persisted.state()).isEqualTo(AuthorizationState.NETWORK_REJECTED);
        assertThat(persisted.decisionSource()).isEqualTo(DecisionSource.NETWORK);
        assertThat(persisted.reasonCategory())
                .isEqualTo(NetworkRejectedCriticalComposer.NETWORK_REJECTION_REASON);
        assertThat(persisted.outcomeDeterminedAt()).isEqualTo(EVIDENCE_AT);
        assertThat(persisted.dispatchedAt()).isEqualTo(DISPATCHED_AT);
        assertThat(persisted.dispatchCorrelationId()).isEqualTo(DISPATCH_CORRELATION);
        assertThat(persisted.paymentFingerprint()).isEqualTo(intent.paymentFingerprint());

        // One claim, one dispatch intent and one rejection event; HEAD advanced exactly once.
        assertThat(eventRows()).hasSize(3);
        assertThat(store.head(CONTEXT).orElseThrow().lastSequence()).isEqualTo(3);
        assertThat(slotRow().get("usedSlots").n()).isEqualTo("3");
        assertThat(slotRow().get("slotBudget").n()).isEqualTo("8");

        StoredEvent stored = store.find(rejectionSubmission(intent, networkRejected()).dedupeKey())
                .orElseThrow();
        assertThat(stored.sourceSequence()).isEqualTo(3);
        assertThat(stored.journalVersion()).isEqualTo(3L);
        assertThat(stored.stateAfter())
                .isEqualTo(NetworkRejectedCriticalComposer.NETWORK_REJECTED_STATE_AFTER);
        assertThat(eventRows().get(2).get("eventId").s())
                .isEqualTo("NETWORK_REJECTED-AUTH-001");

        // OBS-03: the committed critical event is PROCESSOR business-persist; no issuer work exists.
        assertThat(eventRows()).allSatisfy(row -> {
            var payload = readPayload(row);
            assertThat(payload.path("taskName").asText()).isNotEqualTo("issuer-decide");
            assertThat(payload.path("actor").asText()).isNotEqualTo("ISSUER");
        });
        var rejectionPayload = readPayload(eventRows().get(2));
        assertThat(rejectionPayload.path("taskName").asText()).isEqualTo("business-persist");
        assertThat(rejectionPayload.path("actor").asText()).isEqualTo("PROCESSOR");
        assertThat(rejectionPayload.path("correlationId").asText()).isEqualTo(DISPATCH_CORRELATION);

        // CAS advanced to 2; the committed journal version advanced explicitly to 3.
        Map<String, AttributeValue> record = rawJournalItem("AUTH#" + AUTHORIZATION_ID, "META");
        assertThat(record.get("version").n()).isEqualTo("2");
        assertThat(record.get("committedJournalVersion").n()).isEqualTo("3");
        CommittedVersionEvidence evidence = repository.readVersionEvidence(AUTHORIZATION_ID);
        assertThat(evidence.storageVersion()).isEqualTo(2);
        assertThat(evidence.committedJournalVersion()).isEqualTo(3L);
        assertThat(evidence.agreesWith(2L, 3L)).isTrue();
    }

    @Test void invalidEvidenceOrMissingDispatchIntentWritesNothing() {
        // Precondition: without a durable dispatch intent the rejection is refused before any write.
        AuthorizationRecord claim = claim();
        store.append(claimSubmission(), ClaimCriticalComposer.withSlotAccounting(
                journalTable, eventTable, claim, CAPACITY));
        AuthorizationRecord notDispatched = repository.findById(AUTHORIZATION_ID).orElseThrow();
        assertThatThrownBy(() -> rejectionComposer(notDispatched, networkRejected()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("undispatched-outcome");

        assertThat(eventRows()).hasSize(1);
        assertThat(store.head(CONTEXT).orElseThrow().lastSequence()).isEqualTo(1);
        assertThat(slotRow().get("usedSlots").n()).isEqualTo("1");
        assertThat(repository.findById(AUTHORIZATION_ID).orElseThrow().outcomeDeterminedAt())
                .isNull();

        // Now dispatch the intent; invalid evidence must still commit nothing.
        store.append(PossibleSendCriticalComposer.submission(claim, DISPATCH_CORRELATION, "SIM",
                        "BOOT-P1B5A", DISPATCHED_AT),
                PossibleSendCriticalComposer.withSlotAccounting(journalTable, eventTable, claim,
                        DISPATCH_CORRELATION, DISPATCHED_AT, CAPACITY));
        AuthorizationRecord intent = claim.withDispatch(DISPATCH_CORRELATION, DISPATCHED_AT);
        assertThat(repository.readVersionEvidence(AUTHORIZATION_ID).agreesWith(1L, 2L)).isTrue();

        // R22: uncorrelated, non-network or pre-dispatch evidence never yields a committed result.
        assertThatThrownBy(() -> rejectionComposer(intent,
                new DownstreamReply("CORR-OTHER", IssuerDecision.NETWORK_REJECTED, EVIDENCE_AT)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("correlation");
        assertThatThrownBy(() -> rejectionComposer(intent,
                new DownstreamReply(DISPATCH_CORRELATION, IssuerDecision.APPROVED, EVIDENCE_AT)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("NETWORK_REJECTED decision");
        assertThatThrownBy(() -> rejectionComposer(intent,
                new DownstreamReply(DISPATCH_CORRELATION, IssuerDecision.NETWORK_REJECTED,
                        DISPATCHED_AT.minusSeconds(1))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("predate");

        assertThat(eventRows()).hasSize(2);
        assertThat(store.head(CONTEXT).orElseThrow().lastSequence()).isEqualTo(2);
        assertThat(slotRow().get("usedSlots").n()).isEqualTo("2");
        AuthorizationRecord untouched = repository.findById(AUTHORIZATION_ID).orElseThrow();
        assertThat(untouched.state()).isEqualTo(AuthorizationState.AWAITING);
        assertThat(untouched.decisionSource()).isEqualTo(DecisionSource.NONE);
        assertThat(untouched.outcomeDeterminedAt()).isNull();
        assertThat(rawJournalItem("AUTH#" + AUTHORIZATION_ID, "META").get("version").n())
                .isEqualTo("1");
        assertThat(rawJournalItem("AUTH#" + AUTHORIZATION_ID, "META")
                .get("committedJournalVersion").n()).isEqualTo("2");
    }

    private AuthorizationRecord dispatchedIntent() {
        AuthorizationRecord claim = claim();
        store.append(claimSubmission(), ClaimCriticalComposer.withSlotAccounting(
                journalTable, eventTable, claim, CAPACITY));
        AuthorizationRecord intent = claim.withDispatch(DISPATCH_CORRELATION, DISPATCHED_AT);
        store.append(PossibleSendCriticalComposer.submission(claim, DISPATCH_CORRELATION, "SIM",
                        "BOOT-P1B5A", DISPATCHED_AT),
                PossibleSendCriticalComposer.withSlotAccounting(journalTable, eventTable, claim,
                        DISPATCH_CORRELATION, DISPATCHED_AT, CAPACITY));
        assertThat(repository.readVersionEvidence(AUTHORIZATION_ID).agreesWith(1L, 2L)).isTrue();
        return intent;
    }

    private NetworkRejectedCriticalComposer rejectionComposer(AuthorizationRecord intent,
            DownstreamReply evidence) {
        return NetworkRejectedCriticalComposer.withSlotAccounting(journalTable, eventTable, intent,
                evidence, CAPACITY);
    }

    private EventSubmission rejectionSubmission(AuthorizationRecord intent,
            DownstreamReply evidence) {
        return NetworkRejectedCriticalComposer.submission(intent, evidence, "SIM", "BOOT-P1B5A");
    }

    private static DownstreamReply networkRejected() {
        return new DownstreamReply(DISPATCH_CORRELATION, IssuerDecision.NETWORK_REJECTED,
                EVIDENCE_AT);
    }

    private EventSubmission claimSubmission() {
        return new EventSubmission(CONTEXT, payloadEvent("CLAIM-AUTH-001", "BOOT-P1B5A"),
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

    private com.fasterxml.jackson.databind.JsonNode readPayload(
            Map<String, AttributeValue> row) {
        try {
            return MAPPER.readTree(row.get("payload").s());
        } catch (Exception e) {
            throw new IllegalStateException("cannot parse stored event payload", e);
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
