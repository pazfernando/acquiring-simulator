package com.acquiring.simulator.domain.event;

import java.util.Locale;

/**
 * Functional EVENT table key scheme (observation contract O2a). Keys are technical metadata; the
 * canonical payload remains the single stored copy.
 *
 * <ul>
 *   <li>Event partition: {@code ENV#<environment>#ACQ#<acq>#MID#<merchant>#TX#<transaction>}
 *   <li>Event row: {@code E#<12-digit sourceSequence>}; HEAD row in the same partition is {@code HEAD}
 *   <li>Dedupe partition: {@code ENV#<environment>#DEDUPE}, row {@code P#<producerId>#E#<eventId>}
 * </ul>
 */
public final class EventTableKeys {

    public static final String HEAD_SORT_KEY = "HEAD";
    private static final long MAX_SEQUENCE = 999_999_999_999L;

    private EventTableKeys() {
    }

    public static String eventPartition(EventContext context) {
        return "ENV#" + context.environment()
                + "#ACQ#" + context.acquirerRef()
                + "#MID#" + context.merchantRef()
                + "#TX#" + context.transactionId();
    }

    /** Twelve-digit zero-padded commit sequence, so lexical order equals source order. */
    public static String eventSortKey(long sourceSequence) {
        if (sourceSequence < 1 || sourceSequence > MAX_SEQUENCE) {
            throw new EventValidationException(EventViolation.OUT_OF_BOUNDS, "sourceSequence",
                    "twelve-digit commit sequence required");
        }
        return String.format(Locale.ROOT, "E#%012d", sourceSequence);
    }

    public static String headSortKey() {
        return HEAD_SORT_KEY;
    }

    public static String dedupePartition(String environment) {
        if (environment == null || environment.isBlank()) {
            throw new EventValidationException(EventViolation.MISSING_REQUIRED, "environment", null);
        }
        return "ENV#" + environment + "#DEDUPE";
    }

    public static String dedupeSortKey(EventDedupeKey key) {
        return "P#" + key.producerId() + "#E#" + key.eventId();
    }

    /**
     * Technical slot-accounting partition for one operation context. This is collector-owned
     * accounting metadata, NOT an event partition: range reads over {@link #eventPartition} never
     * see it. The exact layout is reviewable; it encodes no business rule.
     */
    public static String capacityPartition(EventContext context) {
        return eventPartition(context) + "#CAPACITY";
    }

    /** Single counter row inside the capacity partition. */
    public static String capacitySortKey() {
        return "SLOTS";
    }
}
