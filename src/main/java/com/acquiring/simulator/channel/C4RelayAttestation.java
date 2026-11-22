package com.acquiring.simulator.channel;

import java.util.Arrays;
import java.util.List;

/**
 * Closed RELAY countersignature object from the C4 outer envelope.
 *
 * <p>Exactly seven keys: {@code principal}, {@code receiptId}, {@code deliveredAt},
 * {@code keyId}, {@code responseSha256}, {@code attestationSha256} and {@code signature}.
 * {@code principal} is fixed to {@code NETWORK-SIM}; the object binds the issuer content
 * hash, the exact response hash and the relay receipt/time. The network signature covers
 * the canonical relay object excluding {@code signature}.</p>
 *
 * <p>Shape and hash binding only: no signature verification, configured key lookup, role
 * or mandate check is performed here. {@link C4EvidenceVerifier} performs the RELAY
 * countersignature and trust checks.</p>
 */
public record C4RelayAttestation(
        String principal,
        String receiptId,
        String deliveredAt,
        String keyId,
        String responseSha256,
        String attestationSha256,
        byte[] signature) {

    /** Fixed relay principal literal. */
    public static final String PRINCIPAL = "NETWORK-SIM";

    /** The exact closed key set, in RFC8785 canonical order. */
    public static final List<String> KEYS = List.of(
            "attestationSha256", "deliveredAt", "keyId", "principal", "receiptId",
            "responseSha256", "signature");

    public C4RelayAttestation {
        C4CarrierSchema.requireExact("principal", principal, PRINCIPAL);
        C4CarrierSchema.requireId("receiptId", receiptId, 64);
        C4CarrierSchema.requireIsoInstantUtc("deliveredAt", deliveredAt);
        C4CarrierSchema.requireId("keyId", keyId, 64);
        C4CarrierSchema.requireHex64("responseSha256", responseSha256);
        C4CarrierSchema.requireHex64("attestationSha256", attestationSha256);
        if (signature == null || signature.length != C4CarrierParser.SIGNATURE_BYTES) {
            throw C4CarrierException.of(C4CarrierViolation.CARRIER_SCHEMA, "signature");
        }
        signature = signature.clone();
    }

    @Override
    public byte[] signature() {
        return signature.clone();
    }

    /** Canonical bytes of the relay object excluding {@code signature}. */
    public byte[] signedBytes() {
        return C4CarrierSchema.asciiBytes(C4CarrierCanonicalJson.relaySigned(this));
    }

    /** Never renders signature bytes. */
    @Override
    public String toString() {
        return "C4RelayAttestation[principal=" + principal
                + ", receiptId=" + receiptId
                + ", keyId=" + keyId
                + ", signatureLength=" + signature.length + "]";
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof C4RelayAttestation that
                && principal.equals(that.principal)
                && receiptId.equals(that.receiptId)
                && deliveredAt.equals(that.deliveredAt)
                && keyId.equals(that.keyId)
                && responseSha256.equals(that.responseSha256)
                && attestationSha256.equals(that.attestationSha256)
                && Arrays.equals(signature, that.signature);
    }

    @Override
    public int hashCode() {
        return 31 * java.util.Objects.hash(principal, receiptId, deliveredAt, keyId,
                responseSha256, attestationSha256) + Arrays.hashCode(signature);
    }
}
