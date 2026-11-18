package com.acquiring.simulator.adapter.aws;

import com.amazonaws.services.lambda.runtime.*;
import com.acquiring.simulator.adapter.downstream.DeterministicIssuerSimulator;
import com.acquiring.simulator.domain.model.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

/** Issuer truth has separate IAM/table custody; processor cannot mistake it for delivered evidence. */
public final class IssuerHandler implements RequestStreamHandler {
    private final DynamoDbClient db;
    private final String table;
    public IssuerHandler() { this(AwsRuntime.db(), AwsRuntime.env("ISSUER_TRUTH_TABLE")); }
    public IssuerHandler(DynamoDbClient db, String table) { this.db = db; this.table = table; }
    public DownstreamReply decide(SimulatedDecisionRequest r) {
        var key = DynamoAuthorizationRepository.key("ISSUER#" + r.authorizationId(), "DECISION");
        var truth = new DeterministicIssuerSimulator().decide(r);
        var item = new HashMap<>(key);
        item.put("payload", DynamoAuthorizationRepository.text(WireJson.write(truth)));
        item.put("request", DynamoAuthorizationRepository.text(WireJson.write(r)));
        try {
            db.putItem(PutItemRequest.builder().tableName(table).item(item).conditionExpression("attribute_not_exists(pk)").build());
            return truth;
        } catch (ConditionalCheckFailedException duplicate) {
            var existing = db.getItem(GetItemRequest.builder().tableName(table).key(key).consistentRead(true).build()).item();
            if (!WireJson.write(r).equals(existing.get("request").s())) throw new IllegalArgumentException("conflicting issuer instruction");
            return WireJson.read(existing.get("payload").s(), DownstreamReply.class);
        }
    }
    @Override public void handleRequest(InputStream in, OutputStream out, Context ctx) throws IOException {
        var r = WireJson.read(new String(in.readNBytes(65537), StandardCharsets.UTF_8), SimulatedDecisionRequest.class);
        out.write(WireJson.write(decide(r)).getBytes(StandardCharsets.UTF_8));
    }
}
