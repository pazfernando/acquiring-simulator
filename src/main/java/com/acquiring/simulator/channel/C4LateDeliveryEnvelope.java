package com.acquiring.simulator.channel;

import java.util.List;
import java.util.Objects;

/**
 * Closed C4 internal late-delivery envelope.
 *
 * <p>Exactly four keys: {@code version} (fixed to 1), {@code responseBodyBase64}
 * (canonical padded standard base64 of the exact original response bytes),
 * {@code evidence} (the unchanged C4 outer evidence envelope) and
 * {@code deliveryReceiptId} (1–64 profile characters). The receipt id is a transport
 * correlation label only: it carries no decision, payment, receipt-durability or
 * business-admission authority.</p>
 *
 * <p>This type is shape-bearing proof carriage. It does not decode the body, verify a
 * signature, resolve a key or trust a sender. Those are performed by
 * {@link C4LateDeliveryParser} and {@link C4LateDeliveryVerifier}.</p>
 */
public record C4LateDeliveryEnvelope(
        int version,
        String deliveryReceiptId,
        String responseBodyBase64,
        C4CarrierEnvelope evidence) {

    /** Approved C4 late-delivery envelope version. */
    public static final int VERSION = 1;

    /** The exact closed key set, in RFC8785 canonical order. */
    public static final List<String> KEYS = List.of(
            "deliveryReceiptId", "evidence", "responseBodyBase64", "version");

    public C4LateDeliveryEnvelope {
        if (version != VERSION) {
            throw C4LateDeliveryException.of(
                    C4LateDeliveryViolation.LATE_ENVELOPE_SCHEMA, "version");
        }
        if (deliveryReceiptId == null || deliveryReceiptId.isEmpty()
                || deliveryReceiptId.length() > 64
                || !C4CarrierSchema.PROFILE_ALPHABET.matcher(deliveryReceiptId).matches()) {
            throw C4LateDeliveryException.of(
                    C4LateDeliveryViolation.LATE_ENVELOPE_SCHEMA, "deliveryReceiptId");
        }
        Objects.requireNonNull(responseBodyBase64, "responseBodyBase64");
        if (responseBodyBase64.isEmpty()) {
            throw C4LateDeliveryException.of(
                    C4LateDeliveryViolation.RESPONSE_BODY_ENCODING, "responseBodyBase64");
        }
        Objects.requireNonNull(evidence, "evidence");
    }

    /** Canonical RFC8785 JSON bytes of this late-delivery envelope. */
    public byte[] toCanonicalBytes() {
        return C4CarrierSchema.asciiBytes(C4CarrierCanonicalJson.lateEnvelope(this));
    }

    /** SHA-256 of the canonical late-delivery bytes, lowercase hex. */
    public String canonicalSha256() {
        return C4CarrierSchema.sha256Hex(toCanonicalBytes());
    }

    /** Never renders the nested envelope, body bytes or base64 payload. */
    @Override
    public String toString() {
        return "C4LateDeliveryEnvelope[version=" + version
                + ", deliveryReceiptId=" + deliveryReceiptId
                + ", evidenceMode=" + evidence.mode()
                + ", responseBodyBase64Length=" + responseBodyBase64.length() + "]";
    }
}
