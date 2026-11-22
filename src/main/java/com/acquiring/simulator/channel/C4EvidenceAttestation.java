package com.acquiring.simulator.channel;

import java.util.List;

/**
 * Closed P5 issuer/actor attestation carried inside a C4 envelope.
 *
 * <p>This is the complete signed P5 field set for the issuer attestation: every bound P5
 * business field except {@code relayPrincipal}, {@code transportReceiptId} and
 * {@code issuerAttestation}, plus {@code signingPrincipal}, {@code keyId} and the added
 * {@code responseSha256}. Exactly these thirty-one keys are representable; there is no
 * extension map, no {@code VALID} oracle flag and no unknown-key escape hatch.</p>
 *
 * <p>Construction validates shape only, including the P1/P2 profile bounds: {@code RRN}
 * is exactly twelve uppercase letters/digits, {@code authorizationId},
 * {@code transactionId} and {@code instructionAttemptId} are 1–64, {@code amountMinor} is
 * 100–999999 and {@code authorizationReference} is null or exactly six uppercase
 * letters/digits. It does not resolve {@code keyId}, check a role or mandate, verify the
 * Ed25519 signature, compare {@code instructionSha256} with a stored instruction or decide
 * whether the attestation is trustworthy. Those remain later slices; this type is always
 * carried by an explicitly unverified candidate.</p>
 */
public record C4EvidenceAttestation(
        int schemaVersion,
        String environmentId,
        String producerPrincipal,
        String evidenceId,
        String instructionSha256,
        String profile,
        String authorizationId,
        String transactionId,
        String correlationId,
        String instructionAttemptId,
        String acquirerRef,
        String merchantRef,
        String terminalRef,
        String paymentRef,
        int generation,
        int amountMinor,
        String currency,
        String STAN,
        String RRN,
        String DE7,
        String sent,
        String actor,
        String stage,
        String DE39,
        String result,
        String reason,
        String authorizationReference,
        String availableAt,
        String signingPrincipal,
        String keyId,
        String responseSha256) {

    /** Approved P5 attestation schema version. */
    public static final int SCHEMA_VERSION = 1;

    /** Approved ISO profile literal. */
    public static final String PROFILE = "SIM-ISO87A-v1";

    /** The exact closed key set, in RFC8785 canonical order. */
    public static final List<String> KEYS = List.of(
            "DE39", "DE7", "RRN", "STAN", "acquirerRef", "actor", "amountMinor",
            "authorizationId", "authorizationReference", "availableAt", "correlationId",
            "currency", "environmentId", "evidenceId", "generation", "instructionAttemptId",
            "instructionSha256", "keyId", "merchantRef", "paymentRef", "producerPrincipal",
            "profile", "reason", "responseSha256", "result", "schemaVersion", "sent",
            "signingPrincipal", "stage", "terminalRef", "transactionId");

    public C4EvidenceAttestation {
        if (schemaVersion != SCHEMA_VERSION) {
            throw C4CarrierException.of(C4CarrierViolation.CARRIER_SCHEMA, "schemaVersion");
        }
        C4CarrierSchema.requireId("environmentId", environmentId, 64);
        C4CarrierSchema.requireId("producerPrincipal", producerPrincipal, 64);
        C4CarrierSchema.requireId("evidenceId", evidenceId, 64);
        C4CarrierSchema.requireHex64("instructionSha256", instructionSha256);
        C4CarrierSchema.requireExact("profile", profile, PROFILE);
        C4CarrierSchema.requireId("authorizationId", authorizationId, 64);
        C4CarrierSchema.requireId("transactionId", transactionId, 64);
        C4CarrierSchema.requireId("correlationId", correlationId, 64);
        C4CarrierSchema.requireId("instructionAttemptId", instructionAttemptId, 64);
        C4CarrierSchema.requireId("acquirerRef", acquirerRef, 128);
        C4CarrierSchema.requireId("merchantRef", merchantRef, 128);
        C4CarrierSchema.requireId("terminalRef", terminalRef, 128);
        C4CarrierSchema.requireId("paymentRef", paymentRef, 128);
        C4CarrierSchema.requirePositiveInt("generation", generation, Integer.MAX_VALUE);
        C4CarrierSchema.requireIntRange("amountMinor", amountMinor, 100, 999_999);
        C4CarrierSchema.requireDigits("currency", currency, 3);
        C4CarrierSchema.requireDigits("STAN", STAN, 6);
        C4CarrierSchema.requireUppercaseAlnumExact("RRN", RRN, 12);
        C4CarrierSchema.requireDigits("DE7", DE7, 10);
        C4CarrierSchema.requireDigits("sent", sent, 14);
        C4CarrierSchema.requireText("actor", actor, 64);
        C4CarrierSchema.requireText("stage", stage, 64);
        C4CarrierSchema.requireDigits("DE39", DE39, 2);
        C4CarrierSchema.requireText("result", result, 64);
        C4CarrierSchema.requireText("reason", reason, 64);
        C4CarrierSchema.requireNullableUppercaseAlnumExact(
                "authorizationReference", authorizationReference, 6);
        C4CarrierSchema.requireIsoInstantUtc("availableAt", availableAt);
        C4CarrierSchema.requireId("signingPrincipal", signingPrincipal, 64);
        C4CarrierSchema.requireId("keyId", keyId, 64);
        C4CarrierSchema.requireHex64("responseSha256", responseSha256);
    }

    /** Canonical RFC8785 JSON bytes of this attestation. */
    public byte[] toCanonicalBytes() {
        return C4CarrierSchema.asciiBytes(C4CarrierCanonicalJson.attestation(this));
    }

    /** SHA-256 of the canonical attestation bytes, lowercase hex. */
    public String canonicalSha256() {
        return C4CarrierSchema.sha256Hex(toCanonicalBytes());
    }

    /** Never renders the attestation values. */
    @Override
    public String toString() {
        return "C4EvidenceAttestation[evidenceId=" + evidenceId
                + ", authorizationId=" + authorizationId
                + ", transactionId=" + transactionId
                + ", keyId=" + keyId
                + ", signingPrincipal=" + signingPrincipal
                + ", responseSha256=" + responseSha256 + "]";
    }
}
