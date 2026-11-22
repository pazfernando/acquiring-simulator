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
import com.acquiring.simulator.adapter.aws.EvidenceReceiptCriticalComposer;
import com.acquiring.simulator.adapter.aws.PossibleSendCriticalComposer;
import com.acquiring.simulator.adapter.aws.ResultCriticalComposer;
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
 * P2b5a atomic normal verified-result receipt against DynamoDB Local.
 *
 * <p>Each positive case builds the real persisted {@code 0100} and received {@code 0110} octets,
 * signs the fixture's exact attestation with an ephemeral in-memory Ed25519 key, runs the real
 * DIRECT verifier and the real P6 binder, then composes the confirmed record, committed version,
 * critical event, HEAD, dedupe, slot and normalized evidence receipt in one transaction.</p>
 *
 * <p>This proves local transaction mechanics only. No service/HTTP caller, late envelope,
 * key custody, runtime wiring or AWS effect is exercised. Ephemeral test keys are never written
 * or logged.</p>
 */
@Testcontainers
class EvidenceReceiptAtomicIntegrationTest {

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
    private static final String INSTANCE = "BOOT-P2B5A";
    private static final Instant CLAIMED_AT = Instant.parse("2026-11-18T12:00:00Z");
    private static final Instant DISPATCHED_AT = Instant.parse("2026-11-18T12:00:01Z");
    private static final Instant EVIDENCE_AT = Instant.parse("2026-11-18T12:00:01Z");

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
                Clock.fixed(EVIDENCE_AT, ZoneOffset.UTC), CriticalWriteComposer.NONE);
    }

    @AfterEach void dropTables() {
        db.deleteTable(b -> b.tableName(eventTable));
        db.deleteTable(b -> b.tableName(journalTable));
    }

    @Test void verifiedApprovedResultCommitsReceiptRecordEventHeadDedupeAndSlotInOneTransaction()
            throws Exception {
        Dispatched dispatched = awaitDispatchedIntent();
        Chain chain = approvedChain(dispatched.intent());

        EventAppendResult result = store.append(chain.submission(), chain.composer());

        assertThat(result.decision()).isEqualTo(EventDeduplication.Decision.NEW);
        assertThat(result.sourceSequence()).isEqualTo(3);

        AuthorizationRecord persisted = repository.findById(AUTHORIZATION_ID).orElseThrow();
        assertThat(persisted.state()).isEqualTo(AuthorizationState.APPROVED);
        assertThat(persisted.decisionSource()).isEqualTo(DecisionSource.ISSUER);
        assertThat(persisted.outcomeDeterminedAt()).isEqualTo(EVIDENCE_AT);
        assertThat(persisted.dispatchCorrelationId()).isEqualTo(DISPATCH_CORRELATION);

        Map<String, AttributeValue> record = rawJournalItem("AUTH#" + AUTHORIZATION_ID, "META");
        assertThat(record.get("version").n()).isEqualTo("2");
        assertThat(record.get("committedJournalVersion").n()).isEqualTo("3");

        assertThat(eventRows()).hasSize(3);
        assertThat(store.head(CONTEXT).orElseThrow().lastSequence()).isEqualTo(3);
        assertThat(slotRow().get("usedSlots").n()).isEqualTo("3");

        ProcessorEvidenceReceipt receipt = repository
                .readEvidenceReceipt(ENV, ISSUER, "EVID-001").orElseThrow();
        assertThat(receipt.identityKey()).isEqualTo("EVID#SPEC-ENV#ISSUER-SIM#EVID-001");
        assertThat(receipt.identitySha256()).hasSize(64);
        assertThat(receipt.canonicalAttestationSha256())
                .isEqualTo(chain.signed().canonicalSha256())
                .isEqualTo(chain.expectedContentSha256());
        assertThat(receipt.instructionSha256()).isEqualTo(chain.signed().instructionSha256());
        assertThat(receipt.responseSha256()).isEqualTo(chain.signed().responseSha256());
        assertThat(receipt.authorizationId()).isEqualTo(AUTHORIZATION_ID);
        assertThat(receipt.transactionId()).isEqualTo(TRANSACTION_ID);
        assertThat(receipt.keyId()).isEqualTo(chain.signed().keyId());
        assertThat(receipt.attestationType())
                .isEqualTo(chain.verified().attestationType().name());
        assertThat(receipt.eventId()).isEqualTo("APPROVED-" + AUTHORIZATION_ID);
        assertThat(receipt.journalVersion()).isEqualTo(3L);
        assertThat(receipt.stateAfter()).isEqualTo("APPROVED");
        assertThat(receipt.evidenceAvailableAt()).isEqualTo(EVIDENCE_AT);
        assertThat(receiptRows()).hasSize(1);
    }

    @Test void tamperedOrMismatchedEvidenceIsRefusedBeforeAnyWrite() throws Exception {
        Dispatched dispatched = awaitDispatchedIntent();
        Chain chain = approvedChain(dispatched.intent());

        // A decision that does not belong to the verified evidence is refused at construction.
        C4P6BindingDecision otherAmount = new C4P6BindingDecision(
                chain.decision().evidenceId(),
                chain.decision().authorizationId(),
                chain.decision().transactionId(),
                1001L,
                chain.decision().currency(),
                chain.decision().de39(),
                chain.decision().result(),
                chain.decision().reason(),
                chain.decision().authorizationReference(),
                chain.decision().scope());
        assertThatThrownBy(() -> EvidenceReceiptCriticalComposer.withSlotAccounting(
                journalTable, eventTable, dispatched.intent(), chain.verified(),
                chain.signed(), otherAmount, CAPACITY))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("amountMinor");

        // The verified result cannot be paired with different signed content.
        C4EvidenceAttestation differentContent = copy(
                chain.signed(), chain.signed().amountMinor() + 1);
        assertThatThrownBy(() -> EvidenceReceiptCriticalComposer.withSlotAccounting(
                journalTable, eventTable, dispatched.intent(), chain.verified(),
                differentContent, chain.decision(), CAPACITY))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("canonicalAttestationSha256");

        // Nothing was written: the dispatch intent is intact, with no receipt row at all.
        assertThat(repository.readEvidenceReceipt(ENV, ISSUER, "EVID-001")).isEmpty();
        assertThat(eventRows()).hasSize(2);
        assertThat(store.head(CONTEXT).orElseThrow().lastSequence()).isEqualTo(2);
        assertThat(slotRow().get("usedSlots").n()).isEqualTo("2");
        AuthorizationRecord persisted = repository.findById(AUTHORIZATION_ID).orElseThrow();
        assertThat(persisted.state()).isEqualTo(AuthorizationState.AWAITING);
        assertThat(persisted.outcomeDeterminedAt()).isNull();
        Map<String, AttributeValue> record = rawJournalItem("AUTH#" + AUTHORIZATION_ID, "META");
        assertThat(record.get("version").n()).isEqualTo("1");
        assertThat(record.get("committedJournalVersion").n()).isEqualTo("2");
    }

    @Test void exactReceiptReplayPreservesReceiptVersionEventHeadAndSlot() throws Exception {
        Dispatched dispatched = awaitDispatchedIntent();
        Chain chain = approvedChain(dispatched.intent());

        store.append(chain.submission(), chain.composer());
        ProcessorEvidenceReceipt first =
                repository.readEvidenceReceipt(ENV, ISSUER, "EVID-001").orElseThrow();

        EventAppendResult replay = store.append(chain.submission(), chain.composer());

        assertThat(replay.decision()).isEqualTo(EventDeduplication.Decision.EXACT_REPLAY);
        assertThat(replay.sourceSequence()).isEqualTo(3);
        assertThat(eventRows()).hasSize(3);
        assertThat(store.head(CONTEXT).orElseThrow().lastSequence()).isEqualTo(3);
        assertThat(slotRow().get("usedSlots").n()).isEqualTo("3");
        assertThat(rawJournalItem("AUTH#" + AUTHORIZATION_ID, "META").get("version").n())
                .isEqualTo("2");
        ProcessorEvidenceReceipt second =
                repository.readEvidenceReceipt(ENV, ISSUER, "EVID-001").orElseThrow();
        assertThat(second).isEqualTo(first);
        assertThat(receiptRows()).hasSize(1);
    }

    @Test void failingBusinessConditionCommitsNoReceiptRecordEventHeadOrSlot() throws Exception {
        Dispatched dispatched = awaitDispatchedIntent();
        Chain chain = approvedChain(dispatched.intent());

        // A competing writer establishes a determined outcome after the intent was read.
        repository.updateOutcome(AUTHORIZATION_ID, AuthorizationState.DECLINED.name(),
                DecisionSource.ISSUER.name(), null, EVIDENCE_AT);

        assertThatThrownBy(() -> store.append(chain.submission(), chain.composer()))
                .isInstanceOf(TransactionCanceledException.class);

        assertThat(repository.readEvidenceReceipt(ENV, ISSUER, "EVID-001")).isEmpty();
        assertThat(eventRows()).hasSize(2);
        assertThat(store.head(CONTEXT).orElseThrow().lastSequence()).isEqualTo(2);
        assertThat(slotRow().get("usedSlots").n()).isEqualTo("2");
        AuthorizationRecord persisted = repository.findById(AUTHORIZATION_ID).orElseThrow();
        assertThat(persisted.state()).isEqualTo(AuthorizationState.DECLINED);
        assertThat(persisted.decisionSource()).isEqualTo(DecisionSource.ISSUER);
    }

    // --- Verified/bound evidence chain ---------------------------------------

    private record Dispatched(AuthorizationRecord intent) {
    }

    private record Chain(
            AuthorizationRecord intent,
            C4EvidenceAttestation signed,
            String expectedContentSha256,
            C4VerifiedAttestation verified,
            C4P6BindingDecision decision,
            EventSubmission submission,
            EvidenceReceiptCriticalComposer composer) {
    }

    private Dispatched awaitDispatchedIntent() {
        AuthorizationRecord claim = claim();
        store.append(claimSubmission(claim),
                ClaimCriticalComposer.withSlotAccounting(journalTable, eventTable, claim, CAPACITY));
        store.append(PossibleSendCriticalComposer.submission(claim, DISPATCH_CORRELATION, ENV,
                        INSTANCE, DISPATCHED_AT),
                PossibleSendCriticalComposer.withSlotAccounting(journalTable, eventTable, claim,
                        DISPATCH_CORRELATION, DISPATCHED_AT, CAPACITY));
        assertThat(repository.readVersionEvidence(AUTHORIZATION_ID).agreesWith(1L, 2L)).isTrue();
        return new Dispatched(claim.withDispatch(DISPATCH_CORRELATION, DISPATCHED_AT));
    }

    private Chain approvedChain(AuthorizationRecord intent) throws Exception {
        JsonNode evidence = evidence("EF:V02-approved-response");
        byte[] original = originalBytes(evidence);
        byte[] response = responseBytes("V02-approved-response");
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

        DownstreamReply reply = new DownstreamReply(
                signed.correlationId(), IssuerDecision.APPROVED, Instant.parse(signed.availableAt()));
        EventSubmission submission =
                ResultCriticalComposer.submission(intent, reply, ENV, INSTANCE);
        EvidenceReceiptCriticalComposer composer =
                EvidenceReceiptCriticalComposer.withSlotAccounting(journalTable, eventTable,
                        intent, verified, signed, decision, CAPACITY);
        return new Chain(intent, signed, evidence.get("attestedContentSha256").asText(), verified,
                decision, submission, composer);
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

    private static C4EvidenceAttestation copy(C4EvidenceAttestation a, int amountMinor) {
        return new C4EvidenceAttestation(
                a.schemaVersion(), a.environmentId(), a.producerPrincipal(), a.evidenceId(),
                a.instructionSha256(), a.profile(), a.authorizationId(), a.transactionId(),
                a.correlationId(), a.instructionAttemptId(), a.acquirerRef(), a.merchantRef(),
                a.terminalRef(), a.paymentRef(), a.generation(), amountMinor, a.currency(),
                a.STAN(), a.RRN(), a.DE7(), a.sent(), a.actor(), a.stage(), a.DE39(),
                a.result(), a.reason(), a.authorizationReference(), a.availableAt(),
                a.signingPrincipal(), a.keyId(), a.responseSha256());
    }

    private static byte[] originalBytes(JsonNode evidence) {
        return java.util.HexFormat.of().parseHex(evidence.get("original0100Hex").asText());
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
