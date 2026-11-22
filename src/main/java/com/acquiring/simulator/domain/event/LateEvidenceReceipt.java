package com.acquiring.simulator.domain.event;

import java.time.Instant;
import java.util.Objects;

/**
 * Read representation of the delivery receipt persisted with a late verified confirmation
 * (channel contract C4 late-delivery paragraph; observation contract O3/P5).
 *
 * <p>These facts are additive journal attributes, not canonical event fields. They carry the
 * delivery identity and the evidence-time routing facts so a fresh terminal read can recognize an
 * identical redelivery without rebuilding an in-memory candidate and without a second write. The
 * receipt id is a delivery identity, not a decision authority.
 *
 * <p>An empty read means the row was not committed as late evidence; it does not mean the row has
 * no confirmed outcome.
 */
public record LateEvidenceReceipt(
        String deliveryReceiptId,
        Instant evidenceAt,
        Instant deliveredAt,
        Instant deadline,
        boolean late) {

    public LateEvidenceReceipt {
        if (deliveryReceiptId == null || deliveryReceiptId.isBlank()
                || deliveryReceiptId.length() > 64) {
            throw new IllegalArgumentException(
                    "deliveryReceiptId must be 1-64 characters");
        }
        Objects.requireNonNull(evidenceAt, "evidenceAt");
        Objects.requireNonNull(deliveredAt, "deliveredAt");
        Objects.requireNonNull(deadline, "deadline");
        EventSanitizer.requireSafe("deliveryReceiptId", deliveryReceiptId);
        if (deliveredAt.isBefore(evidenceAt)) {
            throw new IllegalArgumentException(
                    "delivery cannot precede the evidence availability time");
        }
    }
}
