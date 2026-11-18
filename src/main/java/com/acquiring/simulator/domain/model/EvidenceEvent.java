package com.acquiring.simulator.domain.model;

/** Append-only internal evidence-event row. */
public record EvidenceEvent(
        String id,
        String authorizationId,
        String eventType,
        String decision,
        DecisionSource source,
        String correlationId,
        String detail,
        long recordedAt) {
}
