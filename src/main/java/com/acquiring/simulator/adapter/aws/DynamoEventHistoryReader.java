package com.acquiring.simulator.adapter.aws;

import com.acquiring.simulator.domain.event.EventContext;
import com.acquiring.simulator.domain.event.EventDurability;
import com.acquiring.simulator.domain.event.EventSourceWrapper;
import com.acquiring.simulator.domain.event.EventTableKeys;
import com.acquiring.simulator.domain.event.RecordedEvent;
import com.acquiring.simulator.domain.port.EventHistoryReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;

/**
 * Strongly consistent range reader over the dedicated functional EVENT table.
 *
 * <p>It reads the exact rows written by {@link DynamoEventStore} — canonical payload plus collector
 * wrapper attributes — and returns them in ascending source order. Nothing is copied, rewritten,
 * projected into a second table or interpreted as financial authority. The partition is derived from
 * the environment-bound {@link EventContext}; the caller cannot address a foreign partition.</p>
 *
 * <p>Consistent reads are deliberate: a catch-up binds a fixed snapshot head, so an eventually
 * consistent read could silently omit an already-retained event. Pagination is by source sequence,
 * not by the table's last-evaluated key, so the returned rows always end on a resumable boundary.</p>
 *
 * <p>Out of scope: HEAD resolution (the existing {@code EventStore.head} is the strong head read),
 * retention/expiry mutation, cursor signing, notification and dashboard authority labelling.</p>
 */
public final class DynamoEventHistoryReader implements EventHistoryReader {

    /** Hard upper bound per read; the observation contract proposes pages of at most 100. */
    public static final int MAX_LIMIT = 256;

    private final DynamoDbClient db;
    private final String table;

    public DynamoEventHistoryReader(DynamoDbClient db, String table) {
        this.db = Objects.requireNonNull(db, "db");
        if (table == null || table.isBlank()) {
            throw new IllegalArgumentException("table is required");
        }
        this.table = table;
    }

    @Override
    public List<RecordedEvent> readRange(
            EventContext context, long afterSequence, long throughSequence, int limit) {
        Objects.requireNonNull(context, "context");
        if (afterSequence < 0) {
            throw new IllegalArgumentException("afterSequence cannot be negative");
        }
        if (throughSequence < afterSequence) {
            return List.of();
        }
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new IllegalArgumentException("limit must be 1.." + MAX_LIMIT);
        }

        QueryResponse page = db.query(QueryRequest.builder()
                .tableName(table)
                .consistentRead(true)
                .keyConditionExpression("pk = :pk AND sk BETWEEN :from AND :to")
                .expressionAttributeValues(Map.of(
                        ":pk", text(EventTableKeys.eventPartition(context)),
                        ":from", text(EventTableKeys.eventSortKey(afterSequence + 1)),
                        ":to", text(EventTableKeys.eventSortKey(throughSequence))))
                .limit(limit)
                .build());

        List<RecordedEvent> rows = new ArrayList<>(page.items().size());
        for (Map<String, AttributeValue> item : page.items()) {
            // HEAD never falls inside the E# range, but a malformed row is skipped rather than
            // treated as an event: the source is not silently patched.
            if (!item.containsKey("payload") || !item.containsKey("sourceSequence")) {
                continue;
            }
            rows.add(new RecordedEvent(wrapper(item), item.get("payload").s()));
        }
        rows.sort(java.util.Comparator.comparingLong(RecordedEvent::sourceSequence));
        return List.copyOf(rows);
    }

    private static EventSourceWrapper wrapper(Map<String, AttributeValue> item) {
        Long journalVersion = item.containsKey("journalVersion")
                ? Long.valueOf(number(item.get("journalVersion")))
                : null;
        return new EventSourceWrapper(
                EventDurability.valueOf(string(item, "deliveryClass")),
                string(item, "eventSha256"),
                journalVersion,
                optionalString(item, "committedAt"),
                string(item, "collectedAt"),
                number(item.get("sourceSequence")),
                optionalString(item, "storedAt"),
                optionalString(item, "stateAfter"));
    }

    private static String string(Map<String, AttributeValue> item, String name) {
        AttributeValue value = item.get(name);
        if (value == null || value.s() == null) {
            throw new IllegalStateException("event row is missing " + name);
        }
        return value.s();
    }

    private static String optionalString(Map<String, AttributeValue> item, String name) {
        AttributeValue value = item.get(name);
        return value == null ? null : value.s();
    }

    private static long number(AttributeValue value) {
        if (value == null || value.n() == null) {
            throw new IllegalStateException("event row is missing a numeric attribute");
        }
        return Long.parseLong(value.n());
    }

    private static AttributeValue text(String value) {
        return AttributeValue.builder().s(value).build();
    }
}
