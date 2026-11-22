package com.acquiring.simulator.presenter;

import com.acquiring.simulator.adapter.aws.CriticalWriteComposer;
import com.acquiring.simulator.adapter.aws.DynamoAuthorizationRepository;
import com.acquiring.simulator.adapter.aws.DynamoClaimGateway;
import com.acquiring.simulator.adapter.aws.DynamoDispatchIntentGateway;
import com.acquiring.simulator.adapter.aws.DynamoEventHistoryReader;
import com.acquiring.simulator.adapter.aws.DynamoEventStore;
import com.acquiring.simulator.adapter.aws.DynamoJournalTransactionLister;
import com.acquiring.simulator.adapter.aws.DynamoOriginalInstructionReader;
import com.acquiring.simulator.adapter.aws.SignedEvidenceReceiptGateway;
import com.acquiring.simulator.channel.SyntheticInstructionMaterializer;
import com.acquiring.simulator.config.SimulatorProperties;
import com.acquiring.simulator.domain.AuthorizationService;
import com.acquiring.simulator.domain.IdentifierSource;
import com.acquiring.simulator.domain.event.CriticalCapacity;
import com.acquiring.simulator.domain.event.EventManifest;
import com.acquiring.simulator.domain.event.EventValidator;
import com.acquiring.simulator.domain.event.OperationAManifest;
import com.acquiring.simulator.domain.model.CredentialDecision;
import com.acquiring.simulator.domain.model.Route;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeDefinition;
import software.amazon.awssdk.services.dynamodb.model.BillingMode;
import software.amazon.awssdk.services.dynamodb.model.CreateTableRequest;
import software.amazon.awssdk.services.dynamodb.model.KeySchemaElement;
import software.amazon.awssdk.services.dynamodb.model.KeyType;
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType;

/**
 * Local-demo composition root and launch entry point for the presenter relay.
 *
 * <p>Launch (after {@code mvn -o -q -DskipTests compile}):</p>
 * <pre>
 * PRESENTER_MODE=local-demo PRESENTER_ENVIRONMENT=local-demo \
 * PRESENTER_ACQUIRER=ACQ-SIM-01 PRESENTER_MERCHANT=MID-SIM-01 \
 * PRESENTER_TERMINAL=TERM-SIM-01 PRESENTER_DEMO_SUBJECT=DEMO-USER \
 * PRESENTER_DEMO_ACCESS_CODE=&lt;16+ chars&gt; \
 * JOURNAL_TABLE=presenter-journal EVENT_TABLE=presenter-events \
 * DYNAMODB_ENDPOINT=http://127.0.0.1:8000 \
 * java -cp "target/classes:$(cat target/classpath.txt)" \
 *      com.acquiring.simulator.presenter.LocalPresenterRuntime
 * </pre>
 *
 * <p>It composes the real atomic claim / dispatch-intent adapters, the local signed issuer, the real
 * signed-evidence gateway and the real event store against DynamoDB Local. Nothing here reaches AWS,
 * activates {@code AwsRuntime}, publishes anything or issues a token.</p>
 */
public final class LocalPresenterRuntime {

    private static final String INSTANCE = "BOOT-PRESENTER-LOCAL-DEMO";
    private static final String ISSUER_PRINCIPAL = "ISSUER-SIM-LOCAL";
    private static final String ISSUER_KEY_ID = "LOCAL-DEMO-KEY-1";
    private static final String EVIDENCE_PREFIX = "EVID-LOCAL";
    private static final CriticalCapacity CAPACITY = new CriticalCapacity(5, 8);

    private LocalPresenterRuntime() {
    }

