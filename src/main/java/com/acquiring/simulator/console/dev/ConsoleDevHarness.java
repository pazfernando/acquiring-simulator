package com.acquiring.simulator.console.dev;

import com.acquiring.simulator.adapter.aws.CriticalWriteComposer;
import com.acquiring.simulator.adapter.aws.DynamoAuthorizationRepository;
import com.acquiring.simulator.adapter.aws.DynamoClaimGateway;
import com.acquiring.simulator.adapter.aws.DynamoDispatchIntentGateway;
import com.acquiring.simulator.adapter.aws.DynamoEventStore;
import com.acquiring.simulator.adapter.aws.DynamoResultGateway;
import com.acquiring.simulator.channel.SyntheticInstructionMaterializer;
import com.acquiring.simulator.config.SimulatorProperties;
import com.acquiring.simulator.console.ConsoleAuthorizationRoute;
import com.acquiring.simulator.console.ConsoleIntegrationEntryPoint;
import com.acquiring.simulator.console.ConsoleOriginVerificationGate;
import com.acquiring.simulator.console.ConsoleRateLimitHook;
import com.acquiring.simulator.console.DemoConsoleAuthorizer;
import com.acquiring.simulator.console.StandardStreamOriginDenialSink;
import com.acquiring.simulator.domain.AuthorizationService;
import com.acquiring.simulator.domain.IdentifierSource;
import com.acquiring.simulator.domain.event.CriticalCapacity;
import com.acquiring.simulator.domain.event.EventManifest;
import com.acquiring.simulator.domain.event.EventValidator;
import com.acquiring.simulator.domain.event.OperationAManifest;
import com.acquiring.simulator.domain.model.AuthorizedContext;
import com.acquiring.simulator.domain.model.CredentialDecision;
import com.acquiring.simulator.domain.model.Route;
import java.net.URI;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Set;
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
 * Local entry point for the browser-visible console screen.
 *
 * <p>It composes the same atomic claim / dispatch-intent / result graph the tests and
 * {@code AwsRuntime} use, over a DynamoDB Local endpoint the operator supplies, then mounts the
 * verified console route behind a loopback-only HTTP harness. It contacts no AWS service, loads no
 * credential, activates no Lambda and publishes nothing.</p>
 *
 * <p>Launch (after {@code mvn -o -q -DskipTests compile} and a DynamoDB Local on 127.0.0.1:8000),
 * with the origin-verification value exported as the local edge would inject it:</p>
 * <pre>
 * JAVA_HOME=/home/pazfernando/.local/opt/jdk-25 \
 * EDGE_ORIGIN_VERIFY='&lt;a 32+ character token-safe value&gt;' \
 * java -cp "target/classes:$(cat target/console-dev-classpath.txt)" \
 *      com.acquiring.simulator.console.dev.ConsoleDevHarness --port=8099
 * </pre>
 *
 * <p>The console route is mounted behind the real
 * {@link com.acquiring.simulator.console.ConsoleIntegrationEntryPoint}; the harness refuses to start
 * when {@code EDGE_ORIGIN_VERIFY} is absent or unusable and never falls back to an ungated path.
 * This local composition uses explicit development values for its own version label; it is not a
 * deployment.</p>
 *
 * <p>The critical-capacity figures below are the O8 <em>proposed</em> values already used by the
 * repository's tests. They are local development configuration, not owner-approved capacity.</p>
 */
public final class ConsoleDevHarness {

    private static final String INSTANCE = "BOOT-CONSOLE-DEV";
    private static final CriticalCapacity CAPACITY = new CriticalCapacity(5, 8);
    private static final String CALLER_ID = "SUB-DEMO";

    /** Local-only version label for the denial record; not a deployed artifact version. */
    private static final String DEV_APP_VERSION = "console-dev-local";

    private ConsoleDevHarness() {
    }

