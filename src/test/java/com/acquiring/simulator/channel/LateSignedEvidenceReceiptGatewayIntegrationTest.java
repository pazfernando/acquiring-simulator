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
import com.acquiring.simulator.domain.event.CanonicalEvent;
import com.acquiring.simulator.domain.event.CanonicalEventJson;
import com.acquiring.simulator.domain.event.CriticalCapacity;
import com.acquiring.simulator.domain.event.EventManifest;
import com.acquiring.simulator.domain.event.EventSubmission;
import com.acquiring.simulator.domain.event.EventTableKeys;
import com.acquiring.simulator.domain.event.EventContext;
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
import java.lang.reflect.Method;
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
import java.util.concurrent.atomic.AtomicBoolean;
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
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType;

/**
 * P2b6b signed late-evidence admission gateway against DynamoDB Local (observation contract
 * O3/P5; channel contract C4/P6 late-delivery paragraph; OBS-08/OBS-10).
 *
 * <p>Each case builds the real fixture {@code 0100}, signs the exact {@code 0110} attestation with
 * an ephemeral in-memory Ed25519 key, and lets the gateway run the real DIRECT verifier and the
 * real P6 binder. No test declares evidence verified, bound or already admitted.</p>
 *
 * <p>This proves local admission mechanics only. No HTTP/service caller, key custody, runtime
 * wiring or AWS effect is exercised here. Ephemeral keys are never written or logged.</p>
 */
