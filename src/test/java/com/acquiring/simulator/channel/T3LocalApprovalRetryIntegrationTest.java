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
import com.acquiring.simulator.adapter.aws.DynamoResultGateway;
import com.acquiring.simulator.adapter.aws.SignedEvidenceReceiptGateway;
import com.acquiring.simulator.adapter.downstream.DeterministicIssuerSimulator;
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
import com.acquiring.simulator.domain.model.EvidenceEvent;
import com.acquiring.simulator.domain.model.IssuerDecision;
import com.acquiring.simulator.domain.model.Outcome;
import com.acquiring.simulator.domain.model.PurchaseRequest;
import com.acquiring.simulator.domain.model.RejectionAudit;
import com.acquiring.simulator.domain.model.RiskIndicator;
import com.acquiring.simulator.domain.model.Route;
import com.acquiring.simulator.domain.model.SimulatedDecisionRequest;
import com.acquiring.simulator.domain.port.AuthorizationRepository;
import com.acquiring.simulator.domain.port.DispatchIntentGateway;
import com.acquiring.simulator.domain.port.IssuerSimulator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
import software.amazon.awssdk.core.SdkBytes;
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
 * T3 bounded local slice: synthetic approval plus an identical retry through the existing real
 * atomic claim / dispatch-intent / verified-result adapters and the real signed-response receipt
 * component, against DynamoDB Local.
 *
 * <p>Two genuinely independent existing paths are executed because no production composition root
 * joins them yet (see `evidence/t3-local-approval-retry-20261121/BLOCKER.md`):</p>
 *
 * <ol>
 *   <li><b>Service path.</b> The real {@link AuthorizationService} with the real
 *       {@link DynamoClaimGateway}, {@link DynamoDispatchIntentGateway} and
 *       {@link DynamoResultGateway}. It approves once, commits a durable result, and a second
 *       identical request replays without a second financial send.</li>
 *   <li><b>Signed path.</b> The real {@link DynamoClaimGateway}, {@link DynamoDispatchIntentGateway},
 *       one simulated downstream call and the real {@link SignedEvidenceReceiptGateway} with its
 *       real Ed25519 verification and real signed-P5/P6 binding. It commits one durable approved
 *       result plus one receipt, and an identical retry replays with no send and no extra write.</li>
 * </ol>
 *
 * <p>This proves local adapter/composition mechanics only. It does not activate {@code AwsRuntime},
 * authenticate a service principal, perform a real AWS call, claim end-to-end T3 completion or
 * substitute for QA/Security/acceptance. The ephemeral Ed25519 key is in-memory only.</p>
 */
@Testcontainers
class T3LocalApprovalRetryIntegrationTest {

    @Container static final GenericContainer<?> DYNAMO = new GenericContainer<>(
            DockerImageName.parse("amazon/dynamodb-local:2.6.1")).withExposedPorts(8000);

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Path PACKED =
            Path.of("docs/ai-sdlc/work-items/WI-ACQ-OBS-001/vectors/packed-vectors.json");
    private static final Path EVIDENCE =
            Path.of("docs/ai-sdlc/work-items/WI-ACQ-OBS-001/vectors/evidence-fixtures.json");

    private static final String ENV = "SPEC-ENV";
    private static final String INSTANCE = "BOOT-T3-LOCAL";
    private static final String AUTH_ID = "AUTH-001";
    private static final String TX_ID = "TX-001";
    private static final String CORR = "CORR-001";
    private static final String BUSINESS_KEY = "ACQ-A|MERCHANT-A|TX-001";
    private static final Instant CLAIMED_AT = Instant.parse("2026-11-18T12:00:00Z");
    private static final Instant DISPATCHED_AT = Instant.parse("2026-11-18T12:00:01Z");

    private static final CriticalCapacity CAPACITY = new CriticalCapacity(5, 8);
    private static final EventManifest MANIFEST = OperationAManifest.manifest(CAPACITY);
    private static final EventContext SIGNED_CONTEXT =
            new EventContext(ENV, "ACQ-A", "MERCHANT-A", TX_ID);

    // Service-path fixtures (SimulatorProperties profile from the existing atomic-chain test).
    private static final String SERVICE_ENV = "SPEC-ENV-A";
    private static final String SERVICE_TX = "PURCHASE-A1";
    private static final EventContext SERVICE_CONTEXT =
            new EventContext(SERVICE_ENV, "ACQ-SIM-001", "MERCH-SIM-001", SERVICE_TX);
    private static final AuthorizedContext CALLER = new AuthorizedContext(
            "ACQ-SIM-001", "MERCH-SIM-001", "TEST", Set.of(Route.DOMESTIC));
    private static final SimulatorProperties PROPERTIES = new SimulatorProperties(
            new SimulatorProperties.Authorization(Duration.ofHours(24), Duration.ofSeconds(2)),
            new SimulatorProperties.Profile(
                    List.of(new SimulatorProperties.AcquirerFixture("ACQ-SIM-001",
                            List.of(Route.DOMESTIC))),
                    List.of(new SimulatorProperties.MerchantFixture("MERCH-SIM-001", "ACQ-SIM-001")),
                    List.of(new SimulatorProperties.TerminalFixture("TERM-SIM-001", "MERCH-SIM-001")),
                    List.of(new SimulatorProperties.CredentialRange("SIM-APP-",
                            CredentialDecision.APPROVE, Route.DOMESTIC))));

