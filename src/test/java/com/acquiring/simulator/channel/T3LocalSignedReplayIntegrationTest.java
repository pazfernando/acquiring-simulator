package com.acquiring.simulator.channel;

import static com.acquiring.simulator.channel.C4EvidenceFixtures.ISSUER;
import static com.acquiring.simulator.channel.C4EvidenceFixtures.keyPair;
import static com.acquiring.simulator.channel.C4EvidenceFixtures.registry;
import static com.acquiring.simulator.channel.C4EvidenceFixtures.trusted;
import static org.assertj.core.api.Assertions.assertThat;

import com.acquiring.simulator.adapter.aws.CriticalWriteComposer;
import com.acquiring.simulator.adapter.aws.DynamoAuthorizationRepository;
import com.acquiring.simulator.adapter.aws.DynamoClaimGateway;
import com.acquiring.simulator.adapter.aws.DynamoDispatchIntentGateway;
import com.acquiring.simulator.adapter.aws.DynamoEventStore;
import com.acquiring.simulator.adapter.aws.DynamoOriginalInstructionReader;
import com.acquiring.simulator.adapter.aws.SignedEvidenceReceiptGateway;
import com.acquiring.simulator.config.SimulatorProperties;
import com.acquiring.simulator.domain.AuthorizationService;
import com.acquiring.simulator.domain.IdentifierSource;
import com.acquiring.simulator.domain.event.CriticalCapacity;
import com.acquiring.simulator.domain.event.EventContext;
import com.acquiring.simulator.domain.event.EventManifest;
import com.acquiring.simulator.domain.event.EventTableKeys;
import com.acquiring.simulator.domain.event.EventValidator;
import com.acquiring.simulator.domain.event.OperationAManifest;
import com.acquiring.simulator.domain.event.ProcessorEvidenceReceipt;
import com.acquiring.simulator.domain.model.AuthorizationRecord;
import com.acquiring.simulator.domain.model.AuthorizationResult;
import com.acquiring.simulator.domain.model.AuthorizationState;
import com.acquiring.simulator.domain.model.AuthorizedContext;
import com.acquiring.simulator.domain.model.CredentialDecision;
import com.acquiring.simulator.domain.model.CvmIndicator;
import com.acquiring.simulator.domain.model.DecisionSource;
import com.acquiring.simulator.domain.model.DownstreamReply;
import com.acquiring.simulator.domain.model.EntryMode;
import com.acquiring.simulator.domain.model.IssuerDecision;
import com.acquiring.simulator.domain.model.Outcome;
import com.acquiring.simulator.domain.model.PurchaseRequest;
import com.acquiring.simulator.domain.model.Route;
import com.acquiring.simulator.domain.model.SimulatedDecisionRequest;
import com.acquiring.simulator.domain.port.IssuerSimulator;
import com.acquiring.simulator.domain.port.ResultGateway;
import com.acquiring.simulator.protocol.iso8583.Iso8583De48Request;
import com.acquiring.simulator.protocol.iso8583.Iso8583De48RequestParser;
import com.acquiring.simulator.protocol.iso8583.Iso8583De48ResponseParser;
import com.acquiring.simulator.protocol.iso8583.Iso8583Message;
import com.acquiring.simulator.protocol.iso8583.Iso8583Packer;
import com.acquiring.simulator.protocol.iso8583.Iso8583Scanner;
import java.math.BigDecimal;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType;

