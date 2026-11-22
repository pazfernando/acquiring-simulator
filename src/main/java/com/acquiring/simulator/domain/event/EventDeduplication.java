package com.acquiring.simulator.domain.event;

/**
 * Duplicate classification for a redelivered event. Replay never allocates a new source sequence
 * and never overwrites stored content; a changed immutable payload is an integrity conflict.
 */
public final class EventDeduplication {

    public enum Decision {
        NEW,
        EXACT_REPLAY,
        CONTENT_CONFLICT
    }

    private EventDeduplication() {
    }

    public static Decision classify(String existingSha256, String incomingSha256) {
        if (existingSha256 == null || existingSha256.isBlank()) {
            return Decision.NEW;
        }
        return existingSha256.equals(incomingSha256) ? Decision.EXACT_REPLAY : Decision.CONTENT_CONFLICT;
    }
}
