package com.acquiring.simulator.domain.event;

/**
 * Dedupe metadata for one environment-bound producer/event identity. It is technical metadata, not
 * a second payload copy: the canonical payload lives once, under the event row.
 *
 * <p>{@code journalVersion} is the public committed journal version recorded with the source row for
 * critical events. It is {@code null} when the row carries no committed-version evidence (for
 * example rows written before additive persistence) and is never derived from any CAS counter.
 *
 * <p>{@code integrityConflict} is the durable quarantine marker written when a redelivery carries
 * changed immutable content for this identity (observation contract O1a/O2a; OBS-14; R16). It is
 * {@code null} when no integrity conflict has been recorded. The stored payload, hash, sequence and
 * business outcome are never altered by the marker.
 *
 * <p>{@code collectedAt} is the first authorized collection time recorded with this identity when it
 * was committed, not the time of a later redelivery, restart or read (O1a/O2a: "best-effort first
 * authorized collection is retained across redelivery"; critical collection time equals the commit
 * time). It is {@code null} only for legacy metadata written before this field existed, and that
 * absence means "unknown", never a synthesized current time.
 */
public record StoredEvent(
        EventDedupeKey dedupeKey,
        EventContext context,
        long sourceSequence,
        String eventSha256,
        String storedInstanceId,
        EventDurability deliveryClass,
        String stateAfter,
        Long journalVersion,
        String status,
        EventIntegrityConflict integrityConflict,
        String collectedAt) {

    /** Payload is retained and linkable. */
    public static final String ACTIVE = "ACTIVE";
    /** Payload expired; identity metadata kept so redelivery cannot recreate history. */
    public static final String EXPIRED = "EXPIRED";
}
