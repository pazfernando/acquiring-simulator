package com.acquiring.simulator.channel;

import static com.acquiring.simulator.channel.C4EvidenceFixtures.ISSUER;
import static com.acquiring.simulator.channel.C4EvidenceFixtures.keyPair;
import static com.acquiring.simulator.channel.C4EvidenceFixtures.registry;
import static com.acquiring.simulator.channel.C4EvidenceFixtures.trusted;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.acquiring.simulator.adapter.aws.ClaimCriticalComposer;
import com.acquiring.simulator.adapter.aws.CriticalWriteComposer;
import com.acquiring.simulator.adapter.aws.DynamoAuthorizationRepository;
import com.acquiring.simulator.adapter.aws.DynamoEventStore;
import com.acquiring.simulator.adapter.aws.LateEvidenceCriticalComposer;
import com.acquiring.simulator.adapter.aws.LateSignedEvidenceReceiptComposer;
import com.acquiring.simulator.adapter.aws.PossibleSendCriticalComposer;
import com.acquiring.simulator.domain.event.CanonicalEvent;
import com.acquiring.simulator.domain.event.CanonicalEventJson;
import com.acquiring.simulator.domain.event.CriticalCapacity;
import com.acquiring.simulator.domain.event.EventAppendResult;
import com.acquiring.simulator.domain.event.EventContext;
import com.acquiring.simulator.domain.event.EventDeduplication;
import com.acquiring.simulator.domain.event.EventDurability;
import com.acquiring.simulator.domain.event.EventManifest;
import com.acquiring.simulator.domain.event.EventSubmission;
import com.acquiring.simulator.domain.event.EventTableKeys;
import com.acquiring.simulator.domain.event.EventValidator;
import com.acquiring.simulator.domain.event.LateEvidenceReceipt;
import com.acquiring.simulator.domain.event.OperationAManifest;
import com.acquiring.simulator.domain.event.ProcessorEvidenceReceipt;
import com.acquiring.simulator.domain.model.AuthorizationRecord;
import com.acquiring.simulator.domain.model.AuthorizationState;
import com.acquiring.simulator.domain.model.CvmIndicator;
import com.acquiring.simulator.domain.model.DecisionSource;
import com.acquiring.simulator.domain.model.DownstreamReply;
import com.acquiring.simulator.domain.model.EntryMode;
import com.acquiring.simulator.domain.model.IssuerDecision;
import com.acquiring.simulator.domain.model.RiskIndicator;
import com.acquiring.simulator.domain.model.Route;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
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
import software.amazon.awssdk.services.dynamodb.model.TransactionCanceledException;

/**
 * P2b6a1 late signed-evidence receipt composition against DynamoDB Local (observation contract
 * O3/P5; channel contract C4/P6 late-delivery paragraph; OBS-08/OBS-10).
 *
 * <p>Each case builds the real fixture {@code 0100}, signs the exact late {@code 0110} attestation
 * with an ephemeral in-memory Ed25519 key, runs the real DIRECT verifier and the real P6 binder,
 * then composes the confirmed record, late receipt metadata, committed version, critical event,
 * HEAD, dedupe, slot and normalized processor receipt in one transaction through the wrapper.</p>
 *
 * <p>This proves local atomic composition only. No HTTP/service caller, conflict/replay policy,
 * key custody, runtime wiring or AWS effect is exercised here. Ephemeral keys are never written or
 * logged.</p>
 */
@Testcontainers
class LateSignedEvidenceReceiptAtomicIntegrationTest {

    @Container static final GenericContainer<?> DYNAMO = new GenericContainer<>(
            DockerImageName.parse("amazon/dynamodb-local:2.6.1")).withExposedPorts(8000);

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Path PACKED =
            Path.of("docs/ai-sdlc/work-items/WI-ACQ-OBS-001/vectors/packed-vectors.json");
    private static final Path EVIDENCE =
            Path.of("docs/ai-sdlc/work-items/WI-ACQ-OBS-001/vectors/evidence-fixtures.json");
    private static final Path EVENT_VECTORS =
            Path.of("docs/ai-sdlc/work-items/WI-ACQ-OBS-001/vectors/event-vectors.json");

