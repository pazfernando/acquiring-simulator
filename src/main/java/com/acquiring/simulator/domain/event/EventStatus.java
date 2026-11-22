package com.acquiring.simulator.domain.event;

/** Terminal or started task observation; SKIPPED requires evidence. */
public enum EventStatus {
    STARTED,
    SUCCEEDED,
    FAILED,
    SKIPPED
}
