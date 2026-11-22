package com.acquiring.simulator.domain.event;

/**
 * Explicit critical-event capacity configuration for one manifest.
 *
 * <p>The observation contract (O8) proposes eight reserved critical slots per operation and at most
 * five critical transitions for Operation A, but the owner has NOT approved those numbers as
 * capacity. This type exists so capacity is injected by configuration instead of being embedded in
 * the runtime manifest; a caller must pass a policy explicitly. Production wiring supplies the
 * owner-approved values once decided. Tests may use example values.
 */
public record CriticalCapacity(int maxCriticalEventsPerOperation, int criticalSlotBudget) {

    public CriticalCapacity {
        if (maxCriticalEventsPerOperation < 0) {
            throw new IllegalArgumentException("maxCriticalEventsPerOperation must be non-negative");
        }
        if (criticalSlotBudget < maxCriticalEventsPerOperation) {
            throw new IllegalArgumentException("critical slot budget must cover the declared transitions");
        }
    }
}
