package com.acquiring.simulator.channel;

import java.util.Objects;

/**
 * Shape-checked C4 evidence candidate. Always explicitly UNVERIFIED.
 *
 * <p>Acceptance means only that the carried envelope is well-formed, canonically encoded,
 * hash-bound to the exact received body and bound to the authenticated transport sender.
 * It does not mean the attestation is trusted: no Ed25519 signature has been verified, no
 * configured key or principal has been resolved, no role or mandate has been checked and
 * no business evidence has been admitted. {@link C4EvidenceVerifier} performs the DIRECT
 * or RELAY cryptographic and trust checks separately. There is deliberately no
 * {@code valid}, {@code verified} or {@code trusted} flag.</p>
 *
 * <p>The candidate retains only typed fields and hashes. It never exposes the raw carrier
 * header or body bytes, and {@link #toString()} is value-redacted.</p>
 */
public record C4CarrierCandidate(
        C4CarrierEnvelope envelope,
        String responseBodySha256,
        String canonicalAttestationSha256,
        SenderBinding senderBinding,
        AttestationState attestationState,
        KeyResolution keyResolution) {

    public C4CarrierCandidate {
        Objects.requireNonNull(envelope, "envelope");
        C4CarrierSchema.requireHex64("responseBodySha256", responseBodySha256);
        C4CarrierSchema.requireHex64("canonicalAttestationSha256", canonicalAttestationSha256);
        Objects.requireNonNull(senderBinding, "senderBinding");
        Objects.requireNonNull(attestationState, "attestationState");
        Objects.requireNonNull(keyResolution, "keyResolution");
    }

    /** Which authenticated-sender equality was established by transport identity. */
    public enum SenderBinding {
        DIRECT_SENDER_MATCHED,
        RELAY_SENDER_MATCHED,
        /**
         * The late-delivery transport sender is neither the proof signer nor the relay
         * principal, and was admitted only by an explicit registry-scoped diagnostic
         * forwarding permission. This grants no role, signing authority or outcome
         * authority; the inner proof is still verified unchanged.
         */
        DIAGNOSTIC_FORWARD_PERMITTED
    }

    /** The only state this slice can produce: proof material is not yet trusted. */
    public enum AttestationState {
        UNVERIFIED_CANDIDATE
    }

    /** Key/principal resolution and signature verification are a later slice. */
    public enum KeyResolution {
        UNRESOLVED
    }

    @Override
    public String toString() {
        return "C4CarrierCandidate[evidenceId=" + envelope.attestation().evidenceId()
                + ", transactionId=" + envelope.attestation().transactionId()
                + ", mode=" + envelope.mode()
                + ", senderBinding=" + senderBinding
                + ", attestationState=" + attestationState
                + ", keyResolution=" + keyResolution
                + ", responseBodySha256=" + responseBodySha256 + "]";
    }
}
