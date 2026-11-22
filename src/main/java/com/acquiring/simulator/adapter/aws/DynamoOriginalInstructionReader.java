package com.acquiring.simulator.adapter.aws;

import com.acquiring.simulator.domain.port.OriginalInstructionReader;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;

/**
 * DynamoDB strong reader for the exact persisted original {@code 0100} instruction.
 *
 * <p>The original is a separate journal item under {@code AUTH#<authorizationId>} /
 * {@code ORIGINAL}, so it is written once by the ACL/claim path and is never mixed with the mutable
 * business record. The read is strongly consistent and returns the stored binary attribute exactly;
 * no parsing, re-encoding or hash reconstruction happens here.</p>
 *
 * <p>The write side is deliberately absent from this slice. A caller that persists an original must
 * place the exact accepted bytes in {@link #ORIGINAL_BYTES_ATTRIBUTE} under {@link #originalKey}
 * atomically with the claim. This class exposes the shared key scheme so a future writer cannot
 * drift from the reader.</p>
 *
 * <p>Not referenced by {@code AuthorizationService} or {@code AwsRuntime}; no runtime activation,
 * key custody, TLS/IAM mapping or production storage policy is implied.</p>
 */
public final class DynamoOriginalInstructionReader implements OriginalInstructionReader {

    /** Journal sort key of the persisted original instruction. */
    public static final String ORIGINAL_SORT_KEY = "ORIGINAL";
    /** Binary journal attribute carrying the exact accepted {@code 0100} octets. */
    public static final String ORIGINAL_BYTES_ATTRIBUTE = "original0100";

    private final DynamoDbClient db;
    private final String journalTable;

    public DynamoOriginalInstructionReader(DynamoDbClient db, String journalTable) {
        this.db = Objects.requireNonNull(db, "db");
        this.journalTable = requireText(journalTable, "journalTable");
    }

    /**
     * Shared journal key of the persisted original. The authorization identifier is the same
     * bounded profile identifier the business record uses.
     */
    public static Map<String, AttributeValue> originalKey(String authorizationId) {
        return Map.of(
                "pk", DynamoAuthorizationRepository.text("AUTH#" + requireText(
                        authorizationId, "authorizationId")),
                "sk", DynamoAuthorizationRepository.text(ORIGINAL_SORT_KEY));
    }

    @Override
    public Optional<byte[]> readPersistedOriginal0100(String authorizationId) {
        Map<String, AttributeValue> item = db.getItem(GetItemRequest.builder()
                        .tableName(journalTable)
                        .key(originalKey(authorizationId))
                        .consistentRead(true)
                        .build())
                .item();
        if (item == null || item.isEmpty()) {
            return Optional.empty();
        }
        AttributeValue value = item.get(ORIGINAL_BYTES_ATTRIBUTE);
        if (value == null || value.b() == null) {
            throw new IllegalStateException(
                    "persisted original instruction is missing " + ORIGINAL_BYTES_ATTRIBUTE);
        }
        byte[] bytes = value.b().asByteArray();
        if (bytes.length == 0) {
            throw new IllegalStateException("persisted original instruction is empty");
        }
        return Optional.of(bytes);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value;
    }
}
