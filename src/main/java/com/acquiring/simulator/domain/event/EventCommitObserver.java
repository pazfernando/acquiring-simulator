package com.acquiring.simulator.domain.event;

/**
 * Best-effort post-commit observation seam for the functional event source.
 *
 * <p>It is invoked only after an append transaction committed a genuinely new event row. It is
 * never invoked for an exact replay, a content conflict or a failed transaction, and it cannot
 * change the append result or the business outcome. Implementations must be observation-only:
 * notification loss is an explicit gap, not a payment failure (observation contract O1/O7).</p>
 */
@FunctionalInterface
public interface EventCommitObserver {

    /**
     * One newly committed event row.
     *
     * @param context environment-bound partition the event was committed to
     * @param sourceSequence immutable commit-order sequence allocated for that row
     */
    void onCommitted(EventContext context, long sourceSequence);
}