    public static void main(String[] args) throws Exception {
        ConsoleDevConfig config = ConsoleDevConfig.fromArgs(args, System.getenv());
        Clock clock = Clock.systemUTC();

        // Fail closed like the deployed Lambda: no value means no local composition.
        ConsoleOriginVerificationGate gate = ConsoleOriginVerificationGate.fromEnvironment();
        String edgeOriginVerify = System.getenv(ConsoleOriginVerificationGate.ENVIRONMENT_VARIABLE);

        DynamoDbClient db = DynamoDbClient.builder()
                .endpointOverride(URI.create(config.dynamoEndpoint()))
                .region(Region.of(config.awsRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create("local", "local")))
                .build();

        CountDownLatch stopped = new CountDownLatch(1);

        try (db) {
            ensureTable(db, config.journalTable());
            ensureTable(db, config.eventTable());

            EventManifest manifest = OperationAManifest.manifest(CAPACITY);
            DynamoAuthorizationRepository repository =
                    new DynamoAuthorizationRepository(db, config.journalTable());
            DynamoEventStore store = new DynamoEventStore(db, config.eventTable(), manifest,
                    new EventValidator(), clock, CriticalWriteComposer.NONE);
            DevIssuerSimulator issuerSimulator = new DevIssuerSimulator();

            SimulatorProperties properties = new SimulatorProperties(
                    new SimulatorProperties.Authorization(Duration.ofHours(24),
                            Duration.ofSeconds(2)),
                    new SimulatorProperties.Profile(
                            List.of(new SimulatorProperties.AcquirerFixture(config.acquirerRef(),
                                    List.of(Route.DOMESTIC))),
                            List.of(new SimulatorProperties.MerchantFixture(
                                    config.merchantRef(), config.acquirerRef())),
                            List.of(new SimulatorProperties.TerminalFixture(
                                    config.terminalRef(), config.merchantRef())),
                            List.of(
                                    new SimulatorProperties.CredentialRange("SIM-APP-",
                                            CredentialDecision.APPROVE, Route.DOMESTIC),
                                    new SimulatorProperties.CredentialRange("SIM-DEC-",
                                            CredentialDecision.DECLINE, Route.DOMESTIC),
                                    new SimulatorProperties.CredentialRange("SIM-TMO-",
                                            CredentialDecision.TIMEOUT, Route.DOMESTIC))));

            AuthorizationService service = new AuthorizationService(repository, issuerSimulator,
                    properties, clock,
                    new DynamoClaimGateway(store, repository, config.journalTable(),
                            config.eventTable(), CAPACITY, config.environmentId(), INSTANCE),
                    new DynamoDispatchIntentGateway(store, repository, config.journalTable(),
                            config.eventTable(), CAPACITY, config.environmentId(), INSTANCE,
                            new SyntheticInstructionMaterializer()),
                    new DynamoResultGateway(store, repository, config.journalTable(),
                            config.eventTable(), CAPACITY, config.environmentId(), INSTANCE),
                    IdentifierSource.randomHex());

            DevStubTokenIssuer tokenIssuer = new DevStubTokenIssuer(config.issuer(),
                    config.audience(), config.subject(), config.clientId(), config.scope(), clock);
            DemoConsoleAuthorizer authorizer = new DemoConsoleAuthorizer(config.issuer(),
                    config.audience(), config.subject(), config.clientId(), config.scope(),
                    tokenIssuer.decoder());

            AuthorizedContext demoContext = new AuthorizedContext(
                    config.acquirerRef(), config.merchantRef(), CALLER_ID, Set.of(Route.DOMESTIC));
            ConsoleAuthorizationRoute route = new ConsoleAuthorizationRoute(authorizer,
                    principal -> demoContext, service, ConsoleRateLimitHook.PERMISSIVE, clock);
            ConsoleIntegrationEntryPoint entryPoint = new ConsoleIntegrationEntryPoint(
                    gate, route::handle, new StandardStreamOriginDenialSink(System.out), clock,
                    DEV_APP_VERSION);

            try (ConsoleDevServer server = new ConsoleDevServer(
                    config, Path.of(config.staticRoot()), entryPoint, edgeOriginVerify,
                    tokenIssuer)) {
                server.start();
                Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                    server.close();
                    stopped.countDown();
                }, "console-dev-shutdown"));

                System.out.println("CONSOLE_DEV_READY " + server.url());
                System.out.println("CONSOLE_DEV_MODE dev-stub (NOT authentication; "
                        + "no Keycloak, no AWS, no relay)");
                System.out.println("CONSOLE_DEV_CONTEXT acq=" + config.acquirerRef()
                        + " merchant=" + config.merchantRef()
                        + " terminal=" + config.terminalRef()
                        + " currency=" + config.currency());
                System.out.println("CONSOLE_DEV_DYNAMODB " + config.dynamoEndpoint()
                        + " journal=" + config.journalTable()
                        + " events=" + config.eventTable());
                System.out.flush();

                stopped.await();
            }
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
}