@Testcontainers
class LateSignedEvidenceReceiptGatewayIntegrationTest {

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
    private static final String INSTANCE = "BOOT-P2B6B";
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
    DynamoOriginalInstructionReader originals;

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
        originals = new DynamoOriginalInstructionReader(db, journalTable);
    }

    @AfterEach void dropTables() {
        db.deleteTable(b -> b.tableName(eventTable));
        db.deleteTable(b -> b.tableName(journalTable));
    }

    @Test void lateEd25519EvidenceCommitsReceiptRecordEventHeadSlotAndLateMetadataAtOnce()
            throws Exception {
        Chain chain = awaitDispatchedIntent("EF:V08-late-approval", "V08-late-approval");

        LateSignedEvidenceReceiptGateway.Decision decision = gateway(store, repository).admit(
                chain.candidate(), chain.signed(), chain.context(), chain.registry(),
                chain.response(), DEADLINE, LATE_DELIVERED_AT, RECEIPT_ID);

        assertThat(decision.status()).isEqualTo(LateSignedEvidenceReceiptGateway.Status.COMMITTED);
        assertThat(decision.timing()).isEqualTo(LateEvidenceCriticalComposer.Timing.LATE);
        assertThat(decision.recorded()).isTrue();
        assertThat(decision.confirmedState()).isEqualTo(AuthorizationState.APPROVED);
        assertThat(decision.evidence().agreesWith(2L, 3L)).isTrue();
        assertThat(decision.receipt()).isNotNull();
        assertThat(decision.lateMetadata()).isEqualTo(new LateEvidenceReceipt(
                RECEIPT_ID, LATE_EVIDENCE_AT, LATE_DELIVERED_AT, DEADLINE, true));

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
                .isEqualTo(new LateEvidenceReceipt(
                        RECEIPT_ID, LATE_EVIDENCE_AT, LATE_DELIVERED_AT, DEADLINE, true));

        assertThat(eventRows()).hasSize(3);
        assertThat(store.head(CONTEXT).orElseThrow().lastSequence()).isEqualTo(3);
        assertThat(slotRow().get("usedSlots").n()).isEqualTo("3");
        assertThat(dedupeRows()).hasSize(3);

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

    @Test void freshGatewayExactP5ReplayAfterTerminalStatePreservesFirstReceiptCollectedAtAndSlots()
            throws Exception {
        Chain chain = awaitDispatchedIntent("EF:V08-late-approval", "V08-late-approval");
        LateSignedEvidenceReceiptGateway.Decision first = gateway(store, repository).admit(
                chain.candidate(), chain.signed(), chain.context(), chain.registry(),
                chain.response(), DEADLINE, LATE_DELIVERED_AT, RECEIPT_ID);
        assertThat(first.status()).isEqualTo(LateSignedEvidenceReceiptGateway.Status.COMMITTED);
        ProcessorEvidenceReceipt stored = first.receipt();

        List<Map<String, AttributeValue>> dedupeBefore = dedupeRows();
        List<Map<String, AttributeValue>> eventsBefore = eventRows();
        List<Map<String, AttributeValue>> receiptsBefore = receiptRows();
        long headBefore = store.head(CONTEXT).orElseThrow().lastSequence();

        // A fresh gateway instance, a later delivery and a different delivery receipt id: the
        // exact P5 identity and canonical hash must still replay the first facts.
        LateSignedEvidenceReceiptGateway.Decision replay = gateway(store, repository).admit(
                chain.candidate(), chain.signed(), chain.context(), chain.registry(),
                chain.response(), DEADLINE, LATE_DELIVERED_AT.plusSeconds(30), "LATE-RECEIPT-002");

        assertThat(replay.status()).isEqualTo(LateSignedEvidenceReceiptGateway.Status.REPLAY);
        assertThat(replay.recorded()).isTrue();
        assertThat(replay.confirmedState()).isEqualTo(AuthorizationState.APPROVED);
        assertThat(replay.evidence().agreesWith(2L, 3L)).isTrue();
        assertThat(replay.receipt()).isEqualTo(stored);
        // First admission facts are preserved, not overwritten by the redelivery.
        assertThat(replay.lateMetadata()).isEqualTo(new LateEvidenceReceipt(
                RECEIPT_ID, LATE_EVIDENCE_AT, LATE_DELIVERED_AT, DEADLINE, true));
        assertThat(repository.readLateEvidenceReceipt(AUTHORIZATION_ID).orElseThrow())
                .isEqualTo(replay.lateMetadata());

        assertThat(dedupeRows()).isEqualTo(dedupeBefore);
        assertThat(eventRows()).isEqualTo(eventsBefore);
        assertThat(receiptRows()).isEqualTo(receiptsBefore);
        assertThat(store.head(CONTEXT).orElseThrow().lastSequence()).isEqualTo(headBefore);
        assertThat(slotRow().get("usedSlots").n()).isEqualTo("3");
        assertThat(receiptRows()).hasSize(1);
    }

    @Test void contradictoryLateSignedOutcomeIsQuarantinedAndPreservesTheFirstConfirmation()
            throws Exception {
        Chain original = awaitDispatchedIntent("EF:V08-late-approval", "V08-late-approval");
        LateSignedEvidenceReceiptGateway.Decision committed = gateway(store, repository).admit(
                original.candidate(), original.signed(), original.context(), original.registry(),
                original.response(), DEADLINE, LATE_DELIVERED_AT, RECEIPT_ID);
        ProcessorEvidenceReceipt established = committed.receipt();

        // A different late evidence identity asserts a contradictory determined outcome.
        Chain contradiction =
                chain("EF:N-conflicting-confirmation", "N-conflicting-confirmation");
        LateSignedEvidenceReceiptGateway.Decision conflict = gateway(store, repository).admit(
                contradiction.candidate(), contradiction.signed(), contradiction.context(),
                contradiction.registry(), contradiction.response(), DEADLINE, LATE_DELIVERED_AT,
                "LATE-RECEIPT-CONFLICT");

        assertThat(conflict.status())
                .isEqualTo(LateSignedEvidenceReceiptGateway.Status.CONTENT_CONFLICT);
        assertThat(conflict.recorded()).isFalse();
        assertThat(conflict.quarantine()).isNotNull();
        assertThat(conflict.quarantine().type())
                .isEqualTo(SignedEvidenceConflict.Type.CONTRADICTORY_DETERMINED_OUTCOME);
        assertThat(conflict.quarantine().evidenceId()).isEqualTo("EVID-CONFLICT");
        assertThat(conflict.quarantine().canonicalAttestationSha256())
                .isEqualTo(contradiction.signed().canonicalSha256());
        assertThat(conflict.quarantine().establishedCanonicalAttestationSha256()).isNull();
        assertThat(conflict.quarantine().establishedAuthorizationId())
                .isEqualTo(AUTHORIZATION_ID);

        Map<String, AttributeValue> marker = quarantineRow("EVID-CONFLICT");
        assertThat(marker.keySet()).containsExactlyInAnyOrder(
                "pk", "sk", "conflictType", "environment", "attestingPrincipal", "evidenceId",
                "canonicalAttestationSha256", "establishedAuthorizationId",
                "establishedAcquirerRef", "reason");
        assertThat(marker).doesNotContainKeys("establishedCanonicalAttestationSha256",
                "payload", "signature", "proof", "original0100", "response", "secret");

        // The established late confirmation survives untouched.
        assertThat(repository.readEvidenceReceipt(ENV, ISSUER, "EVID-001"))
                .contains(established);
        assertThat(receiptRows()).hasSize(1);
        AuthorizationRecord persisted = repository.findById(AUTHORIZATION_ID).orElseThrow();
        assertThat(persisted.state()).isEqualTo(AuthorizationState.APPROVED);
        assertThat(persisted.decisionSource()).isEqualTo(DecisionSource.ISSUER);
        assertThat(repository.readVersionEvidence(AUTHORIZATION_ID).agreesWith(2L, 3L)).isTrue();
        assertThat(eventRows()).hasSize(3);
        assertThat(eventRows()).filteredOn(row -> "DECLINED-AUTH-001"
                .equals(row.get("eventId").s())).isEmpty();
        assertThat(store.head(CONTEXT).orElseThrow().lastSequence()).isEqualTo(3);
        assertThat(slotRow().get("usedSlots").n()).isEqualTo("3");
    }

    @Test void lostCommitResponseIsRecoveredOnceWithoutASecondLateResult() throws Exception {
        Chain chain = awaitDispatchedIntent("EF:V08-late-approval", "V08-late-approval");
        DynamoDbClient ambiguous = ambiguousAfterCommitClient();
        DynamoEventStore ambiguousStore = new DynamoEventStore(ambiguous, eventTable, MANIFEST,
                new EventValidator(), Clock.fixed(LATE_DELIVERED_AT, ZoneOffset.UTC),
                CriticalWriteComposer.NONE);
        DynamoAuthorizationRepository ambiguousRepository =
                new DynamoAuthorizationRepository(ambiguous, journalTable);

        LateSignedEvidenceReceiptGateway.Decision decision =
                gateway(ambiguousStore, ambiguousRepository).admit(
                        chain.candidate(), chain.signed(), chain.context(), chain.registry(),
                        chain.response(), DEADLINE, LATE_DELIVERED_AT, RECEIPT_ID);

        assertThat(decision.status())
                .isEqualTo(LateSignedEvidenceReceiptGateway.Status.RECOVERED);
        assertThat(decision.recorded()).isTrue();
        assertThat(decision.confirmedState()).isEqualTo(AuthorizationState.APPROVED);
        assertThat(decision.evidence().agreesWith(2L, 3L)).isTrue();
        assertThat(decision.receipt()).isNotNull();
        assertThat(decision.lateMetadata()).isNotNull();

        // Exactly one committed result: one event, one HEAD advance, one slot and one receipt.
        assertThat(eventRows()).hasSize(3);
        assertThat(store.head(CONTEXT).orElseThrow().lastSequence()).isEqualTo(3);
        assertThat(slotRow().get("usedSlots").n()).isEqualTo("3");
        assertThat(receiptRows()).hasSize(1);
        assertThat(eventRows()).filteredOn(row -> "APPROVED-AUTH-001"
                .equals(row.get("eventId").s())).hasSize(1);
        assertThat(repository.readVersionEvidence(AUTHORIZATION_ID).agreesWith(2L, 3L)).isTrue();
    }

    @Test void ambiguousCommitWithUnreadableLookupStaysUnknownAndNeverNoSend() throws Exception {
        Chain chain = awaitDispatchedIntent("EF:V08-late-approval", "V08-late-approval");
        DynamoDbClient failing = commitThenReadFailureClient();
        DynamoEventStore failingStore = new DynamoEventStore(failing, eventTable, MANIFEST,
                new EventValidator(), Clock.fixed(LATE_DELIVERED_AT, ZoneOffset.UTC),
                CriticalWriteComposer.NONE);
        DynamoAuthorizationRepository failingRepository =
                new DynamoAuthorizationRepository(failing, journalTable);

        LateSignedEvidenceReceiptGateway.Decision decision =
                gateway(failingStore, failingRepository).admit(
                        chain.candidate(), chain.signed(), chain.context(), chain.registry(),
                        chain.response(), DEADLINE, LATE_DELIVERED_AT, RECEIPT_ID);

        assertThat(decision.status()).isEqualTo(LateSignedEvidenceReceiptGateway.Status.UNKNOWN);
        assertThat(decision.recorded()).isFalse();
        assertThat(decision.confirmedState()).isNull();

        // The write had in fact committed; UNKNOWN means "not established", never "no result".
        assertThat(eventRows()).hasSize(3);
        assertThat(store.head(CONTEXT).orElseThrow().lastSequence()).isEqualTo(3);
        assertThat(receiptRows()).hasSize(1);
        assertThat(repository.findById(AUTHORIZATION_ID).orElseThrow().state())
                .isEqualTo(AuthorizationState.APPROVED);
    }

    @Test void onTimeEvidenceDeliveredLateIsHandedToNormalAdmissionWithoutWrites()
            throws Exception {
        Chain chain = awaitDispatchedIntent("EF:V02-approved-response", "V02-approved-response");

        // Evidence time 12:00:01 is before the 12:00:03 deadline; only delivery is late.
        LateSignedEvidenceReceiptGateway.Decision decision = gateway(store, repository).admit(
                chain.candidate(), chain.signed(), chain.context(), chain.registry(),
                chain.response(), DEADLINE, LATE_DELIVERED_AT, RECEIPT_ID);

        assertThat(decision.status())
                .isEqualTo(LateSignedEvidenceReceiptGateway.Status.NORMAL_RESULT_ADMISSION);
        assertThat(decision.timing())
                .isEqualTo(LateEvidenceCriticalComposer.Timing.DELIVERY_DELAY);
        assertThat(decision.recorded()).isFalse();
        assertThat(decision.quarantine()).isNull();

        // Nothing was written or relabelled; the durable intent is intact for the normal path.
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

    // --- Verified/bound late evidence chain -----------------------------------

    private record Chain(
            C4EvidenceAttestation signed,
            C4CarrierCandidate candidate,
            C4AuthenticatedTransportContext context,
            C4KeyRegistry registry,
            byte[] response,
            String expectedContentSha256) {
    }

    private LateSignedEvidenceReceiptGateway gateway(DynamoEventStore eventStore,
            DynamoAuthorizationRepository journalRepository) {
        return new LateSignedEvidenceReceiptGateway(eventStore, journalRepository, originals,
                context -> {
                    if (!ENV.equals(context.environmentId())
                            || !ISSUER.equals(context.authenticatedSender())) {
                        throw new IllegalArgumentException("unexpected transport context");
                    }
                },
                journalTable, eventTable, CAPACITY, ENV, INSTANCE);
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

    /** Build the exact signed candidate for a fixture without touching the dispatch seed. */
    private Chain chain(String fixtureId, String packedResponseId) throws Exception {
        JsonNode evidence = evidence(fixtureId);
        byte[] response = responseBytes(packedResponseId);
        C4EvidenceAttestation signed = attestation(evidence.get("attestedFields"));

        KeyPair key = keyPair();
        C4CarrierEnvelope envelope = C4EvidenceFixtures.direct(signed, key.getPrivate());
        C4AuthenticatedTransportContext context =
                new C4AuthenticatedTransportContext(signed.environmentId(),
                        signed.signingPrincipal());
        C4CarrierCandidate candidate = C4EvidenceFixtures.candidate(
                envelope, response, signed.signingPrincipal());
        C4KeyRegistry registry = registry(trusted(signed.signingPrincipal(), signed.keyId(),
                C4PrincipalRole.ISSUER_SIM, key.getPublic(), signed.acquirerRef()));
        return new Chain(signed, candidate, context, registry, response,
                evidence.get("attestedContentSha256").asText());
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

    private List<Map<String, AttributeValue>> quarantineRows(String evidenceId) {
        return queryJournal(SignedEvidenceConflict.PARTITION_PREFIX + ENV + "#" + ISSUER + "#"
                + evidenceId);
    }

    private Map<String, AttributeValue> quarantineRow(String evidenceId) {
        List<Map<String, AttributeValue>> rows = quarantineRows(evidenceId);
        return rows.isEmpty() ? Map.of() : rows.get(0);
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

    // --- Fault injection ------------------------------------------------------

    /** Commits the transaction and then reports an ambiguous transport outcome. */
    private static DynamoDbClient ambiguousAfterCommitClient() {
        AtomicInteger calls = new AtomicInteger();
        return proxy((method, args) -> {
            if ("transactWriteItems".equals(method.getName())
                    && calls.getAndIncrement() == 0) {
                invoke(db, method, args);
                throw SdkClientException.create("simulated lost response after commit");
            }
            return null;
        });
    }

    /** Commits, then fails every subsequent strongly consistent read. */
    private static DynamoDbClient commitThenReadFailureClient() {
        AtomicBoolean failReads = new AtomicBoolean();
        return proxy((method, args) -> {
            if ("transactWriteItems".equals(method.getName())) {
                invoke(db, method, args);
                failReads.set(true);
                throw SdkClientException.create("simulated lost response after commit");
            }
            if (failReads.get() && "getItem".equals(method.getName())) {
                throw SdkClientException.create("simulated read failure");
            }
            return null;
        });
    }

    @FunctionalInterface private interface Fault {
        /** Return {@code null} to delegate to the real client; throw to simulate a fault. */
        Object apply(Method method, Object[] args) throws Throwable;
    }

    /** Delegating client whose named operations can be intercepted for one simulated fault. */
    private static DynamoDbClient proxy(Fault fault) {
        return (DynamoDbClient) java.lang.reflect.Proxy.newProxyInstance(
                DynamoDbClient.class.getClassLoader(),
                new Class<?>[] {DynamoDbClient.class},
                (proxy, method, args) -> {
                    Object intercepted = fault.apply(method, args);
                    return intercepted != null ? intercepted : invoke(db, method, args);
                });
    }

    private static Object invoke(Object target, Method method, Object[] args) throws Throwable {
        try {
            return method.invoke(target, args);
        } catch (java.lang.reflect.InvocationTargetException e) {
            throw e.getCause();
        }
    }
}
