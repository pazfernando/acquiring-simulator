package com.acquiring.simulator.channel;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Closed C4 outer envelope carried once in {@code X-Simulator-Evidence}.
 *
 * <p>Exactly six keys: {@code version}, {@code mode}, {@code attestation}, {@code keyId},
 * {@code signature} and {@code relay}. {@code version} is fixed to 1; {@code mode} is
 * {@code DIRECT} or {@code RELAY}; {@code DIRECT} requires a null relay and {@code RELAY}
 * requires the closed relay object. The outer {@code keyId} must equal the signed
 * attestation {@code keyId}. The signature is canonical standard base64 of exactly 64
 * Ed25519 signature bytes.</p>
 *
 * <p>The envelope is shape-bearing proof material, not proof. No signature is verified and
 * no key, sender role or mandate is resolved here.</p>
 */
public record C4CarrierEnvelope(
        int version,
        Mode mode,
        C4EvidenceAttestation attestation,
        String keyId,
        byte[] signature,
        C4RelayAttestation relay) {

    /** Approved C4 envelope version. */
    public static final int VERSION = 1;

    /** The exact closed key set, in RFC8785 canonical order. */
    public static final List<String> KEYS = List.of(
            "attestation", "keyId", "mode", "relay", "signature", "version");

    /** Carriage mode; the relay details are only present for {@code RELAY}. */
    public enum Mode {
        DIRECT,
        RELAY
    }

    public C4CarrierEnvelope {
        if (version != VERSION) {
            throw C4CarrierException.of(C4CarrierViolation.CARRIER_SCHEMA, "version");
        }
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(attestation, "attestation");
        C4CarrierSchema.requireId("keyId", keyId, 64);
        if (signature == null || signature.length != C4CarrierParser.SIGNATURE_BYTES) {
            throw C4CarrierException.of(C4CarrierViolation.CARRIER_SCHEMA, "signature");
        }
        if (!keyId.equals(attestation.keyId())) {
            throw C4CarrierException.of(C4CarrierViolation.KEY_BINDING, "keyId");
        }
        if (mode == Mode.DIRECT && relay != null) {
            throw C4CarrierException.of(C4CarrierViolation.CARRIER_SCHEMA, "relay");
        }
        if (mode == Mode.RELAY && relay == null) {
            throw C4CarrierException.of(C4CarrierViolation.CARRIER_SCHEMA, "relay");
        }
        signature = signature.clone();
    }

    @Override
    public byte[] signature() {
        return signature.clone();
    }

    /** Canonical RFC8785 JSON bytes of this envelope. */
    public byte[] toCanonicalBytes() {
        return C4CarrierSchema.asciiBytes(C4CarrierCanonicalJson.envelope(this));
    }

    /** Never renders signature bytes or the full attestation. */
    @Override
    public String toString() {
        return "C4CarrierEnvelope[version=" + version
                + ", mode=" + mode
                + ", keyId=" + keyId
                + ", relay=" + (relay != null)
                + ", signatureLength=" + signature.length + "]";
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof C4CarrierEnvelope that
                && version == that.version
                && mode == that.mode
                && attestation.equals(that.attestation)
                && keyId.equals(that.keyId)
                && Arrays.equals(signature, that.signature)
                && Objects.equals(relay, that.relay);
    }

    @Override
    public int hashCode() {
        return 31 * Objects.hash(version, mode, attestation, keyId, relay)
                + Arrays.hashCode(signature);
    }
}