    public static void main(String[] args) throws Exception {
        PresenterRelayConfig config = PresenterRelayConfig.fromEnvironment();
        // P5 availability is second-granular: C4/P6 binds the signed availableAt to the
        // YYYYMMDDHHmmss "at" echo and refuses a sub-second value rather than truncating it.
        // The demo therefore composes its clock on whole seconds, so the dispatch instant the
        // simulated bank signs is already the value the verifier will accept.
        Clock clock = Clock.tick(Clock.systemUTC(), Duration.ofSeconds(1));
        DynamoDbClient db = DynamoDbClient.builder()
                .endpointOverride(config.dynamoEndpoint())
                .region(Region.of(config.awsRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create("local", "local")))
                .build();
        try {
            ensureTable(db, config.journalTable());
            ensureTable(db, config.eventTable());

            EventManifest manifest = OperationAManifest.manifest(CAPACITY);
            DynamoAuthorizationRepository repository =
                    new DynamoAuthorizationRepository(db, config.journalTable());
            DynamoEventStore store = new DynamoEventStore(db, config.eventTable(), manifest,
                    new EventValidator(), clock, CriticalWriteComposer.NONE);
            DynamoOriginalInstructionReader originals =
                    new DynamoOriginalInstructionReader(db, config.journalTable());
            DynamoEventHistoryReader history =
                    new DynamoEventHistoryReader(db, config.eventTable());
            DynamoJournalTransactionLister lister =
                    new DynamoJournalTransactionLister(db, config.journalTable());

            LocalSignedIssuerSimulator issuer = new LocalSignedIssuerSimulator(originals,
                    repository, config.environmentId(), ISSUER_PRINCIPAL, ISSUER_KEY_ID,
                    EVIDENCE_PREFIX);
            SignedEvidenceReceiptGateway evidenceGateway = new SignedEvidenceReceiptGateway(
                    store, repository, originals, context -> {
                        if (!config.environmentId().equals(context.environmentId())
                                || !ISSUER_PRINCIPAL.equals(context.authenticatedSender())) {
                            throw new IllegalArgumentException(
                                    "unexpected local transport context");
                        }
                    }, config.journalTable(), config.eventTable(), CAPACITY,
                    config.environmentId(), INSTANCE);
            LocalSignedResultGateway resultGateway =
                    new LocalSignedResultGateway(issuer, evidenceGateway);

            SimulatorProperties properties = new SimulatorProperties(
                    new SimulatorProperties.Authorization(Duration.ofHours(24),
                            Duration.ofSeconds(2)),
                    new SimulatorProperties.Profile(
                            List.of(new SimulatorProperties.AcquirerFixture(config.acquirerRef(),
                                    List.of(Route.DOMESTIC))),
                            List.of(new SimulatorProperties.MerchantFixture(config.merchantRef(),
                                    config.acquirerRef())),
                            List.of(new SimulatorProperties.TerminalFixture(config.terminalRef(),
                                    config.merchantRef())),
                            List.of(new SimulatorProperties.CredentialRange("SIM-APP-",
                                            CredentialDecision.APPROVE, Route.DOMESTIC),
                                    new SimulatorProperties.CredentialRange("CRED-SIM-",
                                            CredentialDecision.APPROVE, Route.DOMESTIC))));

            AuthorizationService service = new AuthorizationService(repository, issuer,
                    properties, clock,
                    new DynamoClaimGateway(store, repository, config.journalTable(),
                            config.eventTable(), CAPACITY, config.environmentId(), INSTANCE),
                    new DynamoDispatchIntentGateway(store, repository, config.journalTable(),
                            config.eventTable(), CAPACITY, config.environmentId(), INSTANCE,
                            new SyntheticInstructionMaterializer()),
                    resultGateway, IdentifierSource.randomHex());

            PresenterRelayServer relay = new PresenterRelayServer(config, service, repository,
                    store, history, lister, clock);
            // Live frames are observation-only: the hub is called after a new event row commits and
            // never influences the payment path. HTTP catch-up remains the recovery path.
            store.addCommitObserver(relay.liveHub());
            CountDownLatch stopped = new CountDownLatch(1);
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                relay.close();
                db.close();
                stopped.countDown();
            }, "presenter-relay-shutdown"));
            relay.start();

            System.out.println("PRESENTER_RELAY_READY http://127.0.0.1:" + relay.port() + "/"
                    + " mode=" + config.mode()
                    + " environmentId=" + config.environmentId()
                    + " identityMode=LOCAL_TEST_FIXTURE"
                    + " journalTable=" + config.journalTable()
                    + " eventTable=" + config.eventTable());
            System.out.flush();
            stopped.await();
        } finally {
            db.close();
        }
    }

    private static void ensureTable(DynamoDbClient db, String name) {
        if (db.listTables().tableNames().contains(name)) {
            return;
        }
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

    static URI loopbackEndpoint(int port) {
        return URI.create("http://127.0.0.1:" + port);
    }
}
