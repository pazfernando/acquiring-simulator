package com.acquiring.simulator.console;

import static org.assertj.core.api.Assertions.assertThat;

import com.acquiring.simulator.StubIssuerSimulator;
import com.acquiring.simulator.adapter.aws.CriticalWriteComposer;
import com.acquiring.simulator.adapter.aws.DynamoAuthorizationRepository;
import com.acquiring.simulator.adapter.aws.DynamoClaimGateway;
import com.acquiring.simulator.adapter.aws.DynamoDispatchIntentGateway;
import com.acquiring.simulator.adapter.aws.DynamoEventStore;
import com.acquiring.simulator.adapter.aws.DynamoResultGateway;
import com.acquiring.simulator.channel.SyntheticInstructionMaterializer;
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
import com.acquiring.simulator.domain.model.AuthorizedContext;
import com.acquiring.simulator.domain.model.CredentialDecision;
import com.acquiring.simulator.domain.model.DecisionSource;
import com.acquiring.simulator.domain.model.EvidenceEvent;
import com.acquiring.simulator.domain.model.RejectionAudit;
import com.acquiring.simulator.domain.model.Route;
import com.acquiring.simulator.domain.port.AuthorizationRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
import software.amazon.awssdk.services.dynamodb.model.KeySchemaElement;
import software.amazon.awssdk.services.dynamodb.model.KeyType;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType;

/**
 * Console submission route delegated to the real atomic service, against DynamoDB Local.
 *
 * <p>What this proves: a verified browser-shaped HTTP submission reaches
 * {@link AuthorizationService} on the atomic graph (claim -> dispatch intent -> result ->
 * critical evidence) exactly once and yields one payment with one downstream call; a
 * refused credential produces zero business writes and zero sends; and a duplicate
 * submission replays the existing record without a second claim or send.</p>
 *
 * <p>What it does not prove: no token signature is verified (the decoder is a test
 * double for the seam), no API Gateway, CloudFront, Keycloak, mTLS or AWS call is
 * involved, and {@code AwsRuntime.processor()} is not executed. The late-evidence and
 * network-result ports are absent here because an approval does not exercise them; the
 * other three gateways are the same types the runtime installs.</p>
 */
@Testcontainers
class ConsoleSubmissionAtomicIntegrationTest {

    @Container static final GenericContainer<?> DYNAMO = new GenericContainer<>(
            DockerImageName.parse("amazon/dynamodb-local:2.6.1")).withExposedPorts(8000);

