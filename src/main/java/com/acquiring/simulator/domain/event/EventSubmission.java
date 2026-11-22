package com.acquiring.simulator.domain.event;

import java.util.Objects;

/**
 * One event handed to the collector. It carries the collector-fixed context, the canonical payload
 * and the source-wrapper values that the collector legitimately owns: delivery class, collection
 * time, storage time and (critical only) journal version, commit time and committed state.
 *
 * <p>{@code sourceSequence} is deliberately absent: the store allocates it atomically with the
 * HEAD update, so a producer can never choose or reserve a sequence.
 */
public record EventSubmission(
        EventContext context,
        CanonicalEvent event,
        EventDurability deliveryClass,
        Long journalVersion,
        String committedAt,
        String collectedAt,
        String storedAt,
        String stateAfter) {

    public EventSubmission {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(event, "event");
        Objects.requireNonNull(deliveryClass, "deliveryClass");
        if (collectedAt == null || collectedAt.isBlank()) {
            throw new EventValidationException(EventViolation.MISSING_REQUIRED, "collectedAt", null);
        }
        if (storedAt == null || storedAt.isBlank()) {
            throw new EventValidationException(EventViolation.MISSING_REQUIRED, "storedAt", null);
        }
    }

    public EventDedupeKey dedupeKey() {
        return EventDedupeKey.of(context, event);
    }

    /** Collector-owned wrapper for the sequence the store allocated. */
    public EventSourceWrapper wrapper(long sourceSequence, String eventSha256) {
        return new EventSourceWrapper(deliveryClass, eventSha256, journalVersion, committedAt,
                collectedAt, sourceSequence, storedAt, stateAfter);
    }
}
