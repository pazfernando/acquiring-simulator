package com.acquiring.simulator.domain.port;

import com.acquiring.simulator.domain.event.EventContext;
import com.acquiring.simulator.domain.event.RecordedEvent;
import java.util.List;

/**
 * Read-only access to the retained event rows of the single functional event source (observation
 * contract O6/O8). It reads the same table rows the append path writes; it neither appends nor
 * creates a second payload copy, and it never grants payment authority.
 *
 * <p>Implementations return rows in ascending {@code sourceSequence} order and must be strongly
 * consistent: callers bind a fixed snapshot head before reading, so a stale read would silently
 * omit retained events. Rows are addressed by the environment-bound business context; a caller
 * cannot query an arbitrary partition.</p>
 */
public interface EventHistoryReader {

    /**
     * Read retained event rows strictly after {@code afterSequence} and no later than
     * {@code throughSequence}.
     *
     * @param context environment-bound business context; never caller-selected at random
     * @param afterSequence last sequence already covered by the caller, {@code 0} for the start
     * @param throughSequence inclusive fixed snapshot bound; must be at least {@code afterSequence}
     * @param limit maximum rows returned in one page, {@code 1..256}
     * @return immutable rows in ascending source order; empty when nothing is retained in range
     */
    List<RecordedEvent> readRange(
            EventContext context, long afterSequence, long throughSequence, int limit);
}
