package com.acquiring.simulator.domain.port;

import com.acquiring.simulator.domain.event.EventAppendResult;
import com.acquiring.simulator.domain.event.EventContext;
import com.acquiring.simulator.domain.event.EventDedupeKey;
import com.acquiring.simulator.domain.event.EventHead;
import com.acquiring.simulator.domain.event.EventSubmission;
import com.acquiring.simulator.domain.event.StoredEvent;
import java.util.Optional;

/**
 * Functional event store (observation contract O2a/O3). Appending one event atomically assigns the
 * next source sequence, inserts the event row and dedupe metadata, and conditionally advances HEAD.
 * Critical composition with business writes is an adapter concern layered on this port.
 */
public interface EventStore {

    /**
     * Validate, classify duplicates and atomically append one event.
     *
     * @return the stored sequence for a new event, or the original sequence with the replay/conflict
     *         decision for a redelivery. A conflicting payload is never overwritten.
     */
    EventAppendResult append(EventSubmission submission);

    /** Strongly consistent dedupe lookup; also the ambiguous-commit resolution path. */
    Optional<StoredEvent> find(EventDedupeKey dedupeKey);

    /** Strongly consistent partition HEAD, absent before the first append. */
    Optional<EventHead> head(EventContext context);
}