    private static final String ENV = "SPEC-ENV";
    private static final String AUTHORIZATION_ID = "AUTH-001";
    private static final String TRANSACTION_ID = "TX-001";
    private static final String DISPATCH_CORRELATION = "CORR-001";
    private static final String BUSINESS_KEY = "ACQ-A|MERCHANT-A|TX-001";
    private static final String INSTANCE = "BOOT-P2B6A1";
    private static final String RECEIPT_ID = "LATE-RECEIPT-001";
    private static final Instant CLAIMED_AT = Instant.parse("2026-11-18T12:00:00Z");
    private static final Instant DISPATCHED_AT = Instant.parse("2026-11-18T12:00:01Z");
    private static final Instant DEADLINE = Instant.parse("2026-11-18T12:00:03Z");
    private static final Instant LATE_EVIDENCE_AT = Instant.parse("2026-11-18T12:00:05Z");
    private static final Instant LATE_DELIVERED_AT = Instant.parse("2026-11-18T12:00:20Z");

    /** Test-only example capacity; owner approval of the O8 numbers is still pending. */
    private static final CriticalCapacity CAPACITY = new CriticalCapacity(5, 8);
    private static final EventManifest MANIFEST = OperationAManifest.manifest(CAPACITY);
    private static final EventContext CONTEXT =
            new EventContext(ENV, "ACQ-A", "MERCHANT-A", TRANSACTION_ID);

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

    @Test void lateSignedEvidenceCommitsReceiptLateMetadataRecordEventHeadDedupeAndSlotAtOnce()
            throws Exception {
        AuthorizationRecord intent = awaitDispatchedIntent();
        Chain chain = lateChain(intent);

        EventAppendResult result = store.append(chain.submission(), chain.composer());

        assertThat(result.decision()).isEqualTo(EventDeduplication.Decision.NEW);
        assertThat(result.sourceSequence()).isEqualTo(3);

        AuthorizationRecord persisted = repository.findById(AUTHORIZATION_ID).orElseThrow();
        assertThat(persisted.state()).isEqualTo(AuthorizationState.APPROVED);
        assertThat(persisted.decisionSource()).isEqualTo(DecisionSource.ISSUER);
        assertThat(persisted.outcomeDeterminedAt()).isEqualTo(LATE_EVIDENCE_AT);

        // One record write: CAS advance, explicit committed version and late receipt metadata.
        Map<String, AttributeValue> record = rawJournalItem("AUTH#" + AUTHORIZATION_ID, "META");
        assertThat(record.get("version").n()).isEqualTo("2");
        assertThat(record.get("committedJournalVersion").n()).isEqualTo("3");
        assertThat(record.get("lateEvidence").bool()).isTrue();
        assertThat(repository.readLateEvidenceReceipt(AUTHORIZATION_ID).orElseThrow())
                .isEqualTo(new LateEvidenceReceipt(RECEIPT_ID, LATE_EVIDENCE_AT,
                        LATE_DELIVERED_AT, DEADLINE, true));

        assertThat(eventRows()).hasSize(3);
        assertThat(store.head(CONTEXT).orElseThrow().lastSequence()).isEqualTo(3);
        assertThat(slotRow().get("usedSlots").n()).isEqualTo("3");
        assertThat(eventRows()).filteredOn(row -> "APPROVED-AUTH-001"
                .equals(row.get("eventId").s())).hasSize(1);

        // The normalized P5 receipt is part of the same committed transaction, not a second write.
        ProcessorEvidenceReceipt receipt = repository
                .readEvidenceReceipt(ENV, ISSUER, "EVID-001").orElseThrow();
        assertThat(receipt.identityKey()).isEqualTo("EVID#SPEC-ENV#ISSUER-SIM#EVID-001");
        assertThat(receipt.canonicalAttestationSha256())
                .isEqualTo(chain.signed().canonicalSha256())
                .isEqualTo(chain.expectedContentSha256());
        assertThat(receipt.eventId()).isEqualTo("APPROVED-" + AUTHORIZATION_ID);
        assertThat(receipt.journalVersion()).isEqualTo(3L);
        assertThat(receipt.stateAfter()).isEqualTo("APPROVED");
        assertThat(receipt.evidenceAvailableAt()).isEqualTo(LATE_EVIDENCE_AT);
        assertThat(receiptRows()).hasSize(1);
    }

