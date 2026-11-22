package com.acquiring.simulator.domain.event;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * RFC 8785 (JCS) serialization of the closed canonical event payload.
 *
 * <p>Key order is the RFC 8785 UTF-16 code-unit order of the fixed field names, every optional
 * value appears as null, and only integers and strings occur, so the output is byte-stable.
 * The oracle is {@code vectors/event-vectors.json}: EV1 canonicalSha256
 * ed1044b9562b6cb62da04bc541a5a6eaab9df2ed321f8ccfd65b2f2a298687b5.
 */
public final class CanonicalEventCodec {

    private CanonicalEventCodec() {
    }

    public static String canonicalJson(CanonicalEvent event) {
        StringBuilder json = new StringBuilder(1024).append('{');
        appendString(json, "acquirerRef", event.acquirerRef()).append(',');
        appendString(json, "actor", name(event.actor())).append(',');
        appendString(json, "attemptId", event.attemptId()).append(',');
        appendString(json, "capability", event.capability()).append(',');
        appendString(json, "clockQuality", name(event.clockQuality())).append(',');
        appendString(json, "correlationId", event.correlationId()).append(',');
        appendNumber(json, "elapsedMicros", event.elapsedMicros()).append(',');
        appendString(json, "endedAt", event.endedAt()).append(',');
        appendString(json, "eventId", event.eventId()).append(',');
        appendString(json, "evidenceRef", event.evidenceRef()).append(',');
        appendString(json, "gapCode", name(event.gapCode())).append(',');
        appendString(json, "instanceId", event.instanceId()).append(',');
        appendString(json, "manifestVersion", event.manifestVersion()).append(',');
        appendString(json, "merchantRef", event.merchantRef()).append(',');
        appendString(json, "occurredAt", event.occurredAt()).append(',');
        appendString(json, "operationId", event.operationId()).append(',');
        appendString(json, "originalOperationId", event.originalOperationId()).append(',');
        appendString(json, "parentEventId", event.parentEventId()).append(',');
        appendString(json, "parentTaskId", event.parentTaskId()).append(',');
        appendString(json, "producerId", event.producerId()).append(',');
        json.append("\"producerSequence\":").append(event.producerSequence()).append(',');
        appendString(json, "provenance", name(event.provenance())).append(',');
        appendString(json, "reason", name(event.reason())).append(',');
        appendString(json, "retryOfAttemptId", event.retryOfAttemptId()).append(',');
        json.append("\"schemaVersion\":").append(event.schemaVersion()).append(',');
        appendString(json, "startedAt", event.startedAt()).append(',');
        appendString(json, "status", name(event.status())).append(',');
        appendString(json, "taskExecutionId", event.taskExecutionId()).append(',');
        appendString(json, "taskName", event.taskName() == null ? null : event.taskName().wireName()).append(',');
        appendString(json, "terminalRef", event.terminalRef()).append(',');
        appendString(json, "transactionId", event.transactionId()).append(',');
        appendString(json, "version", event.version());
        return json.append('}').toString();
    }

    public static byte[] canonicalBytes(CanonicalEvent event) {
        return canonicalJson(event).getBytes(StandardCharsets.UTF_8);
    }

    /** Lowercase hex SHA-256 of the canonical payload; the wrapper carries this value. */
    public static String sha256(CanonicalEvent event) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(canonicalBytes(event)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private static String name(Enum<?> value) {
        return value == null ? null : value.name();
    }

    private static StringBuilder appendString(StringBuilder json, String key, String value) {
        json.append('"').append(key).append("\":");
        if (value == null) {
            return json.append("null");
        }
        json.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> json.append("\\\"");
                case '\\' -> json.append("\\\\");
                case '\b' -> json.append("\\b");
                case '\t' -> json.append("\\t");
                case '\n' -> json.append("\\n");
                case '\f' -> json.append("\\f");
                case '\r' -> json.append("\\r");
                default -> {
                    if (c < 0x20) {
                        json.append(String.format("\\u%04x", (int) c));
                    } else {
                        json.append(c);
                    }
                }
            }
        }
        return json.append('"');
    }

    private static StringBuilder appendNumber(StringBuilder json, String key, Long value) {
        return json.append('"').append(key).append("\":")
                .append(value == null ? "null" : value.toString());
    }
}