    private static final String ENVIRONMENT = "SIM";
    private static final String INSTANCE = "BOOT-CONSOLE";
    private static final Instant NOW = Instant.parse("2026-11-22T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private static final String ISSUER = "https://auth.sim.test/realms/sim";
    private static final String AUDIENCE = "console-api";
    private static final String SUBJECT = "demo-user";
    private static final String CLIENT = "console-demo";
    private static final String SCOPE = "console.demo";
    private static final String BEARER = "Bearer demo-token";

    private static final EventContext CONTEXT =
            new EventContext(ENVIRONMENT, "ACQ-SIM-001", "MERCH-SIM-001", "PURCHASE-1");
    private static final AuthorizedContext CALLER = new AuthorizedContext(
            "ACQ-SIM-001", "MERCH-SIM-001", "SUB-DEMO", Set.of(Route.DOMESTIC));
    private static final SimulatorProperties PROPERTIES = new SimulatorProperties(
            new SimulatorProperties.Authorization(Duration.ofHours(24), Duration.ofSeconds(2)),
            new SimulatorProperties.Profile(
                    List.of(new SimulatorProperties.AcquirerFixture("ACQ-SIM-001",
                            List.of(Route.DOMESTIC))),
                    List.of(new SimulatorProperties.MerchantFixture("MERCH-SIM-001", "ACQ-SIM-001")),
                    List.of(new SimulatorProperties.TerminalFixture("TERM-SIM-001", "MERCH-SIM-001")),
                    List.of(new SimulatorProperties.CredentialRange("SIM-APP-",
                            CredentialDecision.APPROVE, Route.DOMESTIC))));

    private static final ObjectMapper JSON = new ObjectMapper();

    static DynamoDbClient db;
    String journalTable;
    String eventTable;
    DynamoAuthorizationRepository delegate;
    DynamoEventStore store;
    StubIssuerSimulator issuer;
    GuardedRepository guarded;
    ConsoleAuthorizationRoute route;

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

    @BeforeEach void createTablesAndWireTheConsole() {
        journalTable = "journal-" + UUID.randomUUID();
        eventTable = "events-" + UUID.randomUUID();
        createTable(journalTable);
        createTable(eventTable);
        delegate = new DynamoAuthorizationRepository(db, journalTable);
        store = new DynamoEventStore(db, eventTable, manifest(CAPACITY),
                new EventValidator(), CLOCK, CriticalWriteComposer.NONE);
        issuer = new StubIssuerSimulator();
        guarded = new GuardedRepository(delegate);
        route = new ConsoleAuthorizationRoute(authorizer(validClaims()),
                principal -> CALLER, atomicService(), ConsoleRateLimitHook.PERMISSIVE, CLOCK);
    }

    @AfterEach void dropTables() {
        db.deleteTable(b -> b.tableName(eventTable));
        db.deleteTable(b -> b.tableName(journalTable));
    }

    @Test
    void authorizedSubmissionCommitsOnePaymentThroughTheWiredAtomicPath() throws Exception {
        ConsoleHttpResponse response = route.handle(request(BEARER, validBody()));

        assertThat(response.status()).isEqualTo(200);
        JsonNode body = JSON.readTree(new String(response.body(), StandardCharsets.UTF_8));
        assertThat(body.get("outcome").asText()).isEqualTo("APPROVED");
        String reference = body.get("authorizationReference").asText();
        assertThat(reference).startsWith("AUTH-");

        // Exactly one downstream send, and no legacy business write was used.
        assertThat(issuer.dispatchCount()).isEqualTo(1);
        assertThat(guarded.claimCalls).isZero();
        assertThat(guarded.dispatches).isZero();
        assertThat(guarded.rejections).isZero();

        // One durable payment with the outcome derived from persisted confirmed state.
        AuthorizationRecord persisted = delegate.findById(reference).orElseThrow();
        assertThat(persisted.state()).isEqualTo(AuthorizationState.APPROVED);
        assertThat(persisted.decisionSource()).isEqualTo(DecisionSource.ISSUER);
        assertThat(persisted.generation()).isEqualTo(1);
        assertThat(persisted.dispatchCorrelationId()).isNotBlank();

        // Claim, possible-send and verified-result events committed once each.
        assertThat(eventRows()).hasSize(3);
        assertThat(store.head(CONTEXT).orElseThrow().lastSequence()).isEqualTo(3);
        assertThat(delegate.eventsFor(reference)).isEmpty();
    }

    @Test
    void refusedTokenRecordsNoClaimNoEventAndNoSend() {
        ConsoleAuthorizationRoute expiredRoute = new ConsoleAuthorizationRoute(
                authorizer(expiredClaims()), principal -> CALLER, atomicService(),
                ConsoleRateLimitHook.PERMISSIVE, CLOCK);

        ConsoleHttpResponse response = expiredRoute.handle(request(BEARER, validBody()));

        assertThat(response.status()).isEqualTo(401);
        assertThat(issuer.dispatchCount()).isZero();
        assertThat(guarded.claimCalls).isZero();
        assertThat(guarded.dispatches).isZero();
        assertThat(guarded.updates).isZero();
        assertThat(guarded.appends).isZero();
        assertThat(guarded.rejections).isZero();
        assertThat(eventRows()).isEmpty();
        assertThat(store.head(CONTEXT)).isEmpty();
        assertThat(delegate.findLatestByBusinessKey(
                "ACQ-SIM-001|MERCH-SIM-001|PURCHASE-1")).isEmpty();
    }

    @Test
    void duplicateSubmissionDoesNotCreateASecondClaimOrASecondSend() throws Exception {
        ConsoleHttpResponse first = route.handle(request(BEARER, validBody()));
        ConsoleHttpResponse second = route.handle(request(BEARER, validBody()));

        assertThat(first.status()).isEqualTo(200);
        assertThat(second.status()).isEqualTo(200);

        String firstReference = JSON.readTree(
                new String(first.body(), StandardCharsets.UTF_8))
                .get("authorizationReference").asText();
        String secondReference = JSON.readTree(
                new String(second.body(), StandardCharsets.UTF_8))
                .get("authorizationReference").asText();
        assertThat(secondReference).isEqualTo(firstReference);

        assertThat(issuer.dispatchCount()).isEqualTo(1);
        assertThat(eventRows()).hasSize(3);
        assertThat(store.head(CONTEXT).orElseThrow().lastSequence()).isEqualTo(3);
        assertThat(delegate.eventsFor(firstReference)).isEmpty();
        assertThat(delegate.findById(firstReference).orElseThrow().generation()).isEqualTo(1);
    }

    // ------------------------------------------------------------------ wiring

    /**
     * The atomic graph the console route delegates to: the same claim, dispatch-intent and
     * result gateway types {@code AwsRuntime.atomicService} installs, over DynamoDB Local.
     * The late-evidence and network-result ports are intentionally absent.
     */
    private AuthorizationService atomicService() {
        return new AuthorizationService(guarded, issuer, PROPERTIES, CLOCK,
                new DynamoClaimGateway(store, delegate, journalTable, eventTable, CAPACITY,
                        ENVIRONMENT, INSTANCE),
                new DynamoDispatchIntentGateway(store, delegate, journalTable, eventTable,
                        CAPACITY, ENVIRONMENT, INSTANCE, new SyntheticInstructionMaterializer()),
                new DynamoResultGateway(store, delegate, journalTable, eventTable, CAPACITY,
                        ENVIRONMENT, INSTANCE),
                IdentifierSource.randomHex());
    }

    private static final CriticalCapacity CAPACITY = new CriticalCapacity(5, 8);

    private static EventManifest manifest(CriticalCapacity capacity) {
        return OperationAManifest.manifest(capacity);
    }

    private static DemoConsoleAuthorizer authorizer(ConsoleTokenClaims claims) {
        return new DemoConsoleAuthorizer(ISSUER, AUDIENCE, SUBJECT, CLIENT, SCOPE,
                token -> {
                    if (!"demo-token".equals(token)) {
                        throw new ConsoleAuthenticationException(
                                ConsoleAuthenticationException.Reason.MALFORMED_TOKEN);
                    }
                    return claims;
                });
    }

    // ------------------------------------------------------------------ fixtures

    private static ConsoleTokenClaims validClaims() {
        return new ConsoleTokenClaims(ISSUER, List.of(AUDIENCE), SUBJECT, CLIENT,
                List.of(SCOPE), NOW.minusSeconds(60), NOW.minusSeconds(60),
                NOW.plusSeconds(600));
    }

    private static ConsoleTokenClaims expiredClaims() {
        return new ConsoleTokenClaims(ISSUER, List.of(AUDIENCE), SUBJECT, CLIENT,
                List.of(SCOPE), NOW.minusSeconds(1200), NOW.minusSeconds(1200),
                NOW.minusSeconds(300));
    }

    private static ConsoleHttpRequest request(String authorization, byte[] body) {
        Map<String, List<String>> headers = new LinkedHashMap<>();
        headers.put("Content-Type", List.of("application/json"));
        headers.put("Authorization", List.of(authorization));
        return new ConsoleHttpRequest("POST", ConsoleAuthorizationRoute.PATH, headers, body);
    }

    private static byte[] validBody() {
        String json = "{\"contractVersion\":\"1\",\"operation\":\"A\","
                + "\"correlationId\":\"CORR-CONSOLE-1\","
                + "\"acquirerRef\":\"ACQ-SIM-001\",\"merchantRef\":\"MERCH-SIM-001\","
                + "\"terminalRef\":\"TERM-SIM-001\",\"paymentRef\":\"PURCHASE-1\","
                + "\"credentialFixtureRef\":\"SIM-APP-001\",\"amount\":\"10.00\","
                + "\"currency\":\"USD\",\"entryMode\":\"simulated-contact\","
                + "\"cvm\":\"simulated-verified\",\"cryptogramPresent\":true}";
        return json.getBytes(StandardCharsets.US_ASCII);
    }

    private List<Map<String, AttributeValue>> eventRows() {
        return rows(EventTableKeys.eventPartition(CONTEXT), "E#");
    }

    private List<Map<String, AttributeValue>> rows(String partition, String sortPrefix) {
        List<Map<String, AttributeValue>> rows = new ArrayList<>();
        Map<String, AttributeValue> cursor = Map.of();
        do {
            QueryRequest.Builder query = QueryRequest.builder().tableName(eventTable)
                    .consistentRead(true)
                    .keyConditionExpression("pk = :pk AND begins_with(sk, :prefix)")
                    .expressionAttributeValues(Map.of(
                            ":pk", text(partition),
                            ":prefix", text(sortPrefix)));
            if (!cursor.isEmpty()) {
                query.exclusiveStartKey(cursor);
            }
            QueryResponse page = db.query(query.build());
            rows.addAll(page.items());
            cursor = page.lastEvaluatedKey();
        } while (cursor != null && !cursor.isEmpty());
        return rows;
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

    /**
     * Delegating repository that counts and forbids legacy writes, so an atomic
     * invocation cannot silently fall back to {@code claim()}, {@code markDispatched()},
     * a legacy outcome update, a legacy event append or a rejection audit.
     */
    private static final class GuardedRepository implements AuthorizationRepository {
        private final DynamoAuthorizationRepository delegate;
        int claimCalls;
        int dispatches;
        int updates;
        int appends;
        int rejections;

        GuardedRepository(DynamoAuthorizationRepository delegate) {
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
            updates++;
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
            appends++;
            throw new AssertionError("legacy appendEvent() must not run on the atomic path");
        }

        @Override public void appendRejection(RejectionAudit audit) {
            rejections++;
            throw new AssertionError("legacy appendRejection() must not run on the atomic path");
        }
    }
}
