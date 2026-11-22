package com.acquiring.simulator.channel;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Ephemeral in-memory Ed25519 fixtures for C4 verification tests.
 *
 * <p>Private keys are generated in memory only and are never written, logged, serialized or
 * included in evidence. These fixtures are test material, not configured credentials or a
 * key-custody implementation.</p>
 */
final class C4EvidenceFixtures {

    static final String ENV = "SPEC-ENV";
    static final String ISSUER = "ISSUER-SIM";
    static final String PROCESSOR = "PROCESSOR-AUTH";
    static final String NETWORK = "NETWORK-SIM";
    static final String ISSUER_KEY = "TEST-KEY-ISSUER";
    static final String PROCESSOR_KEY = "TEST-KEY-PROCESSOR";
    static final String NETWORK_KEY = "TEST-KEY-NETWORK";
    static final String ACQUIRER = "ACQ-A";
    static final String INSTRUCTION_SHA256 =
            "9fdfe1dc6299cc952923f55ae4cebc21ad5e8b98f0e69acbe9c8e8b38ca3a5e2";

    private C4EvidenceFixtures() {
    }

    static byte[] body() {
        return "0110SIMULATED-RESPONSE".getBytes(StandardCharsets.US_ASCII);
    }

    static String bodySha256() {
        return C4CarrierSchema.sha256Hex(body());
    }

    static KeyPair keyPair() throws GeneralSecurityException {
        return KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
    }

    static C4EvidenceAttestation attestation(
            String principal,
            String keyId,
            String actor,
            String stage,
            String de39,
            String result,
            String reason,
            String responseSha256) {
        return new C4EvidenceAttestation(
                1,
                ENV,
                principal,
                "EVID-001",
                INSTRUCTION_SHA256,
                "SIM-ISO87A-v1",
                "AUTH-001",
                "TX-001",
                "CORR-001",
                "ATT-001",
                ACQUIRER,
                "MERCHANT-A",
                "TERMINAL-A",
                "PAY-001",
                1,
                1000,
                "840",
                "000001",
                "RRN123456789",
                "1118120000",
                "20261118120000",
                actor,
                stage,
                de39,
                result,
                reason,
                null,
                "2026-11-18T12:00:01Z",
                principal,
                keyId,
                responseSha256);
    }

    static C4EvidenceAttestation attestationWithBounds(
            String rrn,
            String authorizationId,
            String transactionId,
            String instructionAttemptId,
            String authorizationReference,
            int amountMinor,
            String responseSha256) {
        return new C4EvidenceAttestation(
                1,
                ENV,
                ISSUER,
                "EVID-001",
                INSTRUCTION_SHA256,
                "SIM-ISO87A-v1",
                authorizationId,
                transactionId,
                "CORR-001",
                instructionAttemptId,
                ACQUIRER,
                "MERCHANT-A",
                "TERMINAL-A",
                "PAY-001",
                1,
                amountMinor,
                "840",
                "000001",
                rrn,
                "1118120000",
                "20261118120000",
                "ISSUER",
                "ISSUER_REPLIED",
                "00",
                "APPROVED",
                "APPROVED",
                authorizationReference,
                "2026-11-18T12:00:01Z",
                ISSUER,
                ISSUER_KEY,
                responseSha256);
    }

    static byte[] sign(C4EvidenceAttestation attestation, PrivateKey key)
            throws GeneralSecurityException {
        return signBytes(attestation.toCanonicalBytes(), key);
    }

    static byte[] signBytes(byte[] signedBytes, PrivateKey key)
            throws GeneralSecurityException {
        Signature signer = Signature.getInstance("Ed25519");
        signer.initSign(key);
        signer.update(signedBytes);
        return signer.sign();
    }

    static byte[] signRelay(C4RelayAttestation relay, PrivateKey key)
            throws GeneralSecurityException {
        return signBytes(relay.signedBytes(), key);
    }

    static C4CarrierEnvelope direct(C4EvidenceAttestation attestation, PrivateKey key)
            throws GeneralSecurityException {
        return new C4CarrierEnvelope(
                C4CarrierEnvelope.VERSION,
                C4CarrierEnvelope.Mode.DIRECT,
                attestation,
                attestation.keyId(),
                sign(attestation, key),
                null);
    }

    static C4RelayAttestation relayAttestation(
            C4EvidenceAttestation attestation, byte[] signature) {
        return new C4RelayAttestation(
                C4RelayAttestation.PRINCIPAL,
                "RELAY-001",
                "2026-11-18T12:00:02Z",
                NETWORK_KEY,
                bodySha256(),
                attestation.canonicalSha256(),
                signature);
    }

    static C4CarrierEnvelope relay(
            C4EvidenceAttestation attestation,
            PrivateKey issuerKey,
            PrivateKey networkKey)
            throws GeneralSecurityException {
        C4RelayAttestation unsigned = relayAttestation(
                attestation, new byte[C4CarrierParser.SIGNATURE_BYTES]);
        C4RelayAttestation signed = new C4RelayAttestation(
                unsigned.principal(),
                unsigned.receiptId(),
                unsigned.deliveredAt(),
                unsigned.keyId(),
                unsigned.responseSha256(),
                unsigned.attestationSha256(),
                signRelay(unsigned, networkKey));
        return new C4CarrierEnvelope(
                C4CarrierEnvelope.VERSION,
                C4CarrierEnvelope.Mode.RELAY,
                attestation,
                attestation.keyId(),
                sign(attestation, issuerKey),
                signed);
    }

    static C4CarrierCandidate candidate(
            C4CarrierEnvelope envelope, byte[] body, String sender) {
        return C4CarrierParser.parse(transport(envelope, body, sender));
    }

    static C4TransportResponse transport(
            C4CarrierEnvelope envelope, byte[] body, String sender) {
        Map<String, List<String>> headers = new LinkedHashMap<>();
        headers.put("Content-Type", List.of("application/octet-stream"));
        headers.put(C4CarrierParser.HEADER_NAME, List.of(
                Base64.getEncoder().encodeToString(envelope.toCanonicalBytes())));
        int total = headers.entrySet().stream()
                .mapToInt(entry -> entry.getKey().length() + 2
                        + entry.getValue().get(0).length() + 2)
                .sum();
        return new C4TransportResponse(200, headers, body, sender, total);
    }

    static C4AuthenticatedTransportContext context(String sender) {
        return new C4AuthenticatedTransportContext(ENV, sender);
    }

    static C4TrustedPrincipalKey trusted(
            String principal, String keyId, C4PrincipalRole role, PublicKey publicKey,
            String... mandateRefs) {
        return new C4TrustedPrincipalKey(
                ENV, principal, keyId, role, Set.of(mandateRefs), publicKey);
    }

    static C4KeyRegistry registry(C4TrustedPrincipalKey... keys) {
        Map<String, C4TrustedPrincipalKey> byTriple = new LinkedHashMap<>();
        for (C4TrustedPrincipalKey key : keys) {
            byTriple.put(triple(key.environmentId(), key.principal(), key.keyId()), key);
        }
        return (environmentId, principal, keyId) ->
                Optional.ofNullable(byTriple.get(triple(environmentId, principal, keyId)));
    }

    static C4KeyRegistry emptyRegistry() {
        return (environmentId, principal, keyId) -> Optional.empty();
    }

    private static String triple(String environmentId, String principal, String keyId) {
        return environmentId + "\u0000" + principal + "\u0000" + keyId;
    }
}
