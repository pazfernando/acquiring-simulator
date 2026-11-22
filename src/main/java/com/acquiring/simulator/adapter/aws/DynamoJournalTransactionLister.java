package com.acquiring.simulator.adapter.aws;

import com.acquiring.simulator.domain.model.AuthorizationRecord;
import com.acquiring.simulator.domain.port.TransactionLister;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ScanRequest;
import software.amazon.awssdk.services.dynamodb.model.ScanResponse;

/**
 * Bounded journal listing for the local presenter history.
 *
 * <p>Reads a page of journal items whose sort key is {@code META} (one per authorization) and parses
 * the stored {@link AuthorizationRecord}. Filtering by {@code firstReceivedAt} happens after parsing
 * because that value lives inside the record payload; the scan is hard-bounded so a large table
 * cannot turn one page load into an unbounded read.</p>
 *
 * <p>This is a local demo read model, not an approved production index: the contract's optional list
 * index/GSI remains a T4/T8 deliverable. It never writes, never bypasses authorization and returns
 * only allowlisted synthetic fields that the caller re-reads from the journal.</p>
 */
public final class DynamoJournalTransactionLister implements TransactionLister {

    private static final int MAX_SCAN_PAGE = 200;

    private final DynamoDbClient db;
    private final String table;

    public DynamoJournalTransactionLister(DynamoDbClient db, String table) {
        this.db = Objects.requireNonNull(db, "db");
        if (table == null || table.isBlank()) {
            throw new IllegalArgumentException("table is required");
        }
        this.table = table;
    }

    @Override
    public List<AuthorizationRecord> recentAuthorizations(Instant since, int maxRecords) {
        Objects.requireNonNull(since, "since");
        if (maxRecords < 1 || maxRecords > 500) {
            throw new IllegalArgumentException("maxRecords must be 1..500");
        }
        List<AuthorizationRecord> records = new ArrayList<>();
        Map<String, AttributeValue> cursor = Map.of();
        int scanned = 0;
        do {
            ScanRequest.Builder scan = ScanRequest.builder()
                    .tableName(table)
                    .consistentRead(true)
                    .filterExpression("#sk = :meta AND begins_with(#pk, :auth)")
                    .expressionAttributeNames(Map.of("#sk", "sk", "#pk", "pk"))
                    .expressionAttributeValues(Map.of(
                            ":meta", text("META"),
                            ":auth", text("AUTH#")))
                    .limit(MAX_SCAN_PAGE);
            if (!cursor.isEmpty()) {
                scan.exclusiveStartKey(cursor);
            }
            ScanResponse page = db.scan(scan.build());
            for (Map<String, AttributeValue> item : page.items()) {
                AttributeValue payload = item.get("payload");
                if (payload == null || payload.s() == null) {
                    continue;
                }
                AuthorizationRecord record =
                        WireJson.read(payload.s(), AuthorizationRecord.class);
                if (record.firstReceivedAt() != null && !record.firstReceivedAt().isBefore(since)) {
                    records.add(record);
                    if (records.size() >= maxRecords) {
                        return List.copyOf(records);
                    }
                }
            }
            scanned += page.scannedCount() == null ? 0 : page.scannedCount();
            cursor = page.lastEvaluatedKey();
        } while (cursor != null && !cursor.isEmpty() && scanned < MAX_SCAN_PAGE * 5);
        return List.copyOf(records);
    }

    private static AttributeValue text(String value) {
        return AttributeValue.builder().s(value).build();
    }
}
