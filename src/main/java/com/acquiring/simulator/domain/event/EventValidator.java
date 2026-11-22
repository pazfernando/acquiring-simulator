package com.acquiring.simulator.domain.event;

import java.time.Instant;
import java.time.format.DateTimeParseException;

/**
 * Admission checks for the canonical payload against a versioned manifest. Validation states no
 * business outcome: a valid event says a task was observed, never that a payment succeeded.
 */
public final class EventValidator {

    public static final int MAX_CANONICAL_BYTES = 2048;
    private static final int SCHEMA_VERSION = 1;

    public void validate(CanonicalEvent event, EventManifest manifest) {
        if (event.schemaVersion() != SCHEMA_VERSION) {
            throw new EventValidationException(EventViolation.INVALID_FORMAT, "schemaVersion",
                    "unsupported schema version");
        }

        requiredText("eventId", event.eventId(), 64);
        requiredText("producerId", event.producerId(), 64);
        requiredText("instanceId", event.instanceId(), 64);
        requiredText("transactionId", event.transactionId(), 64);
        requiredText("operationId", event.operationId(), 64);
        requiredText("attemptId", event.attemptId(), 64);
        requiredText("correlationId", event.correlationId(), 64);
        requiredText("taskExecutionId", event.taskExecutionId(), 64);
        requiredText("acquirerRef", event.acquirerRef(), 128);
        requiredText("merchantRef", event.merchantRef(), 128);
        requiredText("terminalRef", event.terminalRef(), 128);
        requiredText("capability", event.capability(), 32);
        requiredText("version", event.version(), 32);
        requiredText("manifestVersion", event.manifestVersion(), 32);
        optionalText("parentTaskId", event.parentTaskId(), 64);
        optionalText("parentEventId", event.parentEventId(), 64);
        optionalText("retryOfAttemptId", event.retryOfAttemptId(), 64);
        optionalText("originalOperationId", event.originalOperationId(), 64);
        optionalText("evidenceRef", event.evidenceRef(), 64);

        requireUtc("occurredAt", event.occurredAt(), true);
        requireUtc("startedAt", event.startedAt(), false);
        requireUtc("endedAt", event.endedAt(), false);

        if (event.producerSequence() < 0) {
            throw new EventValidationException(EventViolation.NEGATIVE_VALUE, "producerSequence", null);
        }
        if (event.elapsedMicros() != null && event.elapsedMicros() < 0) {
            throw new EventValidationException(EventViolation.NEGATIVE_VALUE, "elapsedMicros", null);
        }
        if (event.elapsedMicros() != null) {
            if (event.clockQuality() != EventClockQuality.SAME_MONOTONIC) {
                throw new EventValidationException(EventViolation.INVALID_FORMAT, "clockQuality",
                        "elapsed duration requires same-clock monotonic measurement");
            }
            if (event.startedAt() == null || event.endedAt() == null) {
                throw new EventValidationException(EventViolation.MISSING_REQUIRED, "startedAt",
                        "elapsed duration requires start and end");
            }
        }

        requiredEnum("actor", event.actor());
        requiredEnum("taskName", event.taskName());
        requiredEnum("status", event.status());
        requiredEnum("provenance", event.provenance());
        requiredEnum("reason", event.reason());

        if (event.status() == EventStatus.SKIPPED
                && event.reason() != EventReason.SKIPPED_NOT_APPLICABLE
                && event.evidenceRef() == null) {
            throw new EventValidationException(EventViolation.COVERAGE_GAP_NOT_ALLOWED, "status",
                    "skipped task requires declared not-applicable reason or evidence reference");
        }

        if (!manifest.capability().equals(event.capability())
                || !manifest.manifestVersion().equals(event.manifestVersion())) {
            throw new EventValidationException(EventViolation.MANIFEST_MISMATCH, "manifestVersion", null);
        }

        EventManifest.TaskDeclaration declaration = manifest.task(event.taskName());
        if (declaration == null) {
            throw new EventValidationException(EventViolation.UNKNOWN_TASK, "taskName", null);
        }
        if (declaration.actor() != event.actor()) {
            throw new EventValidationException(EventViolation.TASK_ACTOR_MISMATCH, "actor", null);
        }

        EventManifest.ProducerDeclaration producer = manifest.producer(event.producerId());
        if (producer == null) {
            throw new EventValidationException(EventViolation.PRODUCER_NOT_DECLARED, "producerId", null);
        }
        if (producer.actor() != event.actor() || !producer.tasks().contains(event.taskName())) {
            throw new EventValidationException(EventViolation.PRODUCER_NOT_ASSIGNED_TO_TASK, "producerId", null);
        }
        if (!manifest.allowedReasons().contains(event.reason())) {
            throw new EventValidationException(EventViolation.UNKNOWN_REASON, "reason", null);
        }

        if (CanonicalEventCodec.canonicalBytes(event).length > MAX_CANONICAL_BYTES) {
            throw new EventValidationException(EventViolation.PAYLOAD_TOO_LARGE, "payload", null);
        }
    }

