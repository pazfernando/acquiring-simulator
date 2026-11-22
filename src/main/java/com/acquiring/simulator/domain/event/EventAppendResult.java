package com.acquiring.simulator.domain.event;

import java.util.Objects;

/**
 * Outcome of one append attempt. {@code sourceSequence} is the immutable commit order assigned to
 * the stored event; a replay reports the original sequence and never allocates a new one.
 */
public record EventAppendResult(
        EventDeduplication.Decision decision,
        long sourceSequence,
        String eventSha256,
        String storedEventSha256) {

    public EventAppendResult {
        Objects.requireNonNull(decision, "decision");
        Objects.requireNonNull(eventSha256, "eventSha256");
        Objects.requireNonNull(storedEventSha256, "storedEventSha256");
        if (sourceSequence < 1) {
            throw new IllegalArgumentException("sourceSequence must be a positive commit sequence");
        }
    }

    public static EventAppendResult stored(long sourceSequence, String eventSha256) {
        return new EventAppendResult(EventDeduplication.Decision.NEW, sourceSequence,
                eventSha256, eventSha256);
    }

    public static EventAppendResult replay(long sourceSequence, String incomingSha256,
            String storedSha256) {
        return new EventAppendResult(EventDeduplication.Decision.EXACT_REPLAY, sourceSequence,
                incomingSha256, storedSha256);
    }

    public static EventAppendResult conflict(long sourceSequence, String incomingSha256,
            String storedSha256) {
        return new EventAppendResult(EventDeduplication.Decision.CONTENT_CONFLICT, sourceSequence,
                incomingSha256, storedSha256);
    }

    public boolean stored() {
        return decision == EventDeduplication.Decision.NEW;
    }
}
