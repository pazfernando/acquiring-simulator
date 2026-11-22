package com.acquiring.simulator.domain.event;

/**
 * HEAD row of one event partition (observation contract O2a). It is technical ordering/retention
 * metadata, never a second copy of an event and never financial authority.
 */
public record EventHead(
        EventContext context,
        long lastSequence,
        long firstRetainedSequence,
        long expiryRevision,
        String latestKnownBusinessVersion,
        String lastCollectedAt) {
}
