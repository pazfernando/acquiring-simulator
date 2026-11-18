package com.acquiring.simulator.adapter.aws;

import com.acquiring.simulator.domain.model.*;
import com.acquiring.simulator.domain.port.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import java.util.function.UnaryOperator;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

/** Single-table journal. Strong reads and transactional claim/head prevent double dispatch. */
public final class DynamoAuthorizationRepository implements AuthorizationRepository {
    private final DynamoDbClient db;
    private final String table;
    public DynamoAuthorizationRepository(DynamoDbClient db, String table) {
        this.db = db;
        this.table = Objects.requireNonNull(table);
    }
    public static AttributeValue text(String s) { return AttributeValue.builder().s(s).build(); }
    public static Map<String, AttributeValue> key(String pk, String sk) {
        return Map.of("pk", text(pk), "sk", text(sk));
    }
    private Map<String, AttributeValue> item(String pk, String sk, Object payload) {
        var item = new HashMap<>(key(pk, sk));
        item.put("payload", text(WireJson.write(payload)));
        return item;
    }
    private Optional<Map<String, AttributeValue>> get(String pk, String sk) {
        var item = db.getItem(GetItemRequest.builder().tableName(table).key(key(pk, sk))
                .consistentRead(true).build()).item();
        return item == null || item.isEmpty() ? Optional.empty() : Optional.of(item);
    }
    @Override public void claim(AuthorizationRecord r) {
        var record = item("AUTH#" + r.id(), "META", r);
        record.put("version", AttributeValue.builder().n("0").build());
        var head = new HashMap<>(key("BIZ#" + digest(r.businessKey()), "HEAD"));
        head.put("id", text(r.id()));
        head.put("generation", AttributeValue.builder().n(Integer.toString(r.generation())).build());
        var headPut = Put.builder().tableName(table).item(head)
                .conditionExpression(r.generation() == 1 ? "attribute_not_exists(pk)" : "generation = :previous");
        if (r.generation() > 1) headPut.expressionAttributeValues(Map.of(":previous",
                AttributeValue.builder().n(Integer.toString(r.generation() - 1)).build()));
        try {
            db.transactWriteItems(TransactWriteItemsRequest.builder()
                    .clientRequestToken(r.id())
                    .transactItems(
                        TransactWriteItem.builder().put(Put.builder().tableName(table).item(record)
                                .conditionExpression("attribute_not_exists(pk)").build()).build(),
                        TransactWriteItem.builder().put(headPut.build()).build()).build());
        } catch (TransactionCanceledException e) {
            if (e.cancellationReasons().stream().anyMatch(c -> "ConditionalCheckFailed".equals(c.code()))) {
                throw new DuplicateClaimException(r.businessKey(), r.generation(), e);
            }
            throw e; // throttling, authorization and outage are NOT duplicate claims
        }
    }
    @Override public void markDispatched(String id, String correlation, Instant at) {
        mutate(id, r -> {
            if (r.dispatchedAt() != null) throw new IllegalStateException("already dispatched");
            return r.withDispatch(correlation, at);
        });
    }
    @Override public void updateOutcome(String id, String state, String source, String reason, Instant at) {
        mutate(id, r -> {
            if (r.state() == AuthorizationState.APPROVED || r.state() == AuthorizationState.DECLINED
                    || r.state() == AuthorizationState.NETWORK_REJECTED) return r;
            return r.withOutcome(AuthorizationState.valueOf(state), DecisionSource.valueOf(source), reason, at);
        });
    }
    private void mutate(String id, UnaryOperator<AuthorizationRecord> change) {
        for (int attempt = 0; attempt < 8; attempt++) {
            var old = get("AUTH#" + id, "META").orElseThrow(() -> new IllegalArgumentException("unknown reference"));
            var r = WireJson.read(old.get("payload").s(), AuthorizationRecord.class);
            var changed = change.apply(r);
            if (changed.equals(r)) return;
            long version = Long.parseLong(old.get("version").n());
            var next = item("AUTH#" + id, "META", changed);
            next.put("version", AttributeValue.builder().n(Long.toString(version + 1)).build());
            try {
                db.putItem(PutItemRequest.builder().tableName(table).item(next)
                        .conditionExpression("#v = :v").expressionAttributeNames(Map.of("#v", "version"))
                        .expressionAttributeValues(Map.of(":v", AttributeValue.builder().n(Long.toString(version)).build()))
                        .build());
                return;
            } catch (ConditionalCheckFailedException contention) { /* reread, never blind overwrite */ }
        }
        throw new IllegalStateException("journal contention exhausted");
    }
    @Override public Optional<AuthorizationRecord> findById(String id) {
        return get("AUTH#" + id, "META").map(i -> WireJson.read(i.get("payload").s(), AuthorizationRecord.class));
    }
    @Override public Optional<AuthorizationRecord> findLatestByBusinessKey(String businessKey) {
        return get("BIZ#" + digest(businessKey), "HEAD").flatMap(i -> findById(i.get("id").s()));
    }
    @Override public List<EvidenceEvent> eventsFor(String id) {
        var events = new ArrayList<EvidenceEvent>();
        Map<String, AttributeValue> cursor = Map.of();
        do {
            var query = QueryRequest.builder().tableName(table).consistentRead(true)
                    .keyConditionExpression("pk = :pk AND begins_with(sk, :prefix)")
                    .expressionAttributeValues(Map.of(":pk", text("AUTH#" + id), ":prefix", text("EVT#")))
                    ;
            if (cursor != null && !cursor.isEmpty()) query.exclusiveStartKey(cursor);
            var page = db.query(query.build());
            page.items().forEach(i -> events.add(WireJson.read(i.get("payload").s(), EvidenceEvent.class)));
            cursor = page.lastEvaluatedKey();
        } while (cursor != null && !cursor.isEmpty());
        return List.copyOf(events);
    }
    @Override public void appendEvent(EvidenceEvent e) {
        append(item("AUTH#" + e.authorizationId(), "EVT#" + String.format(Locale.ROOT, "%013d", e.recordedAt()) + "#" + e.id(), e));
    }
    @Override public void appendRejection(RejectionAudit r) { append(item("REJ#" + r.id(), "META", r)); }
    private void append(Map<String, AttributeValue> item) {
        db.putItem(PutItemRequest.builder().tableName(table).item(item)
                .conditionExpression("attribute_not_exists(pk)").build());
    }
    private static String digest(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