/**
 * T3 local signed replay slice: one synthetic purchase through the real service and real atomic
 * claim / dispatch-intent / signed-evidence adapters against DynamoDB Local.
 *
 * <p>This is the smallest local composition that joins the two halves the checkpoint left
 * separate. The service's dispatch-intent adapter is constructed with the real
 * {@link SyntheticInstructionMaterializer}, so the exact outbound {@code 0100} is materialized
 * before the transaction and committed in the SAME {@code TransactWriteItems} as the business
 * transition. The simulated bank then reads that durable original through the real
 * {@link DynamoOriginalInstructionReader} before it answers; nothing is seeded by the test.</p>
 *
 * <p>The bank builds an exact correlated {@code 0110} from the original bytes and signs the P5
 * attestation with an ephemeral in-memory Ed25519 key. The service's result port is bridged by a
 * test-only adapter to the real {@link SignedEvidenceReceiptGateway}<em>without</em> any
 * verification bypass or unsigned fallback: only the real C4 verifier and real P6 binder can
 * produce the receipt, and the atomic receipt/result transaction is the production composer.</p>
 *
 * <p>The production {@code ResultGateway} contract carries only a {@link DownstreamReply}; it does
 * not yet carry signed evidence. This class therefore supplies an isolated test composition seam
 * for that one hop and leaves {@code AwsRuntime}, the public route and the production port
 * unchanged. This proves local construction mechanics only; it is not QA, Security review,
 * acceptance, deployment or production activation. The ephemeral key is never written or logged.</p>
 */
@Testcontainers
class T3LocalSignedReplayIntegrationTest {

    @Container static final GenericContainer<?> DYNAMO = new GenericContainer<>(
            DockerImageName.parse("amazon/dynamodb-local:2.6.1")).withExposedPorts(8000);

    private static final String ENV = "SPEC-ENV";
    private static final String INSTANCE = "BOOT-T3-SIGNED-REPLAY";
    private static final String EVIDENCE_ID = "EVID-T3-SIGNED-REPLAY-001";
    private static final String KEY_ID = "TEST-KEY-ISSUER-REPLAY";
    private static final String ACQUIRER = "ACQ-SIM-001";
    private static final String MERCHANT = "MERCH-SIM-001";
    private static final String TERMINAL = "TERM-SIM-001";
    private static final String PAYMENT = "PURCHASE-A1";
    private static final Instant DISPATCHED_AT = Instant.parse("2026-11-18T12:00:01Z");
    private static final Clock CLOCK = Clock.fixed(DISPATCHED_AT, ZoneOffset.UTC);

    private static final CriticalCapacity CAPACITY = new CriticalCapacity(5, 8);
    private static final EventManifest MANIFEST = OperationAManifest.manifest(CAPACITY);
    private static final EventContext CONTEXT =
            new EventContext(ENV, ACQUIRER, MERCHANT, PAYMENT);

    private static final AuthorizedContext CALLER =
            new AuthorizedContext(ACQUIRER, MERCHANT, "TEST", Set.of(Route.DOMESTIC));
    private static final SimulatorProperties PROPERTIES = new SimulatorProperties(
            new SimulatorProperties.Authorization(Duration.ofHours(24), Duration.ofSeconds(2)),
            new SimulatorProperties.Profile(
                    List.of(new SimulatorProperties.AcquirerFixture(ACQUIRER,
                            List.of(Route.DOMESTIC))),
                    List.of(new SimulatorProperties.MerchantFixture(MERCHANT, ACQUIRER)),
                    List.of(new SimulatorProperties.TerminalFixture(TERMINAL, MERCHANT)),
                    List.of(new SimulatorProperties.CredentialRange("SIM-APP-",
                            CredentialDecision.APPROVE, Route.DOMESTIC))));

