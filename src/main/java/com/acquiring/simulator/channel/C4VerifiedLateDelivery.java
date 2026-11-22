package com.acquiring.simulator.channel;

import java.util.Objects;

/**
 * Successful C4 late-delivery verification result.
 *
 * <p>The nested {@link C4VerifiedAttestation} is the unchanged DIRECT or RELAY
 * cryptographic result. The delivery receipt id and the late-envelope hash identify the
 * carriage envelope only. The result is deliberately <strong>not</strong> a payment
 * confirmation, not a processor receipt and not proof that a receipt is already durable.
 * It has no {@code paymentConfirmed}, {@code receiptDurable}, {@code valid},
 * {@code verified} or {@code trusted} flag and performs no admission, storage, send or
 * resend.</p>
 */
public record C4VerifiedLateDelivery(
        String deliveryReceiptId,
        C4VerifiedAttestation attestation,
        C4CarrierCandidate.SenderBinding senderBinding,
        String canonicalLateEnvelopeSha256) {

    public C4VerifiedLateDelivery {
        if (deliveryReceiptId == null || deliveryReceiptId.isEmpty()
                || deliveryReceiptId.length() > C4LateDeliveryParser.MAX_RECEIPT_ID_CHARS
                || !C4CarrierSchema.PROFILE_ALPHABET.matcher(deliveryReceiptId).matches()) {
            throw C4LateDeliveryException.of(
                    C4LateDeliveryViolation.LATE_ENVELOPE_SCHEMA, "deliveryReceiptId");
        }
        Objects.requireNonNull(attestation, "attestation");
        Objects.requireNonNull(senderBinding, "senderBinding");
        C4CarrierSchema.requireHex64(
                "canonicalLateEnvelopeSha256", canonicalLateEnvelopeSha256);
    }

    /** Never renders the body, base64, signature, key or nested envelope details. */
    @Override
    public String toString() {
        return "C4VerifiedLateDelivery[deliveryReceiptId=" + deliveryReceiptId
                + ", evidenceId=" + attestation.evidenceId()
                + ", transactionId=" + attestation.transactionId()
                + ", senderBinding=" + senderBinding
                + ", verificationScope=" + attestation.verificationScope() + "]";
    }
}
