package com.acquiring.simulator.domain.model;

import java.time.Duration;
import java.time.Instant;

/** Internal synthetic contract v1; not a real Mastercard or ISO wire message. */
public record SimulatedDecisionRequest(
        String authorizationId,
        String correlationId,
        CredentialDecision credentialDecision,
        Route route,
        Instant dispatchedAt,
        Duration responseTimeout) {
}
