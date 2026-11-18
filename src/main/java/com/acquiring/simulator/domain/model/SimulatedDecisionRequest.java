package com.acquiring.simulator.domain.model;

import java.time.Duration;
import java.time.Instant;

/** Deterministic in-process downstream request; no external network is involved. */
public record SimulatedDecisionRequest(
        String authorizationId,
        String correlationId,
        CredentialDecision credentialDecision,
        Route route,
        Instant dispatchedAt,
        Duration responseTimeout) {
}
