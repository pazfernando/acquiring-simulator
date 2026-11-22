package com.acquiring.simulator.domain.event;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Strict ingress parser for the canonical payload. Undeclared fields are rejected and collector
 * fields are named explicitly so a producer cannot supply collectedAt, ingestionSequence or
 * collectorReceiptId. Absent optional values normalize to null.
 */
public final class CanonicalEventJson {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final Set<String> DECLARED = new LinkedHashSet<>(Set.of(
            "schemaVersion", "eventId", "producerId", "instanceId", "actor", "capability",
            "version", "manifestVersion", "acquirerRef", "merchantRef", "terminalRef",
            "transactionId", "operationId", "attemptId", "correlationId", "taskExecutionId",
            "taskName", "status", "occurredAt", "producerSequence", "provenance", "reason",
            "parentTaskId", "parentEventId", "retryOfAttemptId", "originalOperationId",
            "startedAt", "endedAt", "elapsedMicros", "clockQuality", "evidenceRef", "gapCode"));

    private static final Set<String> COLLECTOR_FIELDS = Set.of(
            "collectedAt", "ingestionSequence", "collectorReceiptId");

    private CanonicalEventJson() {
    }

    public static CanonicalEvent parse(String json) {
        return parse(json, null);
    }

    /** Parse and, when a manifest is supplied, admit the event. */
    public static CanonicalEvent parse(String json, EventManifest manifest) {
        JsonNode root;
        try {
            root = MAPPER.readTree(json);
        } catch (Exception e) {
            throw new EventValidationException(EventViolation.INVALID_FORMAT, "payload", "invalid JSON");
        }
        if (root == null || !root.isObject()) {
            throw new EventValidationException(EventViolation.INVALID_FORMAT, "payload", "object required");
        }
        root.fieldNames().forEachRemaining(field -> {
            if (COLLECTOR_FIELDS.contains(field)) {
                throw new EventValidationException(EventViolation.COLLECTOR_FIELD_FORBIDDEN, field, null);
            }
            if (!DECLARED.contains(field)) {
                throw new EventValidationException(EventViolation.UNDECLARED_FIELD, field, null);
            }
        });

        CanonicalEvent event = CanonicalEvent.builder()
                .schemaVersion(requiredInt(root, "schemaVersion"))
                .eventId(text(root, "eventId"))
                .producerId(text(root, "producerId"))
                .instanceId(text(root, "instanceId"))
                .actor(enumValue(root, "actor", EventActor.class))
                .capability(text(root, "capability"))
                .version(text(root, "version"))
                .manifestVersion(text(root, "manifestVersion"))
                .acquirerRef(text(root, "acquirerRef"))
                .merchantRef(text(root, "merchantRef"))
                .terminalRef(text(root, "terminalRef"))
                .transactionId(text(root, "transactionId"))
                .operationId(text(root, "operationId"))
                .attemptId(text(root, "attemptId"))
                .correlationId(text(root, "correlationId"))
                .taskExecutionId(text(root, "taskExecutionId"))
                .taskName(task(root, "taskName"))
                .status(enumValue(root, "status", EventStatus.class))
                .occurredAt(text(root, "occurredAt"))
                .producerSequence(requiredLong(root, "producerSequence"))
                .provenance(enumValue(root, "provenance", EventProvenance.class))
                .reason(enumValue(root, "reason", EventReason.class))
                .parentTaskId(text(root, "parentTaskId"))
                .parentEventId(text(root, "parentEventId"))
                .retryOfAttemptId(text(root, "retryOfAttemptId"))
                .originalOperationId(text(root, "originalOperationId"))
                .startedAt(text(root, "startedAt"))
                .endedAt(text(root, "endedAt"))
                .elapsedMicros(optionalLong(root, "elapsedMicros"))
                .clockQuality(enumValue(root, "clockQuality", EventClockQuality.class))
                .evidenceRef(text(root, "evidenceRef"))
                .gapCode(enumValue(root, "gapCode", EventGapCode.class))
                .build();

        if (manifest != null) {
            new EventValidator().validate(event, manifest);
        }
        return event;
    }

    private static String text(JsonNode root, String field) {
        JsonNode node = root.get(field);
        if (node == null || node.isNull()) {
            return null;
        }
        if (!node.isTextual()) {
            throw new EventValidationException(EventViolation.INVALID_FORMAT, field, "string required");
        }
        return node.asText();
    }

    private static int requiredInt(JsonNode root, String field) {
        JsonNode node = root.get(field);
        if (node == null || !node.isIntegralNumber() || !node.canConvertToInt()) {
            throw new EventValidationException(EventViolation.INVALID_FORMAT, field, "integer required");
        }
        return node.asInt();
    }

    private static long requiredLong(JsonNode root, String field) {
        JsonNode node = root.get(field);
        if (node == null || !node.isIntegralNumber() || !node.canConvertToLong()) {
            throw new EventValidationException(EventViolation.INVALID_FORMAT, field, "integer required");
        }
        return node.asLong();
    }

    private static Long optionalLong(JsonNode root, String field) {
        JsonNode node = root.get(field);
        if (node == null || node.isNull()) {
            return null;
        }
        if (!node.isIntegralNumber() || !node.canConvertToLong()) {
            throw new EventValidationException(EventViolation.INVALID_FORMAT, field, "integer required");
        }
        return node.asLong();
    }

    private static <E extends Enum<E>> E enumValue(JsonNode root, String field, Class<E> type) {
        String value = text(root, field);
        if (value == null) {
            return null;
        }
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException e) {
            throw new EventValidationException(EventViolation.INVALID_ENUM, field, null);
        }
    }

    private static EventTask task(JsonNode root, String field) {
        String value = text(root, field);
        if (value == null) {
            return null;
        }
        try {
            return EventTask.fromWire(value);
        } catch (IllegalArgumentException e) {
            throw new EventValidationException(EventViolation.UNKNOWN_TASK, field, null);
        }
    }
}