    static DynamoDbClient db;
    String journalTable;
    String eventTable;
    DynamoAuthorizationRepository repository;
    DynamoEventStore store;
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
        journalTable = "journal-" + UUID.randomUUID();
        eventTable = "events-" + UUID.randomUUID();
        createTable(journalTable);
        createTable(eventTable);
        repository = new DynamoAuthorizationRepository(db, journalTable);
        store = new DynamoEventStore(db, eventTable, MANIFEST, new EventValidator(), CLOCK,
                CriticalWriteComposer.NONE);
        originals = new DynamoOriginalInstructionReader(db, journalTable);
    }

    @AfterEach void dropTables() {
        db.deleteTable(b -> b.tableName(eventTable));
        db.deleteTable(b -> b.tableName(journalTable));
    }

    @Test
    void serviceCreatedUsdRecordIsApprovedFromCorrelatedSigned840EvidenceAndReplaysWithoutResend()
            throws Exception {
        SignedBank bank = new SignedBank(originals, repository, store, journalTable, eventTable);
        RecordingResultGateway resultGateway = new RecordingResultGateway(bank);
        AuthorizationService service = service(bank, resultGateway);

        AuthorizationResult first = service.authorize(CALLER, request());

        assertThat(first.outcome()).isEqualTo(Outcome.APPROVED);
        assertThat(first.reference()).isNotBlank();
        String authorizationId = first.reference();

        // The exact original was durable before the bank answered, and the bank did not receive a
        // test-seeded value: it read the committed bytes itself and independently re-materialized
        // the same instruction from the persisted record.
        assertThat(bank.sends()).isEqualTo(1);
        assertThat(bank.readOriginalBeforeReply()).isTrue();
        assertThat(bank.materializedMatchesPersisted()).isTrue();
        byte[] durableOriginal = originals.readPersistedOriginal0100(authorizationId).orElseThrow();
        assertThat(durableOriginal).isNotEmpty();
        assertThat(bank.originalBytes()).isEqualTo(durableOriginal);

        AuthorizationRecord intent = repository.findById(authorizationId).orElseThrow();
        byte[] independentlyMaterialized = new SyntheticInstructionMaterializer().materialize(
                intent, intent.dispatchCorrelationId(), DISPATCHED_AT);
        assertThat(durableOriginal).isEqualTo(independentlyMaterialized);
        assertThat(intent.currency()).isEqualTo("USD");
        assertThat(bank.signedCurrency()).isEqualTo("840");

        // The real verifier/binder/adapter ran inside the service flow and committed one result.
        assertThat(resultGateway.calls()).isEqualTo(1);
        assertThat(resultGateway.lastStatus())
                .isEqualTo(SignedEvidenceReceiptGateway.Status.COMMITTED);
        assertThat(resultGateway.lastConfirmedState()).isEqualTo(AuthorizationState.APPROVED);

        AuthorizationRecord persisted = repository.findById(authorizationId).orElseThrow();
        assertThat(persisted.state()).isEqualTo(AuthorizationState.APPROVED);
        assertThat(persisted.decisionSource()).isEqualTo(DecisionSource.ISSUER);
        assertThat(persisted.outcomeDeterminedAt()).isEqualTo(DISPATCHED_AT);
        assertThat(persisted.dispatchCorrelationId()).isEqualTo(bank.correlationId());
        assertThat(repository.readVersionEvidence(authorizationId).agreesWith(2L, 3L)).isTrue();
        assertThat(receiptRows()).hasSize(1);

        ProcessorEvidenceReceipt receipt =
                repository.readEvidenceReceipt(ENV, ISSUER, EVIDENCE_ID).orElseThrow();
        assertThat(receipt.stateAfter()).isEqualTo("APPROVED");
        assertThat(receipt.authorizationId()).isEqualTo(authorizationId);
        assertThat(receipt.canonicalAttestationSha256()).isEqualTo(bank.signedCanonicalSha256());
        assertThat(receipt.instructionSha256()).isEqualTo(bank.originalSha256());
        assertThat(receipt.responseSha256()).isEqualTo(bank.responseSha256());

        assertThat(eventRows()).hasSize(3);
        assertThat(store.head(CONTEXT).orElseThrow().lastSequence()).isEqualTo(3);
        assertThat(slotRow().get("usedSlots").n()).isEqualTo("3");

        // Identical request through the same service: same durable outcome, no second send and no
        // second receipt.
        AuthorizationResult retry = service.authorize(CALLER, request());

        assertThat(retry.outcome()).isEqualTo(Outcome.APPROVED);
        assertThat(retry.reference()).isEqualTo(authorizationId);
        assertThat(bank.sends()).isEqualTo(1);
        assertThat(resultGateway.calls()).isEqualTo(1);
        assertThat(receiptRows()).hasSize(1);
        assertThat(repository.readVersionEvidence(authorizationId).agreesWith(2L, 3L)).isTrue();
        assertThat(repository.findById(authorizationId).orElseThrow().state())
                .isEqualTo(AuthorizationState.APPROVED);
        assertThat(eventRows()).hasSize(3);
        assertThat(store.head(CONTEXT).orElseThrow().lastSequence()).isEqualTo(3);
        assertThat(slotRow().get("usedSlots").n()).isEqualTo("3");
    }

    // --- Service composition -------------------------------------------------

    private AuthorizationService service(SignedBank bank, ResultGateway resultGateway) {
        return new AuthorizationService(repository, bank, PROPERTIES, CLOCK,
                new DynamoClaimGateway(store, repository, journalTable, eventTable, CAPACITY,
                        ENV, INSTANCE),
                new DynamoDispatchIntentGateway(store, repository, journalTable, eventTable,
                        CAPACITY, ENV, INSTANCE, new SyntheticInstructionMaterializer()),
                resultGateway,
                IdentifierSource.randomHex());
    }

    private PurchaseRequest request() {
        return new PurchaseRequest(MERCHANT, TERMINAL, PAYMENT, new BigDecimal("10.00"), "USD",
                "SIM-APP-001", EntryMode.SIMULATED_CONTACT, CvmIndicator.SIMULATED_VERIFIED,
                true, null, null);
    }

    // --- Test-only result seam over the real signed gateway -------------------

    /**
     * Isolated composition seam: carries the bank's already-signed evidence from the simulated
     * downstream call to the real signed gateway. It refuses to call the gateway when the reply and
     * the signed evidence do not describe the same persisted record; it never falls back to an
     * unsigned result.
     */
    private static final class RecordingResultGateway implements ResultGateway {
        private final SignedBank bank;
        private final SignedEvidenceReceiptGateway gateway;
        private final AtomicInteger calls = new AtomicInteger();
        private volatile SignedEvidenceReceiptGateway.Status lastStatus;
        private volatile AuthorizationState lastConfirmedState;

        RecordingResultGateway(SignedBank bank) {
            this.bank = bank;
            this.gateway = new SignedEvidenceReceiptGateway(bank.store(), bank.repository(),
                    bank.originals(), context -> {
                        if (!ENV.equals(context.environmentId())
                                || !ISSUER.equals(context.authenticatedSender())) {
                            throw new IllegalArgumentException("unexpected transport context");
                        }
                    }, bank.journalTable(), bank.eventTable(), CAPACITY, ENV, INSTANCE);
        }

        int calls() {
            return calls.get();
        }

        SignedEvidenceReceiptGateway.Status lastStatus() {
            return lastStatus;
        }

        AuthorizationState lastConfirmedState() {
            return lastConfirmedState;
        }

        @Override
        public Decision recordResult(AuthorizationRecord intent, DownstreamReply evidence) {
            calls.incrementAndGet();
            SignedBank.Chain chain = bank.chain();
            if (chain == null
                    || evidence == null
                    || evidence.decision() != IssuerDecision.APPROVED
                    || !chain.signed().authorizationId().equals(intent.id())
                    || !chain.signed().correlationId().equals(evidence.correlationId())) {
                return new Decision(Status.NOT_COMMITTED, null, null,
                        "signed evidence does not correlate to the durable intent");
            }
            SignedEvidenceReceiptGateway.Decision admitted = gateway.admit(chain.candidate(),
                    chain.signed(), chain.context(), chain.registry(), chain.response());
            lastStatus = admitted.status();
            lastConfirmedState = admitted.confirmedState();
            return new Decision(switch (admitted.status()) {
                case COMMITTED -> Status.COMMITTED;
                case REPLAY -> Status.REPLAY;
                case RECOVERED -> Status.RECOVERED;
                case CONTENT_CONFLICT -> Status.CONTENT_CONFLICT;
                case REFUSED, NOT_COMMITTED -> Status.NOT_COMMITTED;
                case UNKNOWN -> Status.UNKNOWN;
            }, admitted.confirmedState(), admitted.evidence(), admitted.detail());
        }
    }

    // --- Simulated bank ------------------------------------------------------

    /**
     * Simulated bank that behaves like a downstream network: it reads the committed original from
     * storage, echoes it exactly into its response, signs the correlated P5 attestation with an
     * ephemeral test key, and returns one correlated approval. It never receives the instruction as
     * a test argument.
     */
    private static final class SignedBank implements IssuerSimulator {
        private static final DateTimeFormatter AT =
                DateTimeFormatter.ofPattern("yyyyMMddHHmmss").withZone(ZoneOffset.UTC);

        private final DynamoOriginalInstructionReader originals;
        private final DynamoAuthorizationRepository repository;
        private final DynamoEventStore store;
        private final String journalTable;
        private final String eventTable;
        private final KeyPair key;
        private final AtomicInteger sends = new AtomicInteger();

        private volatile byte[] originalBytes;
        private volatile boolean readOriginalBeforeReply;
        private volatile boolean materializedMatchesPersisted;
        private volatile String correlationId;
        private volatile String signedCurrency;
        private volatile String originalSha256;
        private volatile String responseSha256;
        private volatile String signedCanonicalSha256;
        private volatile Chain chain;

        SignedBank(DynamoOriginalInstructionReader originals,
                DynamoAuthorizationRepository repository, DynamoEventStore store,
                String journalTable, String eventTable) throws Exception {
            this.originals = originals;
            this.repository = repository;
            this.store = store;
            this.journalTable = journalTable;
            this.eventTable = eventTable;
            this.key = keyPair();
        }

        DynamoEventStore store() {
            return store;
        }

        int sends() {
            return sends.get();
        }

        boolean readOriginalBeforeReply() {
            return readOriginalBeforeReply;
        }

        boolean materializedMatchesPersisted() {
            return materializedMatchesPersisted;
        }

        byte[] originalBytes() {
            return originalBytes;
        }

        String correlationId() {
            return correlationId;
        }

        String signedCurrency() {
            return signedCurrency;
        }

        String originalSha256() {
            return originalSha256;
        }

        String responseSha256() {
            return responseSha256;
        }

        String signedCanonicalSha256() {
            return signedCanonicalSha256;
        }

        Chain chain() {
            return chain;
        }

        DynamoOriginalInstructionReader originals() {
            return originals;
        }

        DynamoAuthorizationRepository repository() {
            return repository;
        }

        String journalTable() {
            return journalTable;
        }

        String eventTable() {
            return eventTable;
        }

        @Override
        public DownstreamReply decide(SimulatedDecisionRequest request) {
            sends.incrementAndGet();
            try {
                // The financial instruction must already be durable and readable at the moment the
                // downstream is invoked. A missing original fails the test rather than a fallback.
                byte[] persisted = originals
                        .readPersistedOriginal0100(request.authorizationId())
                        .orElseThrow(() -> new AssertionError(
                                "persisted original is absent when the bank is invoked"));
                originalBytes = persisted;
                readOriginalBeforeReply = true;

                AuthorizationRecord intent =
                        repository.findById(request.authorizationId()).orElseThrow();
                byte[] rematerialized = new SyntheticInstructionMaterializer().materialize(
                        intent, request.correlationId(), request.dispatchedAt());
                materializedMatchesPersisted =
                        java.util.Arrays.equals(persisted, rematerialized);
                if (!materializedMatchesPersisted) {
                    throw new AssertionError(
                            "persisted original differs from the independent materialization");
                }
                if (!request.correlationId().equals(intent.dispatchCorrelationId())) {
                    throw new AssertionError("bank correlation differs from the durable intent");
                }

                Iso8583Message original =
                        Iso8583Scanner.scan(new String(persisted, StandardCharsets.US_ASCII));
                Iso8583De48Request extension =
                        Iso8583De48RequestParser.parse(original.field(48));

                byte[] response = approvalResponse(original, extension, request.dispatchedAt());
                C4EvidenceAttestation signed = attestation(original, extension, response,
                        request.dispatchedAt());
                C4CarrierEnvelope envelope =
                        C4EvidenceFixtures.direct(signed, key.getPrivate());
                C4AuthenticatedTransportContext context =
                        new C4AuthenticatedTransportContext(ENV, ISSUER);
                C4CarrierCandidate candidate =
                        C4EvidenceFixtures.candidate(envelope, response, ISSUER);
                C4KeyRegistry registry = registry(trusted(ISSUER, KEY_ID,
                        C4PrincipalRole.ISSUER_SIM, key.getPublic(), extension.value("acq")));

                correlationId = extension.value("cid");
                signedCurrency = signed.currency();
                originalSha256 = signed.instructionSha256();
                responseSha256 = signed.responseSha256();
                signedCanonicalSha256 = signed.canonicalSha256();
                chain = new Chain(signed, candidate, context, registry, response,
                        key.getPublic());
                return new DownstreamReply(request.correlationId(), IssuerDecision.APPROVED,
                        request.dispatchedAt());
            } catch (AssertionError failure) {
                throw failure;
            } catch (Exception failure) {
                throw new IllegalStateException("simulated bank could not return approval", failure);
            }
        }

        private byte[] approvalResponse(Iso8583Message original, Iso8583De48Request extension,
                Instant availableAt) {
            Map<Integer, String> fields = new LinkedHashMap<>();
            for (int dataElement : List.of(3, 4, 7, 11, 22, 25, 32, 37, 41, 42, 49)) {
                fields.put(dataElement, original.field(dataElement));
            }
            fields.put(39, "00");
            fields.put(48, responseExtension(extension, availableAt));
            return Iso8583Packer.pack("0110", fields).getBytes(StandardCharsets.US_ASCII);
        }

        private static String responseExtension(Iso8583De48Request extension, Instant availableAt) {
            StringBuilder de48 = new StringBuilder(256);
            for (String key : Iso8583De48ResponseParser.REQUEST_ECHO_KEYS) {
                append(de48, key, extension.value(key));
            }
            if (extension.has(Iso8583De48ResponseParser.PRIOR_KEY)) {
                append(de48, Iso8583De48ResponseParser.PRIOR_KEY,
                        extension.value(Iso8583De48ResponseParser.PRIOR_KEY));
            }
            append(de48, "actor", "ISSUER");
            append(de48, "stage", "ISSUER_REPLIED");
            append(de48, "result", "APPROVED");
            append(de48, "reason", "APPROVED");
            append(de48, "evidence", EVIDENCE_ID);
            append(de48, "at", AT.format(availableAt));
            return de48.toString();
        }

        private static void append(StringBuilder de48, String key, String value) {
            if (value == null || value.isEmpty()) {
                throw new IllegalStateException("missing approved DE48 echo value for " + key);
            }
            if (!de48.isEmpty()) {
                de48.append(';');
            }
            de48.append(key).append('=').append(value);
        }

        private C4EvidenceAttestation attestation(Iso8583Message original,
                Iso8583De48Request extension, byte[] response, Instant availableAt) {
            return new C4EvidenceAttestation(
                    1,
                    ENV,
                    ISSUER,
                    EVIDENCE_ID,
                    C4CarrierSchema.sha256Hex(originalBytes),
                    "SIM-ISO87A-v1",
                    extension.value("aid"),
                    extension.value("tid"),
                    extension.value("cid"),
                    extension.value("att"),
                    extension.value("acq"),
                    extension.value("mid"),
                    extension.value("term"),
                    extension.value("pay"),
                    Integer.parseInt(extension.value("gen")),
                    Integer.parseInt(original.field(4)),
                    original.field(49),
                    original.field(11),
                    original.field(37),
                    original.field(7),
                    extension.value("sent"),
                    "ISSUER",
                    "ISSUER_REPLIED",
                    "00",
                    "APPROVED",
                    "APPROVED",
                    null,
                    DateTimeFormatter.ISO_INSTANT.format(availableAt),
                    ISSUER,
                    KEY_ID,
                    C4CarrierSchema.sha256Hex(response));
        }

        record Chain(C4EvidenceAttestation signed, C4CarrierCandidate candidate,
                C4AuthenticatedTransportContext context, C4KeyRegistry registry,
                byte[] response, java.security.PublicKey publicKey) {
        }
    }

    // --- DynamoDB assertions and infrastructure -----------------------------

    private List<Map<String, AttributeValue>> receiptRows() {
        return queryJournal("EVID#" + ENV + "#" + ISSUER + "#" + EVIDENCE_ID);
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
