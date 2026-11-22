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
import com.acquiring.simulator.adapter.aws.PossibleSendCriticalComposer;
import com.acquiring.simulator.adapter.aws.SignedEvidenceReceiptGateway;
import com.acquiring.simulator.domain.event.CanonicalEvent;
import com.acquiring.simulator.domain.event.CanonicalEventJson;
import com.acquiring.simulator.domain.event.CriticalCapacity;
import com.acquiring.simulator.domain.event.EventContext;
import com.acquiring.simulator.domain.event.EventDurability;
import com.acquiring.simulator.domain.event.EventManifest;
import com.acquiring.simulator.domain.event.EventSubmission;
import com.acquiring.simulator.domain.event.EventTableKeys;
import com.acquiring.simulator.domain.event.EventValidator;
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
import software.amazon.awssdk.core.SdkBytes;
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
 * P2b5b signed-evidence receipt gateway against DynamoDB Local.
 *
 * <p>Each case builds the exact persisted original {@code 0100} and received {@code 0110} octets,
 * signs the fixture attestation with an ephemeral in-memory Ed25519 key, and lets the gateway run
 * the real C4 verifier and the real P6 binder before the atomic receipt transaction. The persisted
 * original is a separate strongly read journal item; the response is the exact received byte array.</p>
 *
 * <p>This proves local admission mechanics only. No HTTP route, late envelope, service caller,
 * key custody, runtime wiring or AWS effect is exercised. Ephemeral keys are never written or
 * logged.</p>
 */
