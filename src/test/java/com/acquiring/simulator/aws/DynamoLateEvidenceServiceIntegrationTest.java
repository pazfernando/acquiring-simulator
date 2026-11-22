package com.acquiring.simulator.aws;

import static org.assertj.core.api.Assertions.assertThat;

import com.acquiring.simulator.adapter.aws.CriticalWriteComposer;
import com.acquiring.simulator.adapter.aws.DynamoAuthorizationRepository;
import com.acquiring.simulator.adapter.aws.DynamoClaimGateway;
import com.acquiring.simulator.adapter.aws.DynamoDispatchIntentGateway;
import com.acquiring.simulator.adapter.aws.DynamoEventStore;
import com.acquiring.simulator.adapter.aws.DynamoResultGateway;
import com.acquiring.simulator.adapter.aws.LateEvidenceAdmissionGateway;
import com.acquiring.simulator.config.SimulatorProperties;
import com.acquiring.simulator.domain.AuthorizationService;
import com.acquiring.simulator.domain.IdentifierSource;
import com.acquiring.simulator.domain.event.CriticalCapacity;
import com.acquiring.simulator.domain.event.EventContext;
import com.acquiring.simulator.domain.event.EventManifest;
import com.acquiring.simulator.domain.event.EventTableKeys;
import com.acquiring.simulator.domain.event.EventValidator;
import com.acquiring.simulator.domain.event.OperationAManifest;
import com.acquiring.simulator.domain.model.AuthorizationRecord;
import com.acquiring.simulator.domain.model.AuthorizationState;
import com.acquiring.simulator.domain.model.CredentialDecision;
import com.acquiring.simulator.domain.model.CvmIndicator;
import com.acquiring.simulator.domain.model.DecisionSource;
import com.acquiring.simulator.domain.model.DownstreamReply;
import com.acquiring.simulator.domain.model.EntryMode;
import com.acquiring.simulator.domain.model.IssuerDecision;
import com.acquiring.simulator.domain.model.LateEvidenceAdmission;
import com.acquiring.simulator.domain.model.LateEvidenceOutcome;
import com.acquiring.simulator.domain.model.Outcome;
import com.acquiring.simulator.domain.model.RiskIndicator;
import com.acquiring.simulator.domain.model.Route;
import com.acquiring.simulator.domain.model.SimulatedDecisionRequest;
import com.acquiring.simulator.domain.port.ClaimGateway;
import com.acquiring.simulator.domain.port.DispatchIntentGateway;
import com.acquiring.simulator.domain.port.IssuerSimulator;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
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
 * P1b6d service-level late-admission branch against DynamoDB Local. One service call makes exactly
 * one admission attempt; a recorded result is read back with its committed version; repeated calls
 * add no second event, HEAD advance or slot; on-time (delivery-delayed) evidence is routed once to
 * the normal verified-result port; refusal and unknown add nothing.
 *
 * <p>The {@link DownstreamReply} is the explicit internal non-cryptographic seam. No C4/P6
 * verification, no public endpoint, no automatic financial recovery and no runtime wiring are
 * exercised. Capacity is an explicit test example; the O8 numbers remain PROPOSED.
 */
@Testcontainers
class DynamoLateEvidenceServiceIntegrationTest {

    @Container static final GenericContainer<?> DYNAMO = new GenericContainer<>(
            DockerImageName.parse("amazon/dynamodb-local:2.6.1")).withExposedPorts(8000);

    private static final CriticalCapacity CAPACITY = new CriticalCapacity(5, 8);
    private static final EventManifest MANIFEST = OperationAManifest.manifest(CAPACITY);
    private static final EventContext CONTEXT =
            new EventContext("SIM", "ACQ-A", "MERCHANT-A", "TX-001");
    private static final String AUTHORIZATION_ID = "AUTH-001";
    private static final String DISPATCH_CORRELATION = "CORR-SEND-001";
    private static final Instant CLAIMED_AT = Instant.parse("2026-11-18T12:00:00Z");
    private static final Instant DISPATCHED_AT = Instant.parse("2026-11-18T12:00:01Z");
    private static final Instant DEADLINE = Instant.parse("2026-11-18T12:00:03Z");
    private static final Instant LATE_EVIDENCE_AT = Instant.parse("2026-11-18T12:00:05Z");
    private static final Instant DELIVERY_DELAYED_EVIDENCE_AT = Instant.parse("2026-11-18T12:00:02Z");
    private static final Instant DELIVERED_AT = Instant.parse("2026-11-18T12:00:20Z");

