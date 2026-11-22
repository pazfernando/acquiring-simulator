package com.acquiring.simulator.channel;

import static com.acquiring.simulator.channel.C4EvidenceFixtures.ISSUER;
import static com.acquiring.simulator.channel.C4EvidenceFixtures.keyPair;
import static com.acquiring.simulator.channel.C4EvidenceFixtures.registry;
import static com.acquiring.simulator.channel.C4EvidenceFixtures.trusted;
import static org.assertj.core.api.Assertions.assertThat;

import com.acquiring.simulator.adapter.aws.ClaimCriticalComposer;
import com.acquiring.simulator.adapter.aws.CriticalWriteComposer;
import com.acquiring.simulator.adapter.aws.DynamoAuthorizationRepository;
import com.acquiring.simulator.adapter.aws.DynamoEventStore;
import com.acquiring.simulator.adapter.aws.DynamoOriginalInstructionReader;
import com.acquiring.simulator.adapter.aws.LateEvidenceCriticalComposer;
import com.acquiring.simulator.adapter.aws.LateSignedEvidenceReceiptGateway;
import com.acquiring.simulator.adapter.aws.PossibleSendCriticalComposer;
import com.acquiring.simulator.adapter.aws.ResultCriticalComposer;
import com.acquiring.simulator.adapter.aws.SignedEvidenceAdmissionService;
import com.acquiring.simulator.adapter.aws.SignedEvidenceReceiptGateway;
import com.acquiring.simulator.domain.event.CanonicalEvent;
import com.acquiring.simulator.domain.event.CanonicalEventJson;
import com.acquiring.simulator.domain.event.CriticalCapacity;
import com.acquiring.simulator.domain.event.EventContext;
import com.acquiring.simulator.domain.event.EventManifest;
import com.acquiring.simulator.domain.event.EventSubmission;
import com.acquiring.simulator.domain.event.EventTableKeys;
import com.acquiring.simulator.domain.event.EventValidator;
import com.acquiring.simulator.domain.event.LateEvidenceReceipt;
import com.acquiring.simulator.domain.event.OperationAManifest;
import com.acquiring.simulator.domain.event.ProcessorEvidenceReceipt;
import com.acquiring.simulator.domain.event.SignedEvidenceConflict;
import com.acquiring.simulator.domain.model.AuthorizationRecord;
import com.acquiring.simulator.domain.model.AuthorizationState;
import com.acquiring.simulator.domain.model.CvmIndicator;
import com.acquiring.simulator.domain.model.DecisionSource;
import com.acquiring.simulator.domain.model.EntryMode;
import com.acquiring.simulator.domain.model.RiskIndicator;
import com.acquiring.simulator.domain.model.Route;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
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
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeDefinition;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.BillingMode;
import software.amazon.awssdk.services.dynamodb.model.CreateTableRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.KeySchemaElement;
import software.amazon.awssdk.services.dynamodb.model.KeyType;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType;

/**
 * P2b7 internal signed-evidence service facade against DynamoDB Local (observation contract
 * O3/P5; channel contract C4/P6 late-delivery paragraph; OBS-08/OBS-10).
 *
 * <p>Each case builds the real fixture {@code 0100} and exact received {@code 0110}, signs the
 * fixture's attestation with an ephemeral in-memory Ed25519 key, wraps it in the exact C4
 * late-delivery envelope and lets the facade route it to the existing gateways. The gateways run
 * the real DIRECT verifier and the real P6 binder; no test declares evidence verified, bound,
 * late or already admitted.</p>
 *
 * <p>This proves local facade routing only. No HTTP/service caller, sender authentication, key
 * custody, runtime wiring or AWS effect is exercised here. Ephemeral keys are never written or
 * logged.</p>
 */
@Testcontainers
class SignedEvidenceAdmissionServiceIntegrationTest {

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
    private static final String INSTANCE = "BOOT-P2B7";
    private static final Instant CLAIMED_AT = Instant.parse("2026-11-18T12:00:00Z");
    private static final Instant DISPATCHED_AT = Instant.parse("2026-11-18T12:00:01Z");
    private static final Instant DEADLINE = Instant.parse("2026-11-18T12:00:03Z");
    private static final Instant LATE_DELIVERED_AT = Instant.parse("2026-11-18T12:00:20Z");