@Testcontainers
class SignedEvidenceReceiptGatewayIntegrationTest {

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
    private static final String INSTANCE = "BOOT-P2B5B";
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
    DynamoOriginalInstructionReader originals;
    String authorizationId;
    String transactionId;
    String dispatchCorrelation;
    EventContext context;

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
        originals = new DynamoOriginalInstructionReader(db, journalTable);
        authorizationId = AUTHORIZATION_ID;
        transactionId = TRANSACTION_ID;
        dispatchCorrelation = DISPATCH_CORRELATION;
        context = CONTEXT;
    }

    @AfterEach void dropTables() {
        db.deleteTable(b -> b.tableName(eventTable));
        db.deleteTable(b -> b.tableName(journalTable));
    }

    @Test void approvedEd25519EvidenceCommitsReceiptResultEventHeadAndSlotInOneTransaction()
            throws Exception {
        Chain chain = awaitDispatchedIntent("EF:V02-approved-response", "V02-approved-response",
                true);

        SignedEvidenceReceiptGateway.Decision decision = gateway(store, repository).admit(
                chain.candidate(), chain.signed(), chain.context(), chain.registry(),
                chain.response());

        assertThat(decision.status()).isEqualTo(SignedEvidenceReceiptGateway.Status.COMMITTED);
        assertThat(decision.recorded()).isTrue();
        assertThat(decision.confirmedState()).isEqualTo(AuthorizationState.APPROVED);
        assertThat(decision.evidence().agreesWith(2L, 3L)).isTrue();
        assertThat(decision.receipt()).isNotNull();
        assertThat(decision.receipt().canonicalAttestationSha256())
                .isEqualTo(chain.signed().canonicalSha256());
        assertThat(decision.receipt().stateAfter()).isEqualTo("APPROVED");

        AuthorizationRecord persisted = repository.findById(AUTHORIZATION_ID).orElseThrow();
        assertThat(persisted.state()).isEqualTo(AuthorizationState.APPROVED);
        assertThat(persisted.decisionSource()).isEqualTo(DecisionSource.ISSUER);
        assertThat(persisted.outcomeDeterminedAt()).isEqualTo(EVIDENCE_AT);
        Map<String, AttributeValue> record = rawJournalItem("AUTH#" + AUTHORIZATION_ID, "META");
        assertThat(record.get("version").n()).isEqualTo("2");
        assertThat(record.get("committedJournalVersion").n()).isEqualTo("3");

        assertThat(eventRows()).hasSize(3);
        assertThat(store.head(CONTEXT).orElseThrow().lastSequence()).isEqualTo(3);
        assertThat(slotRow().get("usedSlots").n()).isEqualTo("3");
        assertThat(receiptRows()).hasSize(1);
        assertThat(dedupeRows()).hasSize(3);
    }

    @Test void declinedEd25519EvidenceCommitsTheSameAtomicReceiptWithDeclinedState()
            throws Exception {
        // The pinned V04 decline vector describes AUTH-002/TX-002; admit exactly that intent.
        authorizationId = "AUTH-002";
        transactionId = "TX-002";
        dispatchCorrelation = "CORR-002";
        context = new EventContext(ENV, "ACQ-A", "MERCHANT-A", transactionId);
        Chain chain = awaitDispatchedIntent("EF:V04-decline-response", "V04-decline-response",
                true);

        SignedEvidenceReceiptGateway.Decision decision = gateway(store, repository).admit(
                chain.candidate(), chain.signed(), chain.context(), chain.registry(),
                chain.response());

        assertThat(decision.status()).isEqualTo(SignedEvidenceReceiptGateway.Status.COMMITTED);
        assertThat(decision.confirmedState()).isEqualTo(AuthorizationState.DECLINED);
        assertThat(decision.evidence().agreesWith(2L, 3L)).isTrue();
        assertThat(decision.receipt().stateAfter()).isEqualTo("DECLINED");
        assertThat(decision.receipt().eventId()).isEqualTo("DECLINED-" + authorizationId);
        assertThat(repository.findById(authorizationId).orElseThrow().state())
                .isEqualTo(AuthorizationState.DECLINED);
        assertThat(eventRows()).hasSize(3);
        assertThat(slotRow().get("usedSlots").n()).isEqualTo("3");
        assertThat(receiptRows()).hasSize(1);
    }

    @Test void lostCommitResponseIsRecoveredOnceWithoutASecondResult() throws Exception {
        Chain chain = awaitDispatchedIntent("EF:V02-approved-response", "V02-approved-response",
                true);
        DynamoDbClient ambiguous = ambiguousAfterCommitClient();
        DynamoEventStore ambiguousStore = new DynamoEventStore(ambiguous, eventTable, MANIFEST,
                new EventValidator(), Clock.fixed(EVIDENCE_AT, ZoneOffset.UTC),
                CriticalWriteComposer.NONE);
        DynamoAuthorizationRepository ambiguousRepository =
                new DynamoAuthorizationRepository(ambiguous, journalTable);

        SignedEvidenceReceiptGateway.Decision decision = gateway(ambiguousStore, ambiguousRepository)
                .admit(chain.candidate(), chain.signed(), chain.context(), chain.registry(),
                        chain.response());

        assertThat(decision.status()).isEqualTo(SignedEvidenceReceiptGateway.Status.RECOVERED);
        assertThat(decision.recorded()).isTrue();
        assertThat(decision.confirmedState()).isEqualTo(AuthorizationState.APPROVED);
        assertThat(decision.evidence().agreesWith(2L, 3L)).isTrue();
        assertThat(decision.receipt()).isNotNull();

        // Exactly one committed result: one event, one HEAD advance, one slot and one receipt.
        assertThat(eventRows()).hasSize(3);
        assertThat(store.head(CONTEXT).orElseThrow().lastSequence()).isEqualTo(3);
        assertThat(slotRow().get("usedSlots").n()).isEqualTo("3");
        assertThat(receiptRows()).hasSize(1);
        assertThat(eventRows()).filteredOn(row -> "APPROVED-AUTH-001"
                .equals(row.get("eventId").s())).hasSize(1);
        assertThat(repository.readVersionEvidence(AUTHORIZATION_ID).agreesWith(2L, 3L)).isTrue();
    }

    @Test void freshGatewayExactCanonicalReplayReturnsStoredReceiptAndVersionWithoutExtraSlots()
            throws Exception {
        Chain chain = awaitDispatchedIntent("EF:V02-approved-response", "V02-approved-response",
                true);
        SignedEvidenceReceiptGateway.Decision first = gateway(store, repository).admit(
                chain.candidate(), chain.signed(), chain.context(), chain.registry(),
                chain.response());
        assertThat(first.status()).isEqualTo(SignedEvidenceReceiptGateway.Status.COMMITTED);
        ProcessorEvidenceReceipt stored = first.receipt();

        SignedEvidenceReceiptGateway fresh = gateway(store, repository);
        SignedEvidenceReceiptGateway.Decision replay = fresh.admit(
                chain.candidate(), chain.signed(), chain.context(), chain.registry(),
                chain.response());

        assertThat(replay.status()).isEqualTo(SignedEvidenceReceiptGateway.Status.REPLAY);
        assertThat(replay.recorded()).isTrue();
        assertThat(replay.confirmedState()).isEqualTo(AuthorizationState.APPROVED);
        assertThat(replay.evidence().agreesWith(2L, 3L)).isTrue();
        assertThat(replay.receipt()).isEqualTo(stored);

        assertThat(eventRows()).hasSize(3);
        assertThat(store.head(CONTEXT).orElseThrow().lastSequence()).isEqualTo(3);
        assertThat(slotRow().get("usedSlots").n()).isEqualTo("3");
        assertThat(receiptRows()).hasSize(1);
        assertThat(dedupeRows()).hasSize(3);
    }

    @Test void tamperedReceivedResponseIsRefusedAndPreservesTheDurableIntent() throws Exception {
        Chain chain = awaitDispatchedIntent("EF:V02-approved-response", "V02-approved-response",
                true);
        byte[] tampered = chain.response().clone();
        tampered[tampered.length - 1] ^= 0x01;

        SignedEvidenceReceiptGateway.Decision decision = gateway(store, repository).admit(
                chain.candidate(), chain.signed(), chain.context(), chain.registry(), tampered);

        assertThat(decision.status()).isEqualTo(SignedEvidenceReceiptGateway.Status.REFUSED);
        assertThat(decision.recorded()).isFalse();
        assertThat(receiptRows()).isEmpty();
        assertThat(eventRows()).hasSize(2);
        assertThat(store.head(CONTEXT).orElseThrow().lastSequence()).isEqualTo(2);
        assertThat(slotRow().get("usedSlots").n()).isEqualTo("2");
        assertThat(dedupeRows()).hasSize(2);
        AuthorizationRecord persisted = repository.findById(AUTHORIZATION_ID).orElseThrow();
        assertThat(persisted.state()).isEqualTo(AuthorizationState.AWAITING);
        assertThat(persisted.outcomeDeterminedAt()).isNull();
        assertThat(repository.readVersionEvidence(AUTHORIZATION_ID).agreesWith(1L, 2L)).isTrue();
    }

    @Test void competingDeterminedOutcomeIsPreservedAndNeverOverwritten() throws Exception {
        Chain chain = awaitDispatchedIntent("EF:V02-approved-response", "V02-approved-response",
                true);
        // A competing writer records a determined outcome after the intent was read.
        repository.updateOutcome(AUTHORIZATION_ID, AuthorizationState.DECLINED.name(),
                DecisionSource.ISSUER.name(), null, EVIDENCE_AT);

        SignedEvidenceReceiptGateway.Decision decision = gateway(store, repository).admit(
                chain.candidate(), chain.signed(), chain.context(), chain.registry(),
                chain.response());

        assertThat(decision.status())
                .isEqualTo(SignedEvidenceReceiptGateway.Status.CONTENT_CONFLICT);
        assertThat(decision.recorded()).isFalse();
        assertThat(receiptRows()).isEmpty();
        assertThat(eventRows()).hasSize(2);
        assertThat(store.head(CONTEXT).orElseThrow().lastSequence()).isEqualTo(2);
        assertThat(slotRow().get("usedSlots").n()).isEqualTo("2");
        assertThat(dedupeRows()).hasSize(2);
        AuthorizationRecord persisted = repository.findById(AUTHORIZATION_ID).orElseThrow();
        assertThat(persisted.state()).isEqualTo(AuthorizationState.DECLINED);
        assertThat(persisted.decisionSource()).isEqualTo(DecisionSource.ISSUER);
        assertThat(persisted.outcomeDeterminedAt()).isEqualTo(EVIDENCE_AT);
    }

    @Test void reusedEvidenceIdentityWithChangedSignedContentIsQuarantinedAndPreservesTheOriginal()
            throws Exception {
        Chain original = awaitDispatchedIntent("EF:V02-approved-response", "V02-approved-response",
                true);
        SignedEvidenceReceiptGateway.Decision committed = gateway(store, repository).admit(
                original.candidate(), original.signed(), original.context(), original.registry(),
                original.response());
        assertThat(committed.status()).isEqualTo(SignedEvidenceReceiptGateway.Status.COMMITTED);
        ProcessorEvidenceReceipt established = committed.receipt();

        // Same immutable (environment, principal, evidenceId) with different signed content.
        Chain changed = chain("EF:V10-actual-DE38", "V10-actual-DE38");
        SignedEvidenceReceiptGateway.Decision conflict = gateway(store, repository).admit(
                changed.candidate(), changed.signed(), changed.context(), changed.registry(),
                changed.response());

        assertThat(conflict.status()).isEqualTo(SignedEvidenceReceiptGateway.Status.CONTENT_CONFLICT);
        assertThat(conflict.recorded()).isFalse();
        assertThat(conflict.quarantine()).isNotNull();
        assertThat(conflict.quarantine().type())
                .isEqualTo(SignedEvidenceConflict.Type.REUSED_IDENTITY_DIFFERENT_CONTENT);
        assertThat(conflict.quarantine().evidenceId()).isEqualTo("EVID-001");
        assertThat(conflict.quarantine().canonicalAttestationSha256())
                .isEqualTo(changed.signed().canonicalSha256())
                .isNotEqualTo(established.canonicalAttestationSha256());
        assertThat(conflict.quarantine().establishedCanonicalAttestationSha256())
                .isEqualTo(established.canonicalAttestationSha256());
        assertThat(conflict.quarantine().establishedAuthorizationId())
                .isEqualTo(AUTHORIZATION_ID);

        // Durable marker is metadata only: hashes/references/reason, never proof or payload.
        Map<String, AttributeValue> marker = quarantineRow("EVID-001");
        assertThat(marker.keySet()).containsExactlyInAnyOrder(
                "pk", "sk", "conflictType", "environment", "attestingPrincipal", "evidenceId",
                "canonicalAttestationSha256", "establishedCanonicalAttestationSha256",
                "establishedAuthorizationId", "establishedAcquirerRef", "reason");
        assertThat(marker.get("canonicalAttestationSha256").s()).matches("[0-9a-f]{64}");
        assertThat(marker.get("establishedCanonicalAttestationSha256").s())
                .isEqualTo(established.canonicalAttestationSha256());
        assertThat(marker).doesNotContainKeys("payload", "signature", "proof", "original0100",
                "response", "secret");

        // The established receipt, state, version, events, HEAD and slots are untouched.
        assertThat(repository.readEvidenceReceipt(ENV, ISSUER, "EVID-001"))
                .contains(established);
        assertThat(receiptRows()).hasSize(1);
        AuthorizationRecord persisted = repository.findById(AUTHORIZATION_ID).orElseThrow();
        assertThat(persisted.state()).isEqualTo(AuthorizationState.APPROVED);
        assertThat(persisted.decisionSource()).isEqualTo(DecisionSource.ISSUER);
        assertThat(repository.readVersionEvidence(AUTHORIZATION_ID).agreesWith(2L, 3L)).isTrue();
        assertThat(eventRows()).hasSize(3);
        assertThat(store.head(CONTEXT).orElseThrow().lastSequence()).isEqualTo(3);
        assertThat(slotRow().get("usedSlots").n()).isEqualTo("3");
    }

    @Test void differentEvidenceIdWithContradictorySignedDecisionIsQuarantinedAndPreservesConfirmation()
            throws Exception {
        Chain original = awaitDispatchedIntent("EF:V02-approved-response", "V02-approved-response",
                true);
        SignedEvidenceReceiptGateway.Decision committed = gateway(store, repository).admit(
                original.candidate(), original.signed(), original.context(), original.registry(),
                original.response());
        assertThat(committed.status()).isEqualTo(SignedEvidenceReceiptGateway.Status.COMMITTED);
        ProcessorEvidenceReceipt established = committed.receipt();

        // A different signed evidence identity asserts a contradictory determined outcome.
        Chain contradiction = chain("EF:N-conflicting-confirmation", "N-conflicting-confirmation");
        SignedEvidenceReceiptGateway.Decision conflict = gateway(store, repository).admit(
                contradiction.candidate(), contradiction.signed(), contradiction.context(),
                contradiction.registry(), contradiction.response());

        assertThat(conflict.status()).isEqualTo(SignedEvidenceReceiptGateway.Status.CONTENT_CONFLICT);
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

        // The established confirmation survives: no DECLINED result, receipt, event or slot.
        assertThat(repository.readEvidenceReceipt(ENV, ISSUER, "EVID-001"))
                .contains(established);
        assertThat(repository.readEvidenceReceipt(ENV, ISSUER, "EVID-CONFLICT")).isEmpty();
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

    @Test void duplicateQuarantineFlagIsStableAndKeepsTheFirstConflictingWitness() throws Exception {
        Chain original = awaitDispatchedIntent("EF:V02-approved-response", "V02-approved-response",
                true);
        gateway(store, repository).admit(original.candidate(), original.signed(),
                original.context(), original.registry(), original.response());

        Chain firstConflict = chain("EF:V10-actual-DE38", "V10-actual-DE38");
        SignedEvidenceReceiptGateway.Decision first = gateway(store, repository).admit(
                firstConflict.candidate(), firstConflict.signed(), firstConflict.context(),
                firstConflict.registry(), firstConflict.response());
        assertThat(first.status()).isEqualTo(SignedEvidenceReceiptGateway.Status.CONTENT_CONFLICT);

        Chain secondConflict = chain("EF:V08-late-approval", "V08-late-approval");
        assertThat(secondConflict.signed().canonicalSha256())
                .isNotEqualTo(firstConflict.signed().canonicalSha256());
        SignedEvidenceReceiptGateway.Decision repeat = gateway(store, repository).admit(
                secondConflict.candidate(), secondConflict.signed(), secondConflict.context(),
                secondConflict.registry(), secondConflict.response());

        assertThat(repeat.status()).isEqualTo(SignedEvidenceReceiptGateway.Status.CONTENT_CONFLICT);
        assertThat(quarantineRows("EVID-001")).hasSize(1);
        assertThat(quarantineRow("EVID-001").get("canonicalAttestationSha256").s())
                .isEqualTo(firstConflict.signed().canonicalSha256());
        assertThat(repository.readVersionEvidence(AUTHORIZATION_ID).agreesWith(2L, 3L)).isTrue();
        assertThat(repository.findById(AUTHORIZATION_ID).orElseThrow().state())
                .isEqualTo(AuthorizationState.APPROVED);
        assertThat(eventRows()).hasSize(3);
        assertThat(store.head(CONTEXT).orElseThrow().lastSequence()).isEqualTo(3);
        assertThat(slotRow().get("usedSlots").n()).isEqualTo("3");
    }

    @Test void unauthorizedContextCannotWriteAQuarantineFlag() throws Exception {
        Chain original = awaitDispatchedIntent("EF:V02-approved-response", "V02-approved-response",
                true);
        gateway(store, repository).admit(original.candidate(), original.signed(),
                original.context(), original.registry(), original.response());

        Chain changed = chain("EF:V10-actual-DE38", "V10-actual-DE38");
        SignedEvidenceReceiptGateway denying = new SignedEvidenceReceiptGateway(
                store, repository, originals,
                context -> {
                    throw new IllegalArgumentException("not an authorized transport principal");
                },
                journalTable, eventTable, CAPACITY, ENV, INSTANCE);

        SignedEvidenceReceiptGateway.Decision refused = denying.admit(
                changed.candidate(), changed.signed(), changed.context(), changed.registry(),
                changed.response());

        assertThat(refused.status()).isEqualTo(SignedEvidenceReceiptGateway.Status.REFUSED);
        assertThat(refused.quarantine()).isNull();
        assertThat(quarantineRows("EVID-001")).isEmpty();
        assertThat(repository.readEvidenceConflict(ENV, ISSUER, "EVID-001")).isEmpty();
        assertThat(repository.findById(AUTHORIZATION_ID).orElseThrow().state())
                .isEqualTo(AuthorizationState.APPROVED);
        assertThat(repository.readVersionEvidence(AUTHORIZATION_ID).agreesWith(2L, 3L)).isTrue();
        assertThat(receiptRows()).hasSize(1);
        assertThat(eventRows()).hasSize(3);
    }

    @Test void quarantinePersistenceFailureCannotChangeTheOutcomeOrDeleteTheOriginal()
            throws Exception {
        Chain original = awaitDispatchedIntent("EF:V02-approved-response", "V02-approved-response",
                true);
        SignedEvidenceReceiptGateway.Decision committed = gateway(store, repository).admit(
                original.candidate(), original.signed(), original.context(), original.registry(),
                original.response());
        ProcessorEvidenceReceipt established = committed.receipt();

        Chain changed = chain("EF:V10-actual-DE38", "V10-actual-DE38");
        SignedEvidenceReceiptGateway failing = gateway(store, repository, conflict -> {
            throw new IllegalStateException("simulated quarantine persistence failure");
        });

        SignedEvidenceReceiptGateway.Decision conflict = failing.admit(
                changed.candidate(), changed.signed(), changed.context(), changed.registry(),
                changed.response());

        assertThat(conflict.status()).isEqualTo(SignedEvidenceReceiptGateway.Status.CONTENT_CONFLICT);
        assertThat(conflict.quarantine()).isNull();
        assertThat(quarantineRows("EVID-001")).isEmpty();
        assertThat(repository.readEvidenceReceipt(ENV, ISSUER, "EVID-001"))
                .contains(established);
        assertThat(receiptRows()).hasSize(1);
        assertThat(repository.findById(AUTHORIZATION_ID).orElseThrow().state())
                .isEqualTo(AuthorizationState.APPROVED);
        assertThat(repository.readVersionEvidence(AUTHORIZATION_ID).agreesWith(2L, 3L)).isTrue();
        assertThat(eventRows()).hasSize(3);
        assertThat(store.head(CONTEXT).orElseThrow().lastSequence()).isEqualTo(3);
        assertThat(slotRow().get("usedSlots").n()).isEqualTo("3");
    }

    @Test void unauthorizedContextWritesNothingAndCannotPoisonTheResultIdentity()
            throws Exception {
        Chain chain = awaitDispatchedIntent("EF:V02-approved-response", "V02-approved-response",
                true);
        SignedEvidenceReceiptGateway denying = new SignedEvidenceReceiptGateway(
                store, repository, originals,
                context -> {
                    throw new IllegalArgumentException("not an authorized transport principal");
                },
                journalTable, eventTable, CAPACITY, ENV, INSTANCE);

        SignedEvidenceReceiptGateway.Decision refused = denying.admit(
                chain.candidate(), chain.signed(), chain.context(), chain.registry(),
                chain.response());

        assertThat(refused.status()).isEqualTo(SignedEvidenceReceiptGateway.Status.REFUSED);
        assertThat(receiptRows()).isEmpty();
        assertThat(eventRows()).hasSize(2);
        assertThat(slotRow().get("usedSlots").n()).isEqualTo("2");
        assertThat(dedupeRows()).hasSize(2);
        assertThat(repository.findById(AUTHORIZATION_ID).orElseThrow().state())
                .isEqualTo(AuthorizationState.AWAITING);

        // The refused identity did not reserve a dedupe row: authenticated admission still commits NEW.
        SignedEvidenceReceiptGateway.Decision admitted = gateway(store, repository).admit(
                chain.candidate(), chain.signed(), chain.context(), chain.registry(),
                chain.response());
        assertThat(admitted.status()).isEqualTo(SignedEvidenceReceiptGateway.Status.COMMITTED);
        assertThat(eventRows()).hasSize(3);
        assertThat(dedupeRows()).hasSize(3);
        assertThat(receiptRows()).hasSize(1);
    }

    @Test void ambiguousCommitWithUnreadableLookupStaysUnknownAndNeverNoSend() throws Exception {
        Chain chain = awaitDispatchedIntent("EF:V02-approved-response", "V02-approved-response",
                true);
        DynamoDbClient commitThenUnreadable = commitThenReadFailureClient();
        DynamoEventStore ambiguousStore = new DynamoEventStore(commitThenUnreadable, eventTable,
                MANIFEST, new EventValidator(), Clock.fixed(EVIDENCE_AT, ZoneOffset.UTC),
                CriticalWriteComposer.NONE);
        DynamoAuthorizationRepository ambiguousRepository =
                new DynamoAuthorizationRepository(commitThenUnreadable, journalTable);

        SignedEvidenceReceiptGateway.Decision decision = gateway(ambiguousStore, ambiguousRepository)
                .admit(chain.candidate(), chain.signed(), chain.context(), chain.registry(),
                        chain.response());

        assertThat(decision.status()).isEqualTo(SignedEvidenceReceiptGateway.Status.UNKNOWN);
        assertThat(decision.recorded()).isFalse();
        assertThat(decision.receipt()).isNull();

        // The commit happened but cannot be confirmed: UNKNOWN is not "nothing was sent".
        assertThat(eventRows()).hasSize(3);
        assertThat(store.head(CONTEXT).orElseThrow().lastSequence()).isEqualTo(3);
        assertThat(slotRow().get("usedSlots").n()).isEqualTo("3");
        assertThat(receiptRows()).hasSize(1);
        assertThat(eventRows()).filteredOn(row -> "APPROVED-AUTH-001"
                .equals(row.get("eventId").s())).hasSize(1);
    }

    @Test void ambiguousBeforeCommitStaysUnknownWithNoWrites() throws Exception {
        Chain chain = awaitDispatchedIntent("EF:V02-approved-response", "V02-approved-response",
                true);
        DynamoDbClient ambiguous = ambiguousBeforeCommitClient();
        DynamoEventStore ambiguousStore = new DynamoEventStore(ambiguous, eventTable, MANIFEST,
                new EventValidator(), Clock.fixed(EVIDENCE_AT, ZoneOffset.UTC),
                CriticalWriteComposer.NONE);

        SignedEvidenceReceiptGateway.Decision decision = gateway(
                ambiguousStore, new DynamoAuthorizationRepository(ambiguous, journalTable))
                .admit(chain.candidate(), chain.signed(), chain.context(), chain.registry(),
                        chain.response());

        assertThat(decision.status()).isEqualTo(SignedEvidenceReceiptGateway.Status.UNKNOWN);
        assertThat(decision.recorded()).isFalse();
        assertThat(receiptRows()).isEmpty();
        assertThat(eventRows()).hasSize(2);
        assertThat(store.head(CONTEXT).orElseThrow().lastSequence()).isEqualTo(2);
        assertThat(slotRow().get("usedSlots").n()).isEqualTo("2");
        assertThat(dedupeRows()).hasSize(2);
        assertThat(repository.readVersionEvidence(AUTHORIZATION_ID).agreesWith(1L, 2L)).isTrue();
    }

    @Test void absentPersistedOriginalIsUnknownAndWritesNothing() throws Exception {
        Chain chain = awaitDispatchedIntent("EF:V02-approved-response", "V02-approved-response",
                false);

        SignedEvidenceReceiptGateway.Decision decision = gateway(store, repository).admit(
                chain.candidate(), chain.signed(), chain.context(), chain.registry(),
                chain.response());

        assertThat(decision.status()).isEqualTo(SignedEvidenceReceiptGateway.Status.UNKNOWN);
        assertThat(decision.recorded()).isFalse();
        assertThat(receiptRows()).isEmpty();
        assertThat(eventRows()).hasSize(2);
        assertThat(slotRow().get("usedSlots").n()).isEqualTo("2");
        assertThat(repository.findById(AUTHORIZATION_ID).orElseThrow().state())
                .isEqualTo(AuthorizationState.AWAITING);
    }

    // --- Chain helpers --------------------------------------------------------

    private record Chain(
            C4EvidenceAttestation signed,
            C4CarrierCandidate candidate,
            C4AuthenticatedTransportContext context,
            C4KeyRegistry registry,
            byte[] response) {
    }

    private SignedEvidenceReceiptGateway gateway(DynamoEventStore eventStore,
            DynamoAuthorizationRepository journalRepository) {
        return new SignedEvidenceReceiptGateway(eventStore, journalRepository, originals,
                context -> {
                    if (!ENV.equals(context.environmentId())
                            || !ISSUER.equals(context.authenticatedSender())) {
                        throw new IllegalArgumentException("unexpected transport context");
                    }
                },
                journalTable, eventTable, CAPACITY, ENV, INSTANCE);
    }

    private SignedEvidenceReceiptGateway gateway(DynamoEventStore eventStore,
            DynamoAuthorizationRepository journalRepository,
            SignedEvidenceReceiptGateway.ConflictRecorder recorder) {
        return new SignedEvidenceReceiptGateway(eventStore, journalRepository, originals,
                context -> {
                    if (!ENV.equals(context.environmentId())
                            || !ISSUER.equals(context.authenticatedSender())) {
                        throw new IllegalArgumentException("unexpected transport context");
                    }
                },
                journalTable, eventTable, CAPACITY, ENV, INSTANCE, recorder);
    }

    private Chain awaitDispatchedIntent(String fixtureId, String packedResponseId,
            boolean persistOriginal) throws Exception {
        AuthorizationRecord claim = claim();
        store.append(claimSubmission(claim),
                ClaimCriticalComposer.withSlotAccounting(journalTable, eventTable, claim, CAPACITY));
        store.append(PossibleSendCriticalComposer.submission(claim, dispatchCorrelation, ENV,
                        INSTANCE, DISPATCHED_AT),
                PossibleSendCriticalComposer.withSlotAccounting(journalTable, eventTable, claim,
                        dispatchCorrelation, DISPATCHED_AT, CAPACITY));
        assertThat(repository.readVersionEvidence(authorizationId).agreesWith(1L, 2L)).isTrue();

        JsonNode evidence = evidence(fixtureId);
        if (persistOriginal) {
            persistOriginal(originalBytes(evidence));
        }
        return chain(fixtureId, packedResponseId);
    }

    /** Build the exact signed candidate for a fixture without touching the dispatch seed. */
    private Chain chain(String fixtureId, String packedResponseId) throws Exception {
        JsonNode evidence = evidence(fixtureId);
        byte[] response = responseBytes(packedResponseId);
        C4EvidenceAttestation signed = attestation(evidence.get("attestedFields"));
        KeyPair key = keyPair();
        C4CarrierEnvelope envelope = C4EvidenceFixtures.direct(signed, key.getPrivate());
        C4AuthenticatedTransportContext transport =
                new C4AuthenticatedTransportContext(signed.environmentId(),
                        signed.signingPrincipal());
        C4CarrierCandidate candidate = C4EvidenceFixtures.candidate(
                envelope, response, signed.signingPrincipal());
        C4KeyRegistry registry = registry(trusted(signed.signingPrincipal(), signed.keyId(),
                C4PrincipalRole.ISSUER_SIM, key.getPublic(), signed.acquirerRef()));
        return new Chain(signed, candidate, transport, registry, response);
    }

    private void persistOriginal(byte[] original0100) {
        db.putItem(PutItemRequest.builder()
                .tableName(journalTable)
                .item(Map.of(
                        "pk", text("AUTH#" + authorizationId),
                        "sk", text(DynamoOriginalInstructionReader.ORIGINAL_SORT_KEY),
                        DynamoOriginalInstructionReader.ORIGINAL_BYTES_ATTRIBUTE,
                        AttributeValue.builder().b(SdkBytes.fromByteArray(original0100)).build()))
                .build());
    }

    private AuthorizationRecord claim() {
        return new AuthorizationRecord(
                authorizationId,
                "ACQ-A|MERCHANT-A|" + transactionId,
                "ACQ-A",
                "MERCHANT-A",
                transactionId,
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
        return new EventSubmission(context, payloadEvent("CLAIM-" + authorizationId),
                EventDurability.CRITICAL_COMMITTED, ClaimCriticalComposer.CLAIM_JOURNAL_VERSION,
                "2026-11-18T12:00:00Z", "2026-11-18T12:00:00Z", "2026-11-18T12:00:00Z",
                ClaimCriticalComposer.CLAIM_STATE_AFTER);
    }

    private CanonicalEvent payloadEvent(String eventId) {
        try {
            ObjectNode node = (ObjectNode) JSON.readTree(EVENT_VECTORS.toFile())
                    .path("events").get(0).path("producerPayload");
            node.put("eventId", eventId);
            node.put("instanceId", INSTANCE);
            node.put("transactionId", transactionId);
            node.put("operationId", authorizationId);
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
                            ":pk", text(EventTableKeys.eventPartition(context)),
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
                        "pk", text(EventTableKeys.capacityPartition(context)),
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

    /** Reports an ambiguous outcome without committing anything. */
    private static DynamoDbClient ambiguousBeforeCommitClient() {
        AtomicInteger calls = new AtomicInteger();
        return proxy((method, args) -> {
            if ("transactWriteItems".equals(method.getName())
                    && calls.getAndIncrement() == 0) {
                throw SdkClientException.create("simulated ambiguous outcome");
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
