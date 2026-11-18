package com.acquiring.simulator.aws;

import com.acquiring.simulator.adapter.aws.*;
import com.acquiring.simulator.adapter.downstream.DeterministicIssuerSimulator;
import com.acquiring.simulator.config.SimulatorProperties;
import com.acquiring.simulator.domain.AuthorizationService;
import com.acquiring.simulator.domain.model.*;
import com.acquiring.simulator.domain.port.*;
import java.math.BigDecimal;
import java.net.URI;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.*;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;
import static org.assertj.core.api.Assertions.*;

@Testcontainers
class AwsAuthorizationIntegrationTest {
    @Container static final GenericContainer<?> DYNAMO = new GenericContainer<>(
            DockerImageName.parse("amazon/dynamodb-local:2.6.1")).withExposedPorts(8000);
    static DynamoDbClient db;
    String table;
    String truthTable;
    DynamoAuthorizationRepository repo;
    final Instant now = Instant.parse("2026-11-18T23:30:00Z");
    final Clock clock = Clock.fixed(now, ZoneOffset.UTC);
    final AuthorizedContext caller = new AuthorizedContext("ACQ-SIM-001", "MERCH-SIM-001", "TEST", Set.of(Route.DOMESTIC));
    final SimulatorProperties properties = new SimulatorProperties(
            new SimulatorProperties.Authorization(Duration.ofHours(24), Duration.ofSeconds(2)),
            new SimulatorProperties.Profile(
                List.of(new SimulatorProperties.AcquirerFixture("ACQ-SIM-001", List.of(Route.DOMESTIC))),
                List.of(new SimulatorProperties.MerchantFixture("MERCH-SIM-001", "ACQ-SIM-001")),
                List.of(new SimulatorProperties.TerminalFixture("TERM-SIM-001", "MERCH-SIM-001")),
                List.of(new SimulatorProperties.CredentialRange("SIM-APP-", CredentialDecision.APPROVE, Route.DOMESTIC),
                        new SimulatorProperties.CredentialRange("SIM-DEC-", CredentialDecision.DECLINE, Route.DOMESTIC))));
    @BeforeAll static void connect() {
        db = DynamoDbClient.builder().endpointOverride(URI.create("http://" + DYNAMO.getHost() + ":" + DYNAMO.getMappedPort(8000)))
                .region(Region.US_EAST_1).credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("local", "local"))).build();
    }
    @AfterAll static void close() { if (db != null) db.close(); }
    @BeforeEach void create() {
        table = "journal-" + UUID.randomUUID(); truthTable = "issuer-" + UUID.randomUUID();
        for (String name : List.of(table, truthTable)) {
            db.createTable(CreateTableRequest.builder().tableName(name).billingMode(BillingMode.PAY_PER_REQUEST)
                    .attributeDefinitions(AttributeDefinition.builder().attributeName("pk").attributeType(ScalarAttributeType.S).build(),
                            AttributeDefinition.builder().attributeName("sk").attributeType(ScalarAttributeType.S).build())
                    .keySchema(KeySchemaElement.builder().attributeName("pk").keyType(KeyType.HASH).build(),
                            KeySchemaElement.builder().attributeName("sk").keyType(KeyType.RANGE).build()).build());
            db.waiter().waitUntilTableExists(b -> b.tableName(name));
        }
        repo = new DynamoAuthorizationRepository(db, table);
    }
    @AfterEach void drop() { for (String name : List.of(table, truthTable)) db.deleteTable(b -> b.tableName(name)); }
    PurchaseRequest request(String credential) {
        return new PurchaseRequest("MERCH-SIM-001", "TERM-SIM-001", "PURCHASE-1", new BigDecimal("10.00"), "USD",
                credential, EntryMode.SIMULATED_CONTACT, CvmIndicator.SIMULATED_VERIFIED, true, null, null);
    }
    AuthorizationService service(IssuerSimulator downstream) { return new AuthorizationService(repo, downstream, properties, clock); }
    @Test void issuerApprovalSurvivesNetworkLossButProcessorCannotSeeItAndRetryDoesNotRedispatch() {
        var issuer = new IssuerHandler(db, truthTable);
        var calls = new AtomicInteger();
        IssuerSimulator counted = r -> { calls.incrementAndGet(); return issuer.decide(r); };
        var core = service(new MastercardNetworkSimulator(counted, MastercardNetworkSimulator.Fault.DROP_RESPONSE));
        var result = core.authorize(caller, request("SIM-APP-001"));
        assertThat(result.outcome()).isEqualTo(Outcome.NOT_CONFIRMED);
        var record = repo.findById(result.reference()).orElseThrow();
        assertThat(record.state()).isEqualTo(AuthorizationState.NOT_CONFIRMED);
        assertThat(record.decisionSource()).isEqualTo(DecisionSource.NONE);
        var truth = db.getItem(b -> b.tableName(truthTable)
                .key(DynamoAuthorizationRepository.key("ISSUER#" + result.reference(), "DECISION")).consistentRead(true)).item();
        assertThat(WireJson.read(truth.get("payload").s(), DownstreamReply.class).decision()).isEqualTo(IssuerDecision.APPROVED);
        assertThat(core.authorize(caller, request("SIM-APP-001"))).isEqualTo(result);
        assertThat(calls).hasValue(1);
        assertThat(repo.eventsFor(result.reference())).extracting(EvidenceEvent::eventType).contains("TIMEOUT");
        // Only explicitly delivered correlated evidence may reconcile uncertainty.
        core.submitEvidence(result.reference(), new DownstreamReply(record.dispatchCorrelationId(), IssuerDecision.APPROVED, now.plusSeconds(3)));
        assertThat(core.authorize(caller, request("SIM-APP-001")).outcome()).isEqualTo(Outcome.APPROVED);
        assertThat(repo.eventsFor(result.reference())).extracting(EvidenceEvent::eventType).contains("TIMEOUT", "LATE_CONFIRMATION");
    }
    @Test void concurrentIdenticalRequestsDispatchOnceAndSurviveRepositoryRestart() throws Exception {
        var calls = new AtomicInteger();
        var core = service(r -> { calls.incrementAndGet(); return new DeterministicIssuerSimulator().decide(r); });
        try (var executor = Executors.newFixedThreadPool(8)) {
            var start = new CountDownLatch(1);
            var futures = new ArrayList<Future<AuthorizationResult>>();
            for (int i = 0; i < 8; i++) futures.add(executor.submit(() -> { start.await(); return core.authorize(caller, request("SIM-APP-001")); }));
            start.countDown();
            var refs = new HashSet<String>();
            for (var f : futures) refs.add(f.get(20, TimeUnit.SECONDS).reference());
            assertThat(refs).hasSize(1); assertThat(calls).hasValue(1);
            var restarted = new AuthorizationService(new DynamoAuthorizationRepository(db, table), r -> { throw new AssertionError("redispatch"); }, properties, clock);
            assertThat(restarted.authorize(caller, request("SIM-APP-001")).outcome()).isEqualTo(Outcome.APPROVED);
        }
    }
    @Test void expiryAdvancesAtomicGenerationWithoutDeletingPriorReference() {
        var core = service(new DeterministicIssuerSimulator());
        var first = core.authorize(caller, request("SIM-APP-001"));
        var later = new AuthorizationService(repo, new DeterministicIssuerSimulator(), properties, Clock.offset(clock, Duration.ofHours(24)));
        var second = later.authorize(caller, request("SIM-APP-001"));
        assertThat(second.reference()).isNotEqualTo(first.reference());
        assertThat(repo.findById(first.reference())).isPresent();
        assertThat(repo.findById(second.reference()).orElseThrow().generation()).isEqualTo(2);
    }
    @ParameterizedTest @EnumSource(value = MastercardNetworkSimulator.Fault.class,
            names = {"UNAVAILABLE", "MALFORMED_RESPONSE", "MISMATCHED_RESPONSE"})
    void invalidOrMissingNetworkEvidenceNeverBecomesIssuerDecline(MastercardNetworkSimulator.Fault fault) {
        var result = service(new MastercardNetworkSimulator(new DeterministicIssuerSimulator(), fault))
                .authorize(caller, request("SIM-APP-001"));
        assertThat(result.outcome()).isEqualTo(Outcome.NOT_CONFIRMED);
        assertThat(repo.findById(result.reference()).orElseThrow().decisionSource()).isEqualTo(DecisionSource.NONE);
    }
    @Test void networkRejectionHasNetworkProvenanceWithoutCallingIssuer() {
        var core = service(new MastercardNetworkSimulator(r -> { throw new AssertionError("issuer must not execute"); },
                MastercardNetworkSimulator.Fault.REJECT_BEFORE_ISSUER));
        var result = core.authorize(caller, request("SIM-APP-001"));
        assertThat(result.outcome()).isEqualTo(Outcome.PROCESSOR_REJECTED);
        assertThat(repo.findById(result.reference()).orElseThrow().decisionSource()).isEqualTo(DecisionSource.NETWORK);
        assertThat(core.authorize(caller, request("SIM-APP-001"))).isEqualTo(result);
    }
    @Test void deliveredIssuerDeclineAndConfirmedStateCannotRegress() {
        var result = service(new MastercardNetworkSimulator(new DeterministicIssuerSimulator(), MastercardNetworkSimulator.Fault.NONE))
                .authorize(caller, request("SIM-DEC-001"));
        assertThat(result.outcome()).isEqualTo(Outcome.ISSUER_DECLINED);
        repo.updateOutcome(result.reference(), "NOT_CONFIRMED", "NONE", "racing timeout", now);
        assertThat(repo.findById(result.reference()).orElseThrow().state()).isEqualTo(AuthorizationState.DECLINED);
    }
    @Test void lateNetworkResponseReturnsUncertaintyThenCorrelatedLatestEvidenceOnRetry() {
        var core = service(new MastercardNetworkSimulator(new DeterministicIssuerSimulator(), MastercardNetworkSimulator.Fault.LATE_RESPONSE));
        assertThat(core.authorize(caller, request("SIM-APP-001")).outcome()).isEqualTo(Outcome.NOT_CONFIRMED);
        assertThat(core.authorize(caller, request("SIM-APP-001")).outcome()).isEqualTo(Outcome.APPROVED);
    }
    @Test void issuerInstructionIsIdempotentAndConflictingInstructionIsRejected() {
        var issuer = new IssuerHandler(db, truthTable);
        var r = new SimulatedDecisionRequest("AUTH-1", "CORR-1", CredentialDecision.APPROVE, Route.DOMESTIC, now, Duration.ofSeconds(2));
        var first = issuer.decide(r);
        assertThat(issuer.decide(r)).isEqualTo(first);
        var conflict = new SimulatedDecisionRequest("AUTH-1", "CORR-OTHER", CredentialDecision.DECLINE, Route.DOMESTIC, now, Duration.ofSeconds(2));
        assertThatThrownBy(() -> issuer.decide(conflict)).isInstanceOf(IllegalArgumentException.class);
    }
}