    /** Test-only example capacity; owner approval of the O8 numbers is still pending. */
    private static final CriticalCapacity CAPACITY = new CriticalCapacity(5, 8);
    private static final EventManifest MANIFEST = OperationAManifest.manifest(CAPACITY);
    private static final EventContext CONTEXT =
            new EventContext(ENV, "ACQ-A", "MERCHANT-A", TRANSACTION_ID);

    static DynamoDbClient db;
    static final AtomicInteger TRANSACTIONS = new AtomicInteger();

    String eventTable;
    String journalTable;
    DynamoEventStore store;
    DynamoAuthorizationRepository repository;
    DynamoOriginalInstructionReader originals;

    @BeforeAll static void connect() {
        db = counting(DynamoDbClient.builder()
                .endpointOverride(URI.create("http://" + DYNAMO.getHost() + ":"
                        + DYNAMO.getMappedPort(8000)))
                .region(Region.US_EAST_1)
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create("local", "local")))
                .build());
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
        originals = new DynamoOriginalInstructionReader(db, journalTable);
    }

    @AfterEach void dropTables() {
        db.deleteTable(b -> b.tableName(eventTable));
        db.deleteTable(b -> b.tableName(journalTable));
    }

    @Test void strictlyLateEvidenceRoutesTheLateGatewayOnceAndExactReplayWritesNothingMore()
            throws Exception {
        Chain chain = awaitDispatchedIntent("EF:V08-late-approval", "V08-late-approval");
        C4LateDeliveryCandidate delivery = delivery(chain, "LATE-RECEIPT-001");
        SignedEvidenceAdmissionService service = service();

        int transactionsBefore = TRANSACTIONS.get();
        SignedEvidenceAdmissionService.Admission first = service.admit(
                delivery, chain.context(), chain.registry(), DEADLINE, LATE_DELIVERED_AT);

        assertThat(first.route()).isEqualTo(SignedEvidenceAdmissionService.Route.LATE);
        assertThat(first.normal()).isNull();
        assertThat(first.late()).isNotNull();
        assertThat(first.late().status())
                .isEqualTo(LateSignedEvidenceReceiptGateway.Status.COMMITTED);
        assertThat(first.late().timing()).isEqualTo(LateEvidenceCriticalComposer.Timing.LATE);
        assertThat(first.recorded()).isTrue();
        assertThat(first.late().confirmedState()).isEqualTo(AuthorizationState.APPROVED);
        assertThat(first.late().evidence().agreesWith(2L,
                LateEvidenceCriticalComposer.LATE_EVIDENCE_JOURNAL_VERSION)).isTrue();

        LateEvidenceReceipt firstMetadata = new LateEvidenceReceipt(
                "LATE-RECEIPT-001", Instant.parse("2026-11-18T12:00:05Z"),
                LATE_DELIVERED_AT, DEADLINE, true);
        assertThat(first.late().lateMetadata()).isEqualTo(firstMetadata);

        // Exactly one business transaction for the whole attempt.
        assertThat(TRANSACTIONS.get()).isEqualTo(transactionsBefore + 1);
        assertThat(eventRows()).hasSize(3);
        assertThat(store.head(CONTEXT).orElseThrow().lastSequence()).isEqualTo(3);
        assertThat(receiptRows()).hasSize(1);

        // The identical envelope redelivered later is a receipt-level replay through the facade.
        Map<String, AttributeValue> recordBefore = rawJournalItem("AUTH#" + AUTHORIZATION_ID,
                "META");
        List<Map<String, AttributeValue>> dedupeBefore = dedupeRows();
        SignedEvidenceAdmissionService.Admission replay = service.admit(
                delivery, chain.context(), chain.registry(), DEADLINE,
                LATE_DELIVERED_AT.plusSeconds(30));

        assertThat(replay.route()).isEqualTo(SignedEvidenceAdmissionService.Route.LATE);
        assertThat(replay.late().status())
                .isEqualTo(LateSignedEvidenceReceiptGateway.Status.REPLAY);
        assertThat(replay.recorded()).isTrue();
        assertThat(replay.late().receipt()).isEqualTo(first.late().receipt());
        assertThat(replay.late().lateMetadata()).isEqualTo(firstMetadata);
        assertThat(TRANSACTIONS.get()).isEqualTo(transactionsBefore + 1);
        assertThat(rawJournalItem("AUTH#" + AUTHORIZATION_ID, "META")).isEqualTo(recordBefore);
        assertThat(dedupeRows()).isEqualTo(dedupeBefore);
        assertThat(receiptRows()).hasSize(1);
    }

    @Test void onTimeEvidenceDeliveredLateIsHandedToTheNormalSignedAdmissionExactlyOnce()
            throws Exception {
        Chain chain = awaitDispatchedIntent("EF:V02-approved-response", "V02-approved-response");
        C4LateDeliveryCandidate delivery = delivery(chain, "LATE-RECEIPT-NORMAL");
        SignedEvidenceAdmissionService service = service();

        int transactionsBefore = TRANSACTIONS.get();
        SignedEvidenceAdmissionService.Admission admission = service.admit(
                delivery, chain.context(), chain.registry(), DEADLINE, LATE_DELIVERED_AT);

        assertThat(admission.route()).isEqualTo(SignedEvidenceAdmissionService.Route.NORMAL);
        // Evidence time 12:00:01 is at or before the 12:00:03 deadline; only delivery is late.
        assertThat(admission.late().status())
                .isEqualTo(LateSignedEvidenceReceiptGateway.Status.NORMAL_RESULT_ADMISSION);
        assertThat(admission.late().timing())
                .isEqualTo(LateEvidenceCriticalComposer.Timing.DELIVERY_DELAY);
        assertThat(admission.normal()).isNotNull();
        assertThat(admission.normal().status())
                .isEqualTo(SignedEvidenceReceiptGateway.Status.COMMITTED);
        assertThat(admission.recorded()).isTrue();
        assertThat(admission.normal().evidence().agreesWith(2L,
                ResultCriticalComposer.RESULT_JOURNAL_VERSION)).isTrue();

        // The normal path owned the only write: normal journal version, no late metadata.
        ProcessorEvidenceReceipt receipt = repository
                .readEvidenceReceipt(ENV, ISSUER, "EVID-001").orElseThrow();
        assertThat(receipt.journalVersion())
                .isEqualTo(ResultCriticalComposer.RESULT_JOURNAL_VERSION);
        assertThat(receipt.stateAfter()).isEqualTo("APPROVED");
        assertThat(receipt.evidenceAvailableAt())
                .isEqualTo(Instant.parse("2026-11-18T12:00:01Z"));
        assertThat(TRANSACTIONS.get()).isEqualTo(transactionsBefore + 1);
        assertThat(repository.readLateEvidenceReceipt(AUTHORIZATION_ID)).isEmpty();
        assertThat(rawJournalItem("AUTH#" + AUTHORIZATION_ID, "META"))
                .doesNotContainKey("lateEvidence");
        assertThat(eventRows()).hasSize(3);
        assertThat(store.head(CONTEXT).orElseThrow().lastSequence()).isEqualTo(3);
        assertThat(slotRow().get("usedSlots").n()).isEqualTo("3");
        assertThat(receiptRows()).hasSize(1);
    }

    @Test void invalidProofOrCarriageWritesNothingAndRoutesNowhere() throws Exception {
        Chain chain = awaitDispatchedIntent("EF:V08-late-approval", "V08-late-approval");
        SignedEvidenceAdmissionService service = service();

        int transactionsBefore = TRANSACTIONS.get();

        // Real verifier rejection through the facade: a flipped signature byte, valid carriage.
        C4CarrierEnvelope envelope = chain.envelope();
        byte[] tamperedSignature = envelope.signature().clone();
        tamperedSignature[tamperedSignature.length - 1] ^= 0x01;
        C4CarrierEnvelope tampered = new C4CarrierEnvelope(envelope.version(), envelope.mode(),
                envelope.attestation(), envelope.keyId(), tamperedSignature, envelope.relay());
        SignedEvidenceAdmissionService.Admission refused = service.admit(
                lateCandidate(tampered, chain.response(), "LATE-RECEIPT-BAD"),
                chain.context(), chain.registry(), DEADLINE, LATE_DELIVERED_AT);

        assertThat(refused.route()).isEqualTo(SignedEvidenceAdmissionService.Route.LATE);
        assertThat(refused.late().status())
                .isEqualTo(LateSignedEvidenceReceiptGateway.Status.REFUSED);
        assertThat(refused.recorded()).isFalse();
        assertThat(refused.detail()).isNull();

        // A presented body hash that does not describe the exact envelope body is refused by the
        // facade's own carriage check before either gateway is reached.
        C4LateDeliveryCandidate valid = delivery(chain, "LATE-RECEIPT-001");
        C4LateDeliveryCandidate forgedBodyHash = new C4LateDeliveryCandidate(
                valid.lateEnvelope(), "0".repeat(64), valid.canonicalAttestationSha256(),
                valid.canonicalLateEnvelopeSha256(),
                C4LateDeliveryCandidate.ParseState.UNVERIFIED_CANDIDATE);
        SignedEvidenceAdmissionService.Admission carriageRefused = service.admit(
                forgedBodyHash, chain.context(), chain.registry(), DEADLINE, LATE_DELIVERED_AT);

        assertThat(carriageRefused.route()).isEqualTo(SignedEvidenceAdmissionService.Route.NONE);
        assertThat(carriageRefused.late()).isNull();
        assertThat(carriageRefused.normal()).isNull();
        assertThat(carriageRefused.detail())
                .isEqualTo("delivery response body does not match the signed response hash");

        // Nothing was written, relabelled, quarantined or advanced by either refusal.
        assertThat(TRANSACTIONS.get()).isEqualTo(transactionsBefore);
        assertThat(receiptRows()).isEmpty();
        assertThat(quarantineRows("EVID-001")).isEmpty();
        assertThat(repository.readLateEvidenceReceipt(AUTHORIZATION_ID)).isEmpty();
        AuthorizationRecord persisted = repository.findById(AUTHORIZATION_ID).orElseThrow();
        assertThat(persisted.state()).isEqualTo(AuthorizationState.AWAITING);
        assertThat(persisted.decisionSource()).isEqualTo(DecisionSource.NONE);
        assertThat(repository.readVersionEvidence(AUTHORIZATION_ID).agreesWith(1L, 2L)).isTrue();
        assertThat(eventRows()).hasSize(2);
        assertThat(store.head(CONTEXT).orElseThrow().lastSequence()).isEqualTo(2);
        assertThat(slotRow().get("usedSlots").n()).isEqualTo("2");
    }

    // --- Facade wiring and exact delivery envelope -----------------------------

    private SignedEvidenceAdmissionService service() {
        return new SignedEvidenceAdmissionService(normalGateway(), lateGateway());
    }

    private SignedEvidenceReceiptGateway normalGateway() {
        return new SignedEvidenceReceiptGateway(store, repository, originals,
                context -> requireSentByIssuer(context),
                journalTable, eventTable, CAPACITY, ENV, INSTANCE);
    }

    private LateSignedEvidenceReceiptGateway lateGateway() {
        return new LateSignedEvidenceReceiptGateway(store, repository, originals,
                context -> requireSentByIssuer(context),
                journalTable, eventTable, CAPACITY, ENV, INSTANCE);
    }

    private static void requireSentByIssuer(C4AuthenticatedTransportContext context) {
        if (!ENV.equals(context.environmentId()) || !ISSUER.equals(context.authenticatedSender())) {
            throw new IllegalArgumentException("unexpected transport context");
        }
    }

    private static C4LateDeliveryCandidate delivery(Chain chain, String receiptId) {
        return lateCandidate(chain.envelope(), chain.response(), receiptId);
    }

    private static C4LateDeliveryCandidate lateCandidate(C4CarrierEnvelope envelope,
            byte[] response, String receiptId) {
        C4LateDeliveryEnvelope late = new C4LateDeliveryEnvelope(
                C4LateDeliveryEnvelope.VERSION,
                receiptId,
                Base64.getEncoder().encodeToString(response),
                envelope);
        return C4LateDeliveryParser.parse(late.toCanonicalBytes());
    }

    // --- Verified/bound fixture chain ------------------------------------------

    private record Chain(
            C4EvidenceAttestation signed,
            C4CarrierEnvelope envelope,
            C4AuthenticatedTransportContext context,
            C4KeyRegistry registry,
            byte[] response) {
    }

    private Chain awaitDispatchedIntent(String fixtureId, String packedResponseId)
            throws Exception {
        AuthorizationRecord claim = claim();
        store.append(claimSubmission(claim),
                ClaimCriticalComposer.withSlotAccounting(journalTable, eventTable, claim, CAPACITY));
        store.append(PossibleSendCriticalComposer.submission(claim, DISPATCH_CORRELATION, ENV,
                        INSTANCE, DISPATCHED_AT),
                PossibleSendCriticalComposer.withSlotAccounting(journalTable, eventTable, claim,
                        DISPATCH_CORRELATION, DISPATCHED_AT, CAPACITY));
        assertThat(repository.readVersionEvidence(AUTHORIZATION_ID).agreesWith(1L, 2L)).isTrue();

        JsonNode evidence = evidence(fixtureId);
        persistOriginal(java.util.HexFormat.of()
                .parseHex(evidence.get("original0100Hex").asText()));
        return chain(fixtureId, packedResponseId);
    }

    private Chain chain(String fixtureId, String packedResponseId) throws Exception {
        JsonNode evidence = evidence(fixtureId);
        byte[] response = responseBytes(packedResponseId);
        C4EvidenceAttestation signed = attestation(evidence.get("attestedFields"));

        KeyPair key = keyPair();
        C4CarrierEnvelope envelope = C4EvidenceFixtures.direct(signed, key.getPrivate());
        C4AuthenticatedTransportContext context =
                new C4AuthenticatedTransportContext(signed.environmentId(),
                        signed.signingPrincipal());
        C4KeyRegistry registry = registry(trusted(signed.signingPrincipal(), signed.keyId(),
                C4PrincipalRole.ISSUER_SIM, key.getPublic(), signed.acquirerRef()));
        return new Chain(signed, envelope, context, registry, response);
    }

    private void persistOriginal(byte[] original0100) {
        db.putItem(PutItemRequest.builder()
                .tableName(journalTable)
                .item(Map.of(
                        "pk", text("AUTH#" + AUTHORIZATION_ID),
                        "sk", text(DynamoOriginalInstructionReader.ORIGINAL_SORT_KEY),
                        DynamoOriginalInstructionReader.ORIGINAL_BYTES_ATTRIBUTE,
                        AttributeValue.builder().b(
                                software.amazon.awssdk.core.SdkBytes.fromByteArray(
                                        original0100)).build()))
                .build());
    }

    private AuthorizationRecord claim() {
        return new AuthorizationRecord(
                AUTHORIZATION_ID,
                "ACQ-A|MERCHANT-A|" + TRANSACTION_ID,
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
                com.acquiring.simulator.domain.event.EventDurability.CRITICAL_COMMITTED,
                ClaimCriticalComposer.CLAIM_JOURNAL_VERSION,
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

    // --- Fixture helpers --------------------------------------------------------

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

    // --- DynamoDB assertions and infrastructure ---------------------------------

    private List<Map<String, AttributeValue>> receiptRows() {
        return queryJournal("EVID#" + ENV + "#" + ISSUER + "#EVID-001");
    }

    private List<Map<String, AttributeValue>> quarantineRows(String evidenceId) {
        return queryJournal(SignedEvidenceConflict.PARTITION_PREFIX + ENV + "#" + ISSUER + "#"
                + evidenceId);
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

    private List<Map<String, AttributeValue>> dedupeRows() {
        QueryResponse page = db.query(QueryRequest.builder().tableName(eventTable)
                .consistentRead(true)
                .keyConditionExpression("pk = :pk")
                .expressionAttributeValues(Map.of(
                        ":pk", text(EventTableKeys.dedupePartition(ENV))))
                .build());
        return new ArrayList<>(page.items());
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

    // --- Transaction counter ----------------------------------------------------

    /** Delegating client that counts business transactions; every other call passes through. */
    private static DynamoDbClient counting(DynamoDbClient delegate) {
        return (DynamoDbClient) Proxy.newProxyInstance(
                DynamoDbClient.class.getClassLoader(),
                new Class<?>[] {DynamoDbClient.class},
                (proxy, method, args) -> {
                    if ("transactWriteItems".equals(method.getName())) {
                        TRANSACTIONS.incrementAndGet();
                    }
                    try {
                        return method.invoke(delegate, args);
                    } catch (InvocationTargetException e) {
                        throw e.getCause();
                    }
                });
    }
}