    /**
     * Wrapper admission: the stored hash must match the payload, critical-only metadata is required
     * for critical events and forbidden for task events, and sampled telemetry cannot enter the
     * functional store.
     */
    public void validateWrapper(EventSourceWrapper wrapper, CanonicalEvent event) {
        if (wrapper.deliveryClass() == null) {
            throw new EventValidationException(EventViolation.MISSING_REQUIRED, "deliveryClass", null);
        }
        if (wrapper.deliveryClass() == EventDurability.TECHNICAL_SAMPLED) {
            throw new EventValidationException(EventViolation.NOT_A_FUNCTIONAL_EVENT, "deliveryClass", null);
        }
        String expected = CanonicalEventCodec.sha256(event);
        if (wrapper.eventSha256() == null || !wrapper.eventSha256().equals(expected)) {
            throw new EventValidationException(EventViolation.HASH_MISMATCH, "eventSha256", null);
        }
        if (wrapper.sourceSequence() < 1 || wrapper.sourceSequence() > 999_999_999_999L) {
            throw new EventValidationException(EventViolation.OUT_OF_BOUNDS, "sourceSequence",
                    "twelve-digit commit sequence required");
        }
        requireUtc("collectedAt", wrapper.collectedAt(), true);
        requireUtc("storedAt", wrapper.storedAt(), true);

        if (wrapper.deliveryClass() == EventDurability.CRITICAL_COMMITTED) {
            if (wrapper.journalVersion() == null || wrapper.journalVersion() <= 0) {
                throw new EventValidationException(EventViolation.CRITICAL_METADATA_REQUIRED,
                        "journalVersion", null);
            }
            if (wrapper.committedAt() == null || !wrapper.committedAt().equals(wrapper.collectedAt())) {
                throw new EventValidationException(EventViolation.CRITICAL_METADATA_REQUIRED,
                        "committedAt", "critical collection time equals commit time");
            }
            requireUtc("committedAt", wrapper.committedAt(), true);
            requiredText("stateAfter", wrapper.stateAfter(), 32);
        } else if (wrapper.journalVersion() != null
                || wrapper.committedAt() != null
                || wrapper.stateAfter() != null) {
            throw new EventValidationException(EventViolation.CRITICAL_METADATA_FORBIDDEN,
                    "deliveryClass", "best-effort task cannot assert journal metadata");
        }
    }

    private static void requiredText(String field, String value, int max) {
        if (value == null || value.isEmpty()) {
            throw new EventValidationException(EventViolation.MISSING_REQUIRED, field, null);
        }
        text(field, value, max);
    }

    private static void optionalText(String field, String value, int max) {
        if (value != null) {
            text(field, value, max);
        }
    }

    private static void text(String field, String value, int max) {
        EventSanitizer.requireSafe(field, value);
        if (value.length() > max) {
            throw new EventValidationException(EventViolation.OUT_OF_BOUNDS, field,
                    "maximum length " + max);
        }
    }

    private static void requiredEnum(String field, Enum<?> value) {
        if (value == null) {
            throw new EventValidationException(EventViolation.INVALID_ENUM, field, "value is required");
        }
    }

    private static void requireUtc(String field, String value, boolean required) {
        if (value == null) {
            if (required) {
                throw new EventValidationException(EventViolation.MISSING_REQUIRED, field, null);
            }
            return;
        }
        EventSanitizer.requireSafe(field, value);
        if (!value.endsWith("Z")) {
            throw new EventValidationException(EventViolation.MALFORMED_TIME, field, "UTC Z designator required");
        }
        try {
            Instant.parse(value);
        } catch (DateTimeParseException e) {
            throw new EventValidationException(EventViolation.MALFORMED_TIME, field, "RFC 3339 time required");
        }
    }
}
