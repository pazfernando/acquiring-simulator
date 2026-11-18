package com.acquiring.simulator.domain.model;

import java.time.Instant;

/**
 * Normalised downstream reply. {@code availableAt} is when the evidence became available to
 * the processor; malformed or uncorrelated content is never treated as issuer evidence.
 */
public record DownstreamReply(
        String correlationId,
        IssuerDecision decision,
        Instant availableAt) {
}