    static DynamoDbClient db;
    String journalTable;
    String eventTable;
    DynamoAuthorizationRepository repository;
    DynamoEventStore store;
    CountingIssuer issuer;
    NoLegacyWriteRepository guarded;

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
        store = new DynamoEventStore(db, eventTable, MANIFEST, new EventValidator(),
                Clock.fixed(DISPATCHED_AT, ZoneOffset.UTC), CriticalWriteComposer.NONE);
        issuer = new CountingIssuer();
        guarded = new NoLegacyWriteRepository(repository);
    }

    @AfterEach void dropTables() {
        db.deleteTable(b -> b.tableName(eventTable));
        db.deleteTable(b -> b.tableName(journalTable));
    }

    @Test
    void serviceAtomicApprovalAndIdenticalRetrySendOnceAndKeepDurableResult() {
        AuthorizationService service = service();

        AuthorizationResult first = service.authorize(CALLER, request());

        assertThat(first.outcome()).isEqualTo(Outcome.APPROVED);
        assertThat(issuer.decisions()).isEqualTo(1);
        assertThat(guarded.claimCalls).isZero();
        assertThat(guarded.dispatches).isZero();

        AuthorizationRecord persisted = repository.findById(first.reference()).orElseThrow();
        assertThat(persisted.state()).isEqualTo(AuthorizationState.APPROVED);
        assertThat(persisted.decisionSource()).isEqualTo(DecisionSource.ISSUER);
        assertThat(persisted.dispatchedAt()).isEqualTo(DISPATCHED_AT);
        assertThat(persisted.dispatchCorrelationId()).isNotBlank();
        assertThat(repository.readVersionEvidence(first.reference()).agreesWith(2L, 3L)).isTrue();
        assertThat(repository.eventsFor(first.reference())).isEmpty();

        assertThat(eventRows(SERVICE_CONTEXT)).hasSize(3);
        assertThat(store.head(SERVICE_CONTEXT).orElseThrow().lastSequence()).isEqualTo(3);
        assertThat(slotRow(SERVICE_CONTEXT).get("usedSlots").n()).isEqualTo("3");

        AuthorizationResult retry = service.authorize(CALLER, request());

        assertThat(retry.outcome()).isEqualTo(Outcome.APPROVED);
        assertThat(retry.reference()).isEqualTo(first.reference());
        assertThat(issuer.decisions()).isEqualTo(1);
        assertThat(guarded.claimCalls).isZero();
        assertThat(guarded.dispatches).isZero();
        assertThat(eventRows(SERVICE_CONTEXT)).hasSize(3);
        assertThat(store.head(SERVICE_CONTEXT).orElseThrow().lastSequence()).isEqualTo(3);
        assertThat(slotRow(SERVICE_CONTEXT).get("usedSlots").n()).isEqualTo("3");
        assertThat(repository.readVersionEvidence(first.reference()).agreesWith(2L, 3L)).isTrue();
    }

    @Test
    void signedReceiptApprovalAndIdenticalRetryCommitOnceAndSendOnce() throws Exception {
        DynamoClaimGateway claimGateway = new DynamoClaimGateway(store, repository, journalTable,
                eventTable, CAPACITY, ENV, INSTANCE);
        DynamoDispatchIntentGateway intentGateway = new DynamoDispatchIntentGateway(store,
                repository, journalTable, eventTable, CAPACITY, ENV, INSTANCE);
        SignedEvidenceReceiptGateway signedGateway = signedGateway();

        AuthorizationRecord claim = claim();
        assertThat(claimGateway.claim(claim, CORR, CLAIMED_AT).status())
                .isEqualTo(com.acquiring.simulator.domain.port.ClaimGateway.Status.CLAIMED);

        DispatchIntentGateway.Decision intent =
                intentGateway.dispatchIntent(claim, CORR, DISPATCHED_AT);
        assertThat(intent.status()).isEqualTo(DispatchIntentGateway.Status.COMMITTED);
        assertThat(intent.permitsSend()).isTrue();

        // The one financial send: a real downstream-port invocation, not a second intent write.
        DownstreamReply reply = issuer.decide(new SimulatedDecisionRequest(
                AUTH_ID, CORR, CredentialDecision.APPROVE, Route.DOMESTIC, DISPATCHED_AT,
                Duration.ofSeconds(30)));
        assertThat(issuer.decisions()).isEqualTo(1);
        assertThat(reply.correlationId()).isEqualTo(CORR);
        assertThat(reply.decision()).isEqualTo(IssuerDecision.APPROVED);

        SignedChain chain = signedChain("EF:V02-approved-response", "V02-approved-response");
        persistOriginal(chain.original());

        SignedEvidenceReceiptGateway.Decision committed = signedGateway.admit(
                chain.candidate(), chain.signed(), chain.context(), chain.registry(),
                chain.response());

        assertThat(committed.status()).isEqualTo(SignedEvidenceReceiptGateway.Status.COMMITTED);
        assertThat(committed.recorded()).isTrue();
        assertThat(committed.confirmedState()).isEqualTo(AuthorizationState.APPROVED);
        assertThat(committed.evidence().agreesWith(2L, 3L)).isTrue();
        assertThat(committed.receipt()).isNotNull();

        AuthorizationRecord persisted = repository.findById(AUTH_ID).orElseThrow();
        assertThat(persisted.state()).isEqualTo(AuthorizationState.APPROVED);
        assertThat(persisted.decisionSource()).isEqualTo(DecisionSource.ISSUER);
        assertThat(persisted.dispatchedAt()).isEqualTo(DISPATCHED_AT);
        assertThat(persisted.dispatchCorrelationId()).isEqualTo(CORR);
        assertThat(eventRows(SIGNED_CONTEXT)).hasSize(3);
        assertThat(store.head(SIGNED_CONTEXT).orElseThrow().lastSequence()).isEqualTo(3);
        assertThat(slotRow(SIGNED_CONTEXT).get("usedSlots").n()).isEqualTo("3");

        Optional<ProcessorEvidenceReceipt> receipt =
                repository.readEvidenceReceipt(ENV, ISSUER, "EVID-001");
        assertThat(receipt).isPresent();
        assertThat(receipt.orElseThrow().stateAfter()).isEqualTo("APPROVED");
        assertThat(receipt.orElseThrow().eventId()).isEqualTo("APPROVED-" + AUTH_ID);
        assertThat(receipt.orElseThrow().journalVersion()).isEqualTo(3L);
        assertThat(receiptRows()).hasSize(1);

        // Identical retry through the same real components: every stage replays, nothing sends.
        assertThat(claimGateway.claim(claim, CORR, CLAIMED_AT).status())
                .isEqualTo(com.acquiring.simulator.domain.port.ClaimGateway.Status.REPLAY);
        DispatchIntentGateway.Decision retryIntent =
                intentGateway.dispatchIntent(claim, CORR, DISPATCHED_AT);
        assertThat(retryIntent.status()).isEqualTo(DispatchIntentGateway.Status.REPLAY);
        assertThat(retryIntent.permitsSend()).isFalse();

        SignedEvidenceReceiptGateway.Decision retry = signedGateway.admit(
                chain.candidate(), chain.signed(), chain.context(), chain.registry(),
                chain.response());
        assertThat(retry.status()).isEqualTo(SignedEvidenceReceiptGateway.Status.REPLAY);
        assertThat(retry.recorded()).isTrue();
        assertThat(retry.confirmedState()).isEqualTo(AuthorizationState.APPROVED);
        assertThat(issuer.decisions()).isEqualTo(1);
        assertThat(eventRows(SIGNED_CONTEXT)).hasSize(3);
        assertThat(store.head(SIGNED_CONTEXT).orElseThrow().lastSequence()).isEqualTo(3);
        assertThat(slotRow(SIGNED_CONTEXT).get("usedSlots").n()).isEqualTo("3");
        assertThat(receiptRows()).hasSize(1);
        assertThat(repository.readEvidenceReceipt(ENV, ISSUER, "EVID-001"))
                .contains(receipt.orElseThrow());
        assertThat(repository.readVersionEvidence(AUTH_ID).agreesWith(2L, 3L)).isTrue();
    }

    // --- Composition helpers ------------------------------------------------

    private AuthorizationService service() {
        return new AuthorizationService(guarded, issuer, PROPERTIES,
                Clock.fixed(DISPATCHED_AT, ZoneOffset.UTC),
                new DynamoClaimGateway(store, repository, journalTable, eventTable, CAPACITY,
                        SERVICE_ENV, INSTANCE),
                new DynamoDispatchIntentGateway(store, repository, journalTable, eventTable,
                        CAPACITY, SERVICE_ENV, INSTANCE),
                new DynamoResultGateway(store, repository, journalTable, eventTable, CAPACITY,
                        SERVICE_ENV, INSTANCE),
                IdentifierSource.randomHex());
    }

    private SignedEvidenceReceiptGateway signedGateway() {
        return new SignedEvidenceReceiptGateway(store, repository,
                new DynamoOriginalInstructionReader(db, journalTable),
                context -> {
                    if (!ENV.equals(context.environmentId())
                            || !ISSUER.equals(context.authenticatedSender())) {
                        throw new IllegalArgumentException("unexpected transport context");
                    }
                },
                journalTable, eventTable, CAPACITY, ENV, INSTANCE);
    }

    private PurchaseRequest request() {
        return new PurchaseRequest("MERCH-SIM-001", "TERM-SIM-001", SERVICE_TX,
                new BigDecimal("10.00"), "USD", "SIM-APP-001", EntryMode.SIMULATED_CONTACT,
                CvmIndicator.SIMULATED_VERIFIED, true, null, null);
    }

    // --- Approved signed-fixture helpers ------------------------------------

    private record SignedChain(
            C4EvidenceAttestation signed,
            C4CarrierCandidate candidate,
            C4AuthenticatedTransportContext context,
            C4KeyRegistry registry,
            byte[] response,
            byte[] original) {
    }

    private SignedChain signedChain(String evidenceFixture, String packedResponse)
            throws Exception {
        JsonNode evidence = evidence(evidenceFixture);
        byte[] response = responseBytes(packedResponse);
        byte[] original = java.util.HexFormat.of()
                .parseHex(evidence.get("original0100Hex").asText());
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
        return new SignedChain(signed, candidate, transport, registry, response, original);
    }

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

    private AuthorizationRecord claim() {
        return new AuthorizationRecord(
                AUTH_ID,
                BUSINESS_KEY,
                "ACQ-A",
                "MERCHANT-A",
                TX_ID,
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

    private void persistOriginal(byte[] original0100) {
        db.putItem(PutItemRequest.builder()
                .tableName(journalTable)
                .item(Map.of(
                        "pk", text("AUTH#" + AUTH_ID),
                        "sk", text(DynamoOriginalInstructionReader.ORIGINAL_SORT_KEY),
                        DynamoOriginalInstructionReader.ORIGINAL_BYTES_ATTRIBUTE,
                        AttributeValue.builder().b(SdkBytes.fromByteArray(original0100)).build()))
                .build());
    }

    // --- DynamoDB assertions and infrastructure ----------------------------

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

    private List<Map<String, AttributeValue>> eventRows(EventContext context) {
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

    private Map<String, AttributeValue> slotRow(EventContext context) {
        var item = db.getItem(GetItemRequest.builder().tableName(eventTable)
                .key(Map.of(
                        "pk", text(EventTableKeys.capacityPartition(context)),
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

    /** Counting production downstream simulator; proves the financial send happens once. */
    private static final class CountingIssuer implements IssuerSimulator {
        private final DeterministicIssuerSimulator delegate = new DeterministicIssuerSimulator();
        private final AtomicInteger decisions = new AtomicInteger();

        int decisions() {
            return decisions.get();
        }

        @Override public DownstreamReply decide(SimulatedDecisionRequest request) {
            decisions.incrementAndGet();
            return delegate.decide(request);
        }
    }

    /** Delegating repository that fails the test if a legacy claim/dispatch/result write occurs. */
    private static final class NoLegacyWriteRepository implements AuthorizationRepository {
        private final DynamoAuthorizationRepository delegate;
        int claimCalls;
        int dispatches;

        NoLegacyWriteRepository(DynamoAuthorizationRepository delegate) {
            this.delegate = delegate;
        }

        @Override public void claim(AuthorizationRecord record) {
            claimCalls++;
            throw new AssertionError("legacy claim() must not run on the atomic path");
        }

        @Override public void markDispatched(String authorizationId, String correlationId,
                Instant dispatchedAt) {
            dispatches++;
            throw new AssertionError("legacy markDispatched() must not run on the atomic path");
        }

        @Override public void updateOutcome(String authorizationId, String state,
                String decisionSource, String reasonCategory, Instant outcomeDeterminedAt) {
            throw new AssertionError("legacy updateOutcome() must not run on the atomic path");
        }

        @Override public Optional<AuthorizationRecord> findById(String id) {
            return delegate.findById(id);
        }

        @Override public Optional<AuthorizationRecord> findLatestByBusinessKey(String businessKey) {
            return delegate.findLatestByBusinessKey(businessKey);
        }

        @Override public List<EvidenceEvent> eventsFor(String authorizationId) {
            return delegate.eventsFor(authorizationId);
        }

        @Override public void appendEvent(EvidenceEvent event) {
            throw new AssertionError("legacy appendEvent() must not run on the atomic path");
        }

        @Override public void appendRejection(RejectionAudit audit) {
            throw new AssertionError("legacy appendRejection() must not run on the atomic path");
        }
    }
}
