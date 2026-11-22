package com.acquiring.simulator.domain.event;

import java.util.Objects;

/**
 * Durable quarantine marker for an observation integrity conflict.
 *
 * <p>Approved wording: observation contract O1a/O2a — "Same canonical hash no-op; changed immutable
 * content quarantined as integrity conflict" and the dedupe metadata partition records "payload hash
 * + original context/source key/sequence, no second payload"; acceptance scenario OBS-14 —
 * "Preserve original, flag/quarantine conflict and observation integrity gap; do not overwrite or
 * create payment evidence"; requirements R16 — "A conflicting payload for an existing event ID is
 * quarantined/reported as observation integrity failure".
 *
 * <p>This is collector-owned technical metadata attached to the existing dedupe row. It records the
 * <em>first</em> conflicting content hash only. It is not a second payload copy, carries no canonical
 * event content or secrets, and never changes the stored payload, stored hash, immutable source
 * sequence, HEAD or business outcome.
 *
 * @param conflictingEventSha256 SHA-256 of the first canonical payload that conflicted with the
 *                               stored identity; safe metadata, never the payload itself
 */
public record EventIntegrityConflict(String conflictingEventSha256) {

    /** Attribute value for the durable integrity-conflict flag on the dedupe row. */
    public static final String INTEGRITY_CONFLICT = "INTEGRITY_CONFLICT";

    public EventIntegrityConflict {
        Objects.requireNonNull(conflictingEventSha256, "conflictingEventSha256");
    }
}