    private static final SimulatorProperties PROPERTIES = new SimulatorProperties(
            new SimulatorProperties.Authorization(Duration.ofHours(24), Duration.ofSeconds(2)),
            new SimulatorProperties.Profile(
                    List.of(new SimulatorProperties.AcquirerFixture("ACQ-A",
                            List.of(Route.DOMESTIC))),
                    List.of(new SimulatorProperties.MerchantFixture("MERCHANT-A", "ACQ-A")),
                    List.of(new SimulatorProperties.TerminalFixture("TERMINAL-A", "MERCHANT-A")),
                    List.of(new SimulatorProperties.CredentialRange("CRED-",
                            CredentialDecision.APPROVE, Route.DOMESTIC))));

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
                Clock.fixed(DELIVERED_AT, ZoneOffset.UTC), CriticalWriteComposer.NONE);
    }

    @AfterEach void dropTables() {
        db.deleteTable(b -> b.tableName(eventTable));
        db.deleteTable(b -> b.tableName(journalTable));
    }

    @Test void serviceLateAdmissionCommitsOnceThenReplaysWithoutASecondEventOrSlot() {
        dispatchedIntent();

        LateEvidenceOutcome first = service()
                .submitVerifiedEvidence(AUTHORIZATION_ID, approved());
        assertThat(first.outcome()).isEqualTo(Outcome.APPROVED);
        assertThat(first.status())
                .isEqualTo(LateEvidenceAdmission.Status.COMMITTED);
        assertThat(first.recorded()).isTrue();
        assertThat(first.evidence().agreesWith(2L, 3L)).isTrue();
        assertThat(eventRows()).hasSize(3);
        assertThat(store.head(CONTEXT).orElseThrow().lastSequence()).isEqualTo(3L);
        assertThat(slotRow().get("usedSlots").n()).isEqualTo("3");
        assertThat(repository.readLateEvidenceReceipt(AUTHORIZATION_ID).orElseThrow()
                .deliveryReceiptId()).isEqualTo("SVC-" + AUTHORIZATION_ID);

        LateEvidenceOutcome replay = service()
                .submitVerifiedEvidence(AUTHORIZATION_ID, approved());
        assertThat(replay.outcome()).isEqualTo(Outcome.APPROVED);
        assertThat(replay.status())
                .isEqualTo(LateEvidenceAdmission.Status.REPLAY);
        assertThat(replay.recorded()).isTrue();
        assertThat(replay.evidence().agreesWith(2L, 3L)).isTrue();
        // No second event, HEAD advance, receipt replacement or slot reservation.
        assertThat(eventRows()).hasSize(3);
        assertThat(store.head(CONTEXT).orElseThrow().lastSequence()).isEqualTo(3L);
        assertThat(slotRow().get("usedSlots").n()).isEqualTo("3");
        assertThat(repository.readLateEvidenceReceipt(AUTHORIZATION_ID).orElseThrow()
                .deliveryReceiptId()).isEqualTo("SVC-" + AUTHORIZATION_ID);
    }

    @Test void serviceConflictPreservesTheOriginalLateOutcomeAndWritesNothing() {
        dispatchedIntent();
        assertThat(service().submitVerifiedEvidence(AUTHORIZATION_ID, approved()).status())
                .isEqualTo(LateEvidenceAdmission.Status.COMMITTED);

        LateEvidenceOutcome conflict = service()
                .submitVerifiedEvidence(AUTHORIZATION_ID, declined());

        assertThat(conflict.status()).isEqualTo(
                LateEvidenceAdmission.Status.CONTENT_CONFLICT);
        assertThat(conflict.outcome()).isEqualTo(Outcome.APPROVED);
        assertThat(conflict.confirmedState()).isNull();
        AuthorizationRecord persisted = repository.findById(AUTHORIZATION_ID).orElseThrow();
        assertThat(persisted.state()).isEqualTo(AuthorizationState.APPROVED);
        assertThat(persisted.decisionSource()).isEqualTo(DecisionSource.ISSUER);
        assertThat(persisted.outcomeDeterminedAt()).isEqualTo(LATE_EVIDENCE_AT);
        assertThat(repository.readVersionEvidence(AUTHORIZATION_ID).agreesWith(2L, 3L)).isTrue();
        assertThat(eventRows()).hasSize(3);
        assertThat(store.head(CONTEXT).orElseThrow().lastSequence()).isEqualTo(3L);
        assertThat(slotRow().get("usedSlots").n()).isEqualTo("3");
    }

    @Test void serviceRoutesOnTimeEvidenceThroughTheNormalResultPortOnce() {
        dispatchedIntent();

        LateEvidenceOutcome first = service()
                .submitVerifiedEvidence(AUTHORIZATION_ID, deliveryDelayed());
        assertThat(first.outcome()).isEqualTo(Outcome.APPROVED);
        assertThat(first.status())
                .isEqualTo(LateEvidenceAdmission.Status.COMMITTED);
        assertThat(first.recorded()).isTrue();
        assertThat(first.evidence().agreesWith(2L, 3L)).isTrue();
        assertThat(eventRows()).hasSize(3);
        assertThat(store.head(CONTEXT).orElseThrow().lastSequence()).isEqualTo(3L);
        assertThat(slotRow().get("usedSlots").n()).isEqualTo("3");
        // The normal verified-result path, not the late composer, wrote the result.
        assertThat(repository.readLateEvidenceReceipt(AUTHORIZATION_ID)).isEmpty();

        LateEvidenceOutcome duplicate = service()
                .submitVerifiedEvidence(AUTHORIZATION_ID, deliveryDelayed());
        assertThat(duplicate.outcome()).isEqualTo(Outcome.APPROVED);
        assertThat(duplicate.status()).isEqualTo(
                LateEvidenceAdmission.Status
                        .NORMAL_RESULT_ADMISSION);
        assertThat(eventRows()).hasSize(3);
        assertThat(slotRow().get("usedSlots").n()).isEqualTo("3");
    }

    @Test void serviceRefusalWritesNothing() {
        dispatchedIntent();

        LateEvidenceOutcome refused = service().submitVerifiedEvidence(AUTHORIZATION_ID,
                new DownstreamReply("CORR-FOREIGN", IssuerDecision.APPROVED, LATE_EVIDENCE_AT));

        assertThat(refused.outcome()).isEqualTo(Outcome.NOT_CONFIRMED);
        assertThat(refused.status()).isEqualTo(
                LateEvidenceAdmission.Status.REFUSED);
        assertThat(refused.recorded()).isFalse();
        assertThat(eventRows()).hasSize(2);
        assertThat(store.head(CONTEXT).orElseThrow().lastSequence()).isEqualTo(2L);
        assertThat(slotRow().get("usedSlots").n()).isEqualTo("2");
        assertThat(repository.findById(AUTHORIZATION_ID).orElseThrow().state())
                .isEqualTo(AuthorizationState.AWAITING);
        assertThat(repository.readLateEvidenceReceipt(AUTHORIZATION_ID)).isEmpty();
    }

    @Test void serviceAbsentRecordIsUnknownAndWritesNothing() {
        LateEvidenceOutcome unknown = service()
                .submitVerifiedEvidence("AUTH-MISSING", approved());

        assertThat(unknown.outcome()).isEqualTo(Outcome.NOT_CONFIRMED);
        assertThat(unknown.status()).isEqualTo(
                LateEvidenceAdmission.Status.UNKNOWN);
        assertThat(unknown.recorded()).isFalse();
        assertThat(eventRows()).isEmpty();
        assertThat(slotRow()).isEmpty();
    }

    private AuthorizationService service() {
        return new AuthorizationService(repository, new NoDownstream(), PROPERTIES,
                Clock.fixed(DELIVERED_AT, ZoneOffset.UTC), unusedClaim(), unusedIntent(),
                new DynamoResultGateway(store, repository, journalTable, eventTable, CAPACITY,
                        "SIM", "BOOT-P1B6D"),
                null,
                LateEvidenceAdmissionGateway.over(store, repository, journalTable, eventTable,
                        CAPACITY, "SIM", "BOOT-P1B6D"),
                IdentifierSource.randomHex());
    }

    private ClaimGateway unusedClaim() {
        return (record, correlationId, now) -> {
            throw new AssertionError("late evidence must not claim");
        };
    }

    private DispatchIntentGateway unusedIntent() {
        return (claim, correlationId, now) -> {
            throw new AssertionError("late evidence must not dispatch");
        };
    }

    private AuthorizationRecord dispatchedIntent() {
        AuthorizationRecord claim = claim();
        ClaimGateway.Decision claimed = new DynamoClaimGateway(store, repository, journalTable,
                eventTable, CAPACITY, "SIM", "BOOT-P1B6D")
                .claim(claim, "CORR-CLAIM-001", CLAIMED_AT);
        assertThat(claimed.status()).isEqualTo(ClaimGateway.Status.CLAIMED);

        DispatchIntentGateway.Decision intent = new DynamoDispatchIntentGateway(store, repository,
                journalTable, eventTable, CAPACITY, "SIM", "BOOT-P1B6D")
                .dispatchIntent(claim, DISPATCH_CORRELATION, DISPATCHED_AT);
        assertThat(intent.status()).isEqualTo(DispatchIntentGateway.Status.COMMITTED);
        assertThat(intent.evidence().agreesWith(1L, 2L)).isTrue();
        return claim.withDispatch(DISPATCH_CORRELATION, DISPATCHED_AT);
    }

    private static DownstreamReply approved() {
        return new DownstreamReply(DISPATCH_CORRELATION, IssuerDecision.APPROVED, LATE_EVIDENCE_AT);
    }

    private static DownstreamReply declined() {
        return new DownstreamReply(DISPATCH_CORRELATION, IssuerDecision.DECLINED, LATE_EVIDENCE_AT);
    }

    private static DownstreamReply deliveryDelayed() {
        return new DownstreamReply(DISPATCH_CORRELATION, IssuerDecision.APPROVED,
                DELIVERY_DELAYED_EVIDENCE_AT);
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

    private static final class NoDownstream implements IssuerSimulator {
        @Override public DownstreamReply decide(SimulatedDecisionRequest request) {
            throw new AssertionError("late evidence must never call the downstream");
        }
    }
}
