package com.acquiring.simulator.console;

/**
 * Sink for the deny record.
 *
 * <p>Exactly one record is emitted per refusal. Emission is best effort: the caller contains a
 * throwing sink and still returns the fixed {@code 403}. A logging failure is a defect to alert on,
 * never a reason to permit the request.</p>
 */
@FunctionalInterface
public interface ConsoleOriginDenialSink {

    void emit(ConsoleOriginDenial denial);

    /** Sink that discards the record. Used only where an explicit sink is not yet composed. */
    static ConsoleOriginDenialSink noop() {
        return denial -> {
        };
    }
}
