package com.acquiring.simulator.adapter.aws;

import com.acquiring.simulator.config.ResponseTimeoutConfig;
import com.acquiring.simulator.config.SimulatorProperties;
import com.acquiring.simulator.domain.AuthorizationService;
import com.acquiring.simulator.domain.model.AuthorizedContext;
import java.time.*;
import software.amazon.awssdk.core.retry.RetryPolicy;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.lambda.LambdaClient;

/** Cold-start wiring; Spring/JDBC remain the optional local regression harness. */
final class AwsRuntime {
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
        return new AuthorizationService(new DynamoAuthorizationRepository(db, env("JOURNAL_TABLE")),
                new LambdaDownstream(lambda(), env("MASTERCARD_FUNCTION")), props, Clock.systemUTC());
    }
    static AuthorizedContext caller(String subject) { return registry(db(), "CALLER#" + subject, AuthorizedContext.class); }
}
