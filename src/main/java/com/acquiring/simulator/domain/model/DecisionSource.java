package com.acquiring.simulator.domain.model;

/** Internal decision provenance; never disclosed wholesale to callers. */
public enum DecisionSource {
    NETWORK,
    ISSUER,
    PROCESSOR,
    NONE
}
