package com.acquiring.simulator.domain.event;

/**
 * Transport/source-wrapper delivery class (O1a). It is not canonical event content.
 * TECHNICAL_SAMPLED belongs to ADOT telemetry and may never enter the functional event table.
 */
public enum EventDurability {
    CRITICAL_COMMITTED,
    TASK_BEST_EFFORT,
    TECHNICAL_SAMPLED
}
