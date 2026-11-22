package com.acquiring.simulator.domain.event;

/**
 * One recorded functional event read back from the single event source: the collector-owned
 * {@link EventSourceWrapper} plus the exact canonical payload JSON that is stored once on that row.
 *
 * <p>This is a read projection over the same row, never a second payload copy (observation contract
 * O1a/O2a). It carries no journal authority and no notification guarantee: a recorded event is
 * evidence of what the source retained, not proof of payment finality.</p>
 */
public record RecordedEvent(EventSourceWrapper wrapper, String canonicalJson) {

    public RecordedEvent {
        if (wrapper == null) {
            throw new IllegalArgumentException("wrapper is required");
        }
        if (canonicalJson == null || canonicalJson.isBlank()) {
            throw new IllegalArgumentException("canonicalJson is required");
        }
    }

    /** Immutable source sequence assigned at commit time. */
    public long sourceSequence() {
        return wrapper.sourceSequence();
    }
}
