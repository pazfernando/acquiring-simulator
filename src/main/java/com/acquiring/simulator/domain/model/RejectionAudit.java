package com.acquiring.simulator.domain.model;

/** Internal-only record of a request rejected before any dispatch. */
public record RejectionAudit(
        String id,
        String acquirerRef,
        String merchantRef,
        String callerId,
        String channelPaymentRef,
        RejectionReason reason,
        long recordedAt) {
}
