package com.acquiring.simulator.adapter.aws;

import com.acquiring.simulator.domain.event.EventSourceWrapper;
import com.acquiring.simulator.domain.event.EventSubmission;
import java.util.List;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItem;

/**
 * Transaction-composition seam for a critical event (observation contract O3).
 *
 * <p>Writes returned here join the SAME {@code TransactWriteItems} call account/region as the event
 * row, HEAD update and dedupe metadata. That is where a later slice adds the business record,
 * business head, immutable evidence and critical slot accounting, so no business-write-then-event
 * dual write is possible. The seam is inert by default ({@link #NONE}); P1b1 does not wire business
 * writes and does not enforce slot capacity.
 *
 * <p>Returned writes must not target the event row, HEAD row or dedupe row: those are owned by the
 * store. A failing composed condition aborts the whole transaction (no event, no sequence advance).
 */
@FunctionalInterface
public interface CriticalWriteComposer {

    /** No composed writes; the append transaction touches only event-table items. */
    CriticalWriteComposer NONE = (submission, sequence, wrapper) -> List.of();

    List<TransactWriteItem> compose(EventSubmission submission, long sequence,
            EventSourceWrapper wrapper);
}
