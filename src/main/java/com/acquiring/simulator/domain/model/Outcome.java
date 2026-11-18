package com.acquiring.simulator.domain.model;

/** Four caller-visible business outcomes; no internal reasons cross this boundary. */
public enum Outcome {
    APPROVED,
    ISSUER_DECLINED,
    PROCESSOR_REJECTED,
    NOT_CONFIRMED
}
