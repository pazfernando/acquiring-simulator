package com.acquiring.simulator.domain.event;

/**
 * Collector-owned wrapper around one canonical payload (observation contract O1a). It is not part
 * of the canonical content hash. Critical-only fields must stay absent for best-effort task events,
 * so a task can never assert journal metadata.
 */
public record EventSourceWrapper(
        EventDurability deliveryClass,
        String eventSha256,
        Long journalVersion,
        String committedAt,
        String collectedAt,
        long sourceSequence,
        String storedAt,
        String stateAfter) {
}
