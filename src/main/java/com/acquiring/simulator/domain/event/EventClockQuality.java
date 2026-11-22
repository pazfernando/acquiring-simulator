package com.acquiring.simulator.domain.event;

/** Same-clock duration evidence only; cross-host subtraction is never claimed. */
public enum EventClockQuality {
    SAME_MONOTONIC,
    SKEWED,
    UNKNOWN
}