    @Test void failingBusinessConditionCommitsNoLateReceiptRecordEventHeadOrSlot() throws Exception {
        AuthorizationRecord intent = awaitDispatchedIntent();
        Chain chain = lateChain(intent);

        // A competing writer establishes a determined outcome after the intent was read.
        repository.updateOutcome(AUTHORIZATION_ID, AuthorizationState.DECLINED.name(),
                DecisionSource.ISSUER.name(), null, LATE_EVIDENCE_AT);

        assertThatThrownBy(() -> store.append(chain.submission(), chain.composer()))
                .isInstanceOf(TransactionCanceledException.class);

        // Nothing from the late transaction survives: no receipt row, no late metadata, no event,
        // no HEAD advance and no slot. The competing outcome is unchanged.
        assertThat(repository.readEvidenceReceipt(ENV, ISSUER, "EVID-001")).isEmpty();
        assertThat(receiptRows()).isEmpty();
        Map<String, AttributeValue> record = rawJournalItem("AUTH#" + AUTHORIZATION_ID, "META");
        // Only the competing outcome advanced the storage CAS; the late write added nothing.
        assertThat(record.get("version").n()).isEqualTo("2");
        assertThat(record.get("committedJournalVersion").n()).isEqualTo("2");
        assertThat(record.get("lateEvidence")).isNull();
        assertThat(repository.readLateEvidenceReceipt(AUTHORIZATION_ID)).isEmpty();
        assertThat(eventRows()).hasSize(2);
        assertThat(store.head(CONTEXT).orElseThrow().lastSequence()).isEqualTo(2L);
        assertThat(slotRow().get("usedSlots").n()).isEqualTo("2");
        AuthorizationRecord persisted = repository.findById(AUTHORIZATION_ID).orElseThrow();
        assertThat(persisted.state()).isEqualTo(AuthorizationState.DECLINED);
        assertThat(persisted.decisionSource()).isEqualTo(DecisionSource.ISSUER);
    }

    // --- Verified/bound late evidence chain -----------------------------------

    private record Chain(
            C4EvidenceAttestation signed,
            String expectedContentSha256,
            EventSubmission submission,
            LateSignedEvidenceReceiptComposer composer) {
    }

    private Chain lateChain(AuthorizationRecord intent) throws Exception {
        JsonNode evidence = evidence("EF:V08-late-approval");
        byte[] original = java.util.HexFormat.of()
                .parseHex(evidence.get("original0100Hex").asText());
        byte[] response = responseBytes("V08-late-approval");
        C4EvidenceAttestation signed = attestation(evidence.get("attestedFields"));

        KeyPair key = keyPair();
        C4CarrierEnvelope envelope = C4EvidenceFixtures.direct(signed, key.getPrivate());
        C4AuthenticatedTransportContext context =
                new C4AuthenticatedTransportContext(signed.environmentId(),
                        signed.signingPrincipal());
        C4VerifiedAttestation verified = C4EvidenceVerifier.verify(
                C4EvidenceFixtures.candidate(envelope, response, signed.signingPrincipal()),
                context,
                registry(trusted(signed.signingPrincipal(), signed.keyId(),
                        C4PrincipalRole.ISSUER_SIM, key.getPublic(), signed.acquirerRef())));
        C4P6BindingDecision decision =
                C4P6SignedFieldBinder.bind(verified, signed, context, original, response);

        DownstreamReply reply = new DownstreamReply(signed.correlationId(),
                IssuerDecision.APPROVED, Instant.parse(signed.availableAt()));
        EventSubmission submission =
                LateSignedEvidenceReceiptComposer.submission(intent, reply, ENV, INSTANCE);
        LateSignedEvidenceReceiptComposer composer =
                LateSignedEvidenceReceiptComposer.withSlotAccounting(journalTable, eventTable,
                        intent, verified, signed, decision, DEADLINE, LATE_DELIVERED_AT,
                        RECEIPT_ID, CAPACITY);
        assertThat(composer.timing())
                .isEqualTo(LateEvidenceCriticalComposer.Timing.LATE);
        return new Chain(signed, evidence.get("attestedContentSha256").asText(), submission,
                composer);
    }

    private AuthorizationRecord awaitDispatchedIntent() {
        AuthorizationRecord claim = claim();
        store.append(claimSubmission(claim),
                ClaimCriticalComposer.withSlotAccounting(journalTable, eventTable, claim, CAPACITY));
        store.append(PossibleSendCriticalComposer.submission(claim, DISPATCH_CORRELATION, ENV,
                        INSTANCE, DISPATCHED_AT),
                PossibleSendCriticalComposer.withSlotAccounting(journalTable, eventTable, claim,
                        DISPATCH_CORRELATION, DISPATCHED_AT, CAPACITY));
        assertThat(repository.readVersionEvidence(AUTHORIZATION_ID).agreesWith(1L, 2L)).isTrue();
        return claim.withDispatch(DISPATCH_CORRELATION, DISPATCHED_AT);
    }

