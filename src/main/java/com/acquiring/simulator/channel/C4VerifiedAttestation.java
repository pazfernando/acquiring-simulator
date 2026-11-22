package com.acquiring.simulator.channel;

import java.util.Objects;

/**
 * Cryptographically checked DIRECT or RELAY attestation identity and type.
 *
 * <p>This record is the successful result of {@link C4EvidenceVerifier}. For DIRECT, the
 * Ed25519 signature verified over the exact canonical attestation bytes, and the key,
 * environment, sender, P5 role, P4 row, acquirer mandate, body hash and
 * canonical-attestation hash all bound. For RELAY, the same inner issuer signature and
 * trust bindings hold <strong>and</strong> the configured network countersignature over the
 * canonical relay object verified, so the relay receipt, delivery time and both content
 * hashes are bound without replacing the issuer signature.</p>
 *
 * <p>It is deliberately <strong>not</strong> a payment confirmation, not a processor
 * receipt, not business evidence admission and not a decision that the original wire
 * instruction or full business context matches. Those remain later seams.</p>
 *
 * <p>It carries no raw signature, body, public key or private key and has no
 * {@code valid}, {@code paymentConfirmed} or {@code processorReceipt} flag. The explicit
 * {@link VerificationScope} states exactly which cryptographic chain was checked.</p>
 */
public record C4VerifiedAttestation(
        String environmentId,
        String evidenceId,
        String producerPrincipal,
        String signingPrincipal,
        String keyId,
        C4PrincipalRole role,
        C4AttestationType attestationType,
        String authorizationId,
        String transactionId,
        String acquirerRef,
        String instructionSha256,
        String responseBodySha256,
        String canonicalAttestationSha256,
        VerificationScope verificationScope,
        RelayBinding relayBinding) {

    /** Exactly what this slice established. */
    public enum VerificationScope {
        DIRECT_ED25519_AND_TRUST_BINDINGS_VERIFIED,
        RELAY_ED25519_AND_TRUST_BINDINGS_VERIFIED,
        /**
         * Same DIRECT cryptographic and trust bindings, with the transport sender
         * separately authorised as an explicit diagnostic forwarder by the late-delivery
         * seam. The transport sender is not the proof signer and gains no role or outcome
         * authority.
         */
        DIRECT_ED25519_AND_TRUST_BINDINGS_VERIFIED_VIA_AUTHORIZED_FORWARD,
        /**
         * Same RELAY inner-issuer plus network-countersignature bindings, with the
         * transport sender separately authorised as an explicit diagnostic forwarder.
         */
        RELAY_ED25519_AND_TRUST_BINDINGS_VERIFIED_VIA_AUTHORIZED_FORWARD
    }

    /**
     * Relay countersignature facts established by RELAY verification.
     *
     * <p>The inner issuer identity remains on the enclosing record; this binding names the
     * configured network relay that carried it. It contains no signature or key material
     * and is not a delivery receipt in the business-admission sense.</p>
     */
    public record RelayBinding(
            String principal,
            String keyId,
            String receiptId,
            String deliveredAt) {

        public RelayBinding {
            C4CarrierSchema.requireId("relayPrincipal", principal, 64);
            C4CarrierSchema.requireId("relayKeyId", keyId, 64);
            C4CarrierSchema.requireId("relayReceiptId", receiptId, 64);
            C4CarrierSchema.requireIsoInstantUtc("relayDeliveredAt", deliveredAt);
        }

        /** Never renders signature bytes; the relay signature is not retained here. */
        @Override
        public String toString() {
            return "RelayBinding[principal=" + principal
                    + ", keyId=" + keyId
                    + ", receiptId=" + receiptId
                    + ", deliveredAt=" + deliveredAt + "]";
        }
    }

    public C4VerifiedAttestation {
        C4CarrierSchema.requireId("environmentId", environmentId, 64);
        C4CarrierSchema.requireId("evidenceId", evidenceId, 64);
        C4CarrierSchema.requireId("producerPrincipal", producerPrincipal, 64);
        C4CarrierSchema.requireId("signingPrincipal", signingPrincipal, 64);
        C4CarrierSchema.requireId("keyId", keyId, 64);
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(attestationType, "attestationType");
        C4CarrierSchema.requireId("authorizationId", authorizationId, 64);
        C4CarrierSchema.requireId("transactionId", transactionId, 64);
        C4CarrierSchema.requireId("acquirerRef", acquirerRef, 128);
        C4CarrierSchema.requireHex64("instructionSha256", instructionSha256);
        C4CarrierSchema.requireHex64("responseBodySha256", responseBodySha256);
        C4CarrierSchema.requireHex64(
                "canonicalAttestationSha256", canonicalAttestationSha256);
        Objects.requireNonNull(verificationScope, "verificationScope");
        switch (verificationScope) {
            case DIRECT_ED25519_AND_TRUST_BINDINGS_VERIFIED,
                    DIRECT_ED25519_AND_TRUST_BINDINGS_VERIFIED_VIA_AUTHORIZED_FORWARD -> {
                if (relayBinding != null) {
                    throw C4CarrierException.of(
                            C4CarrierViolation.CARRIER_SCHEMA, "relayBinding");
                }
            }
            case RELAY_ED25519_AND_TRUST_BINDINGS_VERIFIED,
                    RELAY_ED25519_AND_TRUST_BINDINGS_VERIFIED_VIA_AUTHORIZED_FORWARD -> {
                if (relayBinding == null) {
                    throw C4CarrierException.of(
                            C4CarrierViolation.CARRIER_SCHEMA, "relayBinding");
                }
            }
        }
    }

    /** Never renders signature, body, key or full attestation values. */
    @Override
    public String toString() {
        return "C4VerifiedAttestation[evidenceId=" + evidenceId
                + ", authorizationId=" + authorizationId
                + ", transactionId=" + transactionId
                + ", signingPrincipal=" + signingPrincipal
                + ", keyId=" + keyId
                + ", role=" + role
                + ", attestationType=" + attestationType
                + ", verificationScope=" + verificationScope
                + ", relay=" + (relayBinding == null ? "none" : relayBinding) + "]";
    }
}
