package com.acquiring.simulator.domain.event;

/** Explicit coverage gap; a missing event is never reported as a failed task. */
public enum EventGapCode {
    MISSING_START,
    MISSING_TERMINAL,
    MISSING_PRODUCER,
    PERSISTENCE_LOSS,
    CONFLICT
}
