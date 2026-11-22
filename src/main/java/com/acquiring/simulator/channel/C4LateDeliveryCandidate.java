package com.acquiring.simulator.channel;

import java.util.Objects;

/**
 * Parsed but explicitly unverified C4 late-delivery candidate.
 *
 * <p>Parsing establishes only that the payload respects the 8192-byte bound, is
 * uncompressed and unfurled, has the exact closed four-key shape, carries canonical
 * standard base64 for a 1–2048-byte original body, carries a canonical inner C4 envelope
 * of at most 4096 bytes, and binds the SHA-256 of the exact decoded body to the signed
 * {@code responseSha256}. No Ed25519 signature has been checked, no key, principal, role
 * or mandate has been resolved, no delivery sender has been authorised and no business
 * evidence has been admitted.</p>
 *
 * <p>The candidate retains only hashes and typed identifiers, never the raw payload, JSON,
 * decoded body, base64 or signature bytes. It has deliberately no {@code valid},
 * {@code verified}, {@code trusted}, payment, receipt-durability or admission flag.</p>
 */
public record C4LateDeliveryCandidate(
        C4LateDeliveryEnvelope lateEnvelope,
        String responseBodySha256,
        String canonicalAttestationSha256,
        String canonicalLateEnvelopeSha256,
        ParseState parseState) {

    public C4LateDeliveryCandidate {
        Objects.requireNonNull(lateEnvelope, "lateEnvelope");
        C4CarrierSchema.requireHex64("responseBodySha256", responseBodySha256);
        C4CarrierSchema.requireHex64(
                "canonicalAttestationSha256", canonicalAttestationSha256);
        C4CarrierSchema.requireHex64(
                "canonicalLateEnvelopeSha256", canonicalLateEnvelopeSha256);
        Objects.requireNonNull(parseState, "parseState");
    }

    /** The only state shape parsing can produce: proof material is not yet trusted. */
    public enum ParseState {
        UNVERIFIED_CANDIDATE
    }

    /** Never renders the raw payload, body, base64, envelope or signature material. */
    @Override
    public String toString() {
        return "C4LateDeliveryCandidate[deliveryReceiptId="
                + lateEnvelope.deliveryReceiptId()
                + ", evidenceId=" + lateEnvelope.evidence().attestation().evidenceId()
                + ", transactionId=" + lateEnvelope.evidence().attestation().transactionId()
                + ", mode=" + lateEnvelope.evidence().mode()
                + ", parseState=" + parseState
                + ", responseBodySha256=" + responseBodySha256 + "]";
    }
}