    private AuthorizationRecord claim() {
        return new AuthorizationRecord(
                AUTHORIZATION_ID,
                BUSINESS_KEY,
                "ACQ-A",
                "MERCHANT-A",
                TRANSACTION_ID,
                1000L,
                "840",
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

    private EventSubmission claimSubmission(AuthorizationRecord claim) {
        return new EventSubmission(CONTEXT, payloadEvent("CLAIM-AUTH-001"),
                EventDurability.CRITICAL_COMMITTED, ClaimCriticalComposer.CLAIM_JOURNAL_VERSION,
                "2026-11-18T12:00:00Z", "2026-11-18T12:00:00Z", "2026-11-18T12:00:00Z",
                ClaimCriticalComposer.CLAIM_STATE_AFTER);
    }

    private static CanonicalEvent payloadEvent(String eventId) {
        try {
            ObjectNode node = (ObjectNode) JSON.readTree(EVENT_VECTORS.toFile())
                    .path("events").get(0).path("producerPayload");
            node.put("eventId", eventId);
            node.put("instanceId", INSTANCE);
            node.put("transactionId", TRANSACTION_ID);
            node.put("operationId", AUTHORIZATION_ID);
            node.put("taskName", "business-persist");
            node.put("status", "SUCCEEDED");
            return CanonicalEventJson.parse(node.toString(), MANIFEST);
        } catch (Exception e) {
            throw new IllegalStateException("cannot read event fixture", e);
        }
    }

    // --- Fixture helpers ------------------------------------------------------

    private static C4EvidenceAttestation attestation(JsonNode fields) {
        JsonNode reference = fields.get("authorizationReference");
        return new C4EvidenceAttestation(
                fields.get("schemaVersion").asInt(),
                fields.get("environmentId").asText(),
                fields.get("producerPrincipal").asText(),
                fields.get("evidenceId").asText(),
                fields.get("instructionSha256").asText(),
                fields.get("profile").asText(),
                fields.get("authorizationId").asText(),
                fields.get("transactionId").asText(),
                fields.get("correlationId").asText(),
                fields.get("instructionAttemptId").asText(),
                fields.get("acquirerRef").asText(),
                fields.get("merchantRef").asText(),
                fields.get("terminalRef").asText(),
                fields.get("paymentRef").asText(),
                fields.get("generation").asInt(),
                fields.get("amountMinor").asInt(),
                fields.get("currency").asText(),
                fields.get("STAN").asText(),
                fields.get("RRN").asText(),
                fields.get("DE7").asText(),
                fields.get("sent").asText(),
                fields.get("actor").asText(),
                fields.get("stage").asText(),
                fields.get("DE39").asText(),
                fields.get("result").asText(),
                fields.get("reason").asText(),
                reference == null || reference.isNull() ? null : reference.asText(),
                fields.get("availableAt").asText(),
                fields.get("signingPrincipal").asText(),
                fields.get("keyId").asText(),
                fields.get("responseSha256").asText());
    }

    private static byte[] responseBytes(String fixtureId) throws Exception {
        Map<String, JsonNode> byId = new HashMap<>();
        for (JsonNode vector : JSON.readTree(Files.readString(PACKED)).get("vectors")) {
            byId.put(vector.get("id").asText(), vector);
        }
        JsonNode found = byId.get(fixtureId);
        if (found == null) {
            throw new IllegalArgumentException("unknown packed vector " + fixtureId);
        }
        return java.util.HexFormat.of().parseHex(found.get("hex").asText());
    }

    private static JsonNode evidence(String id) throws Exception {
        for (JsonNode fixture : JSON.readTree(Files.readString(EVIDENCE)).get("fixtures")) {
            if (id.equals(fixture.get("id").asText())) {
                return fixture;
            }
        }
        throw new IllegalArgumentException("unknown evidence fixture " + id);
    }

    // --- DynamoDB assertions and infrastructure -------------------------------

    private List<Map<String, AttributeValue>> receiptRows() {
        return queryJournal("EVID#" + ENV + "#" + ISSUER + "#EVID-001");
    }

    private List<Map<String, AttributeValue>> queryJournal(String pk) {
        QueryResponse page = db.query(QueryRequest.builder().tableName(journalTable)
                .consistentRead(true)
                .keyConditionExpression("pk = :pk")
                .expressionAttributeValues(Map.of(":pk", text(pk)))
                .build());
        return new ArrayList<>(page.items());
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
