package com.acquiring.simulator.adapter.aws;

import com.acquiring.simulator.channel.SyntheticInstructionMaterializer;
import com.acquiring.simulator.config.ResponseTimeoutConfig;
import com.acquiring.simulator.config.SimulatorProperties;
import com.acquiring.simulator.domain.AuthorizationService;
import com.acquiring.simulator.domain.IdentifierSource;
import com.acquiring.simulator.domain.event.CriticalCapacity;
import com.acquiring.simulator.domain.event.EventValidator;
import com.acquiring.simulator.domain.event.OperationAManifest;
import com.acquiring.simulator.domain.model.AuthorizedContext;
import com.acquiring.simulator.domain.port.IssuerSimulator;
import java.time.*;
import java.util.function.Function;
import software.amazon.awssdk.core.retry.RetryPolicy;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.lambda.LambdaClient;

/**
 * Cold-start wiring.
 *
 * <p>{@link #processor()} installs the complete atomic service: the claim, dispatch-intent, result,
 * network-result and late-evidence gateways plus the functional event store. The legacy four-argument
 * {@code AuthorizationService} remains only for the Spring/JDBC local regression harness and for
 * explicit test construction; no atomic runtime invocation reaches it.</p>
 *
 * <p>Every input the atomic composition needs is explicit. {@code JOURNAL_TABLE},
 * {@code EVENT_TABLE}, {@code ENVIRONMENT}, {@code BOOT_INSTANCE_ID} and the two critical-capacity
 * values have no defaults: an absent or invalid value fails the cold start with a message naming the
 * exact variable, before any authorization is evaluated. The O8 capacity numbers are not approved,
 * so the runtime never chooses them.</p>
 */
final class AwsRuntime {

    /** Explicit atomic-composition configuration; nothing here is defaulted. */
    record AtomicConfig(String eventTable, String environment, String instanceId,
            CriticalCapacity capacity) {

        AtomicConfig {
            if (eventTable == null || eventTable.isBlank()) {
                throw new IllegalStateException("missing environment EVENT_TABLE");
            }
            if (environment == null || environment.isBlank()) {
                throw new IllegalStateException("missing environment ENVIRONMENT");
            }
            if (instanceId == null || instanceId.isBlank()) {
                throw new IllegalStateException("missing environment BOOT_INSTANCE_ID");
            }
            if (capacity == null) {
                throw new IllegalStateException("critical capacity is required");
            }
        }

        /** Resolve from the process environment; absent capacity values make cold start fail. */
        static AtomicConfig fromEnvironment() {
            return from(System::getenv);
        }

        /** Resolver seam so fail-closed behaviour can be asserted without mutating the process. */
        static AtomicConfig from(Function<String, String> source) {
            String eventTable = required(source, "EVENT_TABLE");
            String environment = required(source, "ENVIRONMENT");
            String instanceId = required(source, "BOOT_INSTANCE_ID");
            int maxEvents = capacity(source, "CRITICAL_MAX_EVENTS_PER_OPERATION");
            int slotBudget = capacity(source, "CRITICAL_SLOT_BUDGET");
            return new AtomicConfig(eventTable, environment, instanceId,
                    new CriticalCapacity(maxEvents, slotBudget));
        }

        private static String required(Function<String, String> source, String name) {
            String value = source.apply(name);
            if (value == null || value.isBlank()) {
                throw new IllegalStateException("missing environment " + name);
            }
            return value.trim();
        }

        private static int capacity(Function<String, String> source, String name) {
            String raw = source.apply(name);
            if (raw == null || raw.isBlank()) {
                throw new IllegalStateException("missing environment " + name);
            }
            String trimmed = raw.trim();
            int value;
            try {
                value = Integer.parseInt(trimmed);
            } catch (NumberFormatException notANumber) {
                throw new IllegalStateException(name
                        + " must be a whole number but was '" + raw + "'", notANumber);
            }
            if (value < 0) {
                throw new IllegalStateException(name
                        + " must be non-negative but was '" + raw + "'");
            }
            return value;
        }
    }

    private static class DynamoHolder { static final DynamoDbClient CLIENT = DynamoDbClient.create(); }
    private static class LambdaHolder { static final LambdaClient CLIENT = createLambda(); }
    static DynamoDbClient db() { return DynamoHolder.CLIENT; }
    static LambdaClient lambda() { return LambdaHolder.CLIENT; }
    private static LambdaClient createLambda() {
        return LambdaClient.builder().overrideConfiguration(c -> c.retryPolicy(RetryPolicy.none())
                .apiCallTimeout(Duration.ofSeconds(4))).build();
    }
    static String env(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalStateException("missing environment " + name);
        return value;
    }
    static <T> T registry(DynamoDbClient db, String pk, Class<T> type) {
        var item = db.getItem(GetItemRequest.builder().tableName(env("JOURNAL_TABLE"))
                .key(DynamoAuthorizationRepository.key(pk, "PROFILE")).consistentRead(true).build()).item();
        if (item == null || !item.containsKey("payload")) throw new IllegalArgumentException("profile unavailable");
        return WireJson.read(item.get("payload").s(), type);
    }
    static AuthorizationService processor() {
        var db = db();
        var profile = registry(db, "CONFIG#ACTIVE", SimulatorProperties.Profile.class);
        var props = new SimulatorProperties(new SimulatorProperties.Authorization(Duration.ofHours(24),
                ResponseTimeoutConfig.resolve(
                        System.getenv(ResponseTimeoutConfig.RESPONSE_TIMEOUT_MILLIS_VARIABLE))), profile);
        String journalTable = env("JOURNAL_TABLE");
        return atomicService(db, new DynamoAuthorizationRepository(db, journalTable),
                new LambdaDownstream(lambda(), env("MASTERCARD_FUNCTION")), journalTable, props,
                Clock.systemUTC(), AtomicConfig.fromEnvironment());
    }

    /**
     * Build the one atomic authorization graph. Package-private so a local integrated test can
     * execute the same composition against DynamoDB Local with a stub issuer; production
     * {@link #processor()} supplies the Lambda downstream.
     */
    static AuthorizationService atomicService(DynamoDbClient db,
            DynamoAuthorizationRepository repository, IssuerSimulator issuer, String journalTable,
            SimulatorProperties properties, Clock clock, AtomicConfig config) {
        CriticalCapacity capacity = config.capacity();
        String eventTable = config.eventTable();
        String environment = config.environment();
        String instanceId = config.instanceId();

        DynamoEventStore store = new DynamoEventStore(db, eventTable,
                OperationAManifest.manifest(capacity), new EventValidator(), clock,
                CriticalWriteComposer.NONE);

        return new AuthorizationService(repository, issuer, properties, clock,
                new DynamoClaimGateway(store, repository, journalTable, eventTable, capacity,
                        environment, instanceId),
                new DynamoDispatchIntentGateway(store, repository, journalTable, eventTable,
                        capacity, environment, instanceId, new SyntheticInstructionMaterializer()),
                new DynamoResultGateway(store, repository, journalTable, eventTable, capacity,
                        environment, instanceId),
                new DynamoNetworkResultGateway(store, repository, journalTable, eventTable, capacity,
                        environment, instanceId),
                LateEvidenceAdmissionGateway.over(store, repository, journalTable, eventTable,
                        capacity, environment, instanceId),
                IdentifierSource.randomHex());
    }

    static AuthorizedContext caller(String subject) { return registry(db(), "CALLER#" + subject, AuthorizedContext.class); }
}
