package com.acquiring.simulator.domain.event;

/** OBSERVED is directly seen; INFERRED is derived and must stay labelled as such. */
public enum EventProvenance {
    OBSERVED,
    INFERRED
}
