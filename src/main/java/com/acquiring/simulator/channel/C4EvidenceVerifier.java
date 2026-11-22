package com.acquiring.simulator.channel;

import java.security.GeneralSecurityException;
import java.security.PublicKey;
import java.security.Signature;
import java.security.SignatureException;
import java.util.Objects;

/**
 * One real DIRECT or RELAY C4 attestation verifier using the JDK Ed25519 provider.
 *
 * <p>DIRECT verifies the Ed25519 signature over the exact canonical attestation bytes
 * against one configured public key resolved by
 * {@code (environmentId, signingPrincipal, keyId)}, then enforces the authenticated
 * transport sender, environment, outer/signed/registry key equality, P5 role-to-P4
 * actor/stage/DE39/result/reason row and acquirer mandate. The candidate's body hash and
 * canonical-attestation hash must still equal the signed values.</p>
 *
 * <p>RELAY keeps the exact same inner issuer attestation, key and signature, and
 * additionally verifies the configured {@code NETWORK-SIM} countersignature over the
 * canonical relay object excluding {@code signature}. The relay binds its receipt, delivery
 * time, exact response hash and canonical inner-attestation hash; the authenticated sender,
 * relay principal, relay key, environment, {@code NETWORK_SIM} role and acquirer mandate
 * must all agree. The network cannot mint an issuer outcome or replace the issuer
 * signature: the inner signer must resolve to {@code ISSUER_SIM} and an issuer P4 row.</p>
 *
 * <p>Rejection categories stay distinct and sanitized. Signature/key custody, TLS/IAM
 * principal mapping and business admission are separate concerns.</p>
 */
public final class C4EvidenceVerifier {

    private C4EvidenceVerifier() {
    }

    /**
     * Verify exactly one DIRECT or RELAY candidate.
     *
     * @throws C4VerificationException on the first failed rule; never returns a partially
     *     verified result
     */
    public static C4VerifiedAttestation verify(
            C4CarrierCandidate candidate,
            C4AuthenticatedTransportContext context,
            C4KeyRegistry registry) {
        return verifyInternal(candidate, context, registry, false);
    }

    /**
     * Verify exactly one candidate whose transport sender was separately authorised as an
     * explicit diagnostic forwarder by the late-delivery seam.
     *
     * <p>This skips only the equality between the transport sender and the proof principal
     * (DIRECT signer or relay principal). It does not skip the environment, key, role,
     * mandate, hash, inner issuer or relay countersignature checks. It must be called only
     * after {@link C4KeyRegistry#diagnosticForwardPermitted} returned true for the exact
     * transport principal and acquirer scope. It grants no new role, signing authority or
     * outcome authority; the inner issuer proof remains the only decision evidence.</p>
     */
    static C4VerifiedAttestation verifyForwarded(
            C4CarrierCandidate candidate,
            C4AuthenticatedTransportContext context,
            C4KeyRegistry registry) {
        return verifyInternal(candidate, context, registry, true);
    }

    private static C4VerifiedAttestation verifyInternal(
            C4CarrierCandidate candidate,
            C4AuthenticatedTransportContext context,
            C4KeyRegistry registry,
            boolean forwarded) {
        Objects.requireNonNull(candidate, "candidate");
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(registry, "registry");

        C4CarrierEnvelope envelope = candidate.envelope();
        C4EvidenceAttestation attestation = envelope.attestation();

        if (!candidate.responseBodySha256().equals(attestation.responseSha256())) {
            throw fail(C4VerificationViolation.HASH_BINDING, "responseSha256");
        }
        if (!candidate.canonicalAttestationSha256().equals(attestation.canonicalSha256())) {
            throw fail(C4VerificationViolation.ATTESTATION_HASH_BINDING, "attestation");
        }
        if (!context.environmentId().equals(attestation.environmentId())) {
            throw fail(C4VerificationViolation.ENVIRONMENT_BINDING, "environmentId");
        }
        if (!envelope.keyId().equals(attestation.keyId())) {
            throw fail(C4VerificationViolation.KEY_BINDING, "keyId");
        }

        return switch (envelope.mode()) {
            case DIRECT -> verifyDirect(
                    candidate, context, registry, envelope, attestation, forwarded);
            case RELAY -> verifyRelay(
                    candidate, context, registry, envelope, attestation, forwarded);
        };
    }

    private static C4VerifiedAttestation verifyDirect(
            C4CarrierCandidate candidate,
            C4AuthenticatedTransportContext context,
            C4KeyRegistry registry,
            C4CarrierEnvelope envelope,
            C4EvidenceAttestation attestation,
            boolean forwarded) {
        C4TrustedPrincipalKey trusted = registry
                .resolve(attestation.environmentId(), attestation.signingPrincipal(),
                        envelope.keyId())
                .orElseThrow(() -> fail(C4VerificationViolation.UNKNOWN_KEY, "keyId"));
        requireTrustedBinding(
                trusted,
                attestation.environmentId(),
                attestation.signingPrincipal(),
                envelope.keyId(),
                "keyId");

        if (!forwarded
                && !context.authenticatedSender().equals(attestation.signingPrincipal())) {
            throw fail(C4VerificationViolation.AUTHENTICATED_SENDER, "signingPrincipal");
        }

        C4AttestationType type = attestationType(trusted.role(), attestation);
        if (type == null) {
            throw fail(C4VerificationViolation.ROLE_NOT_PERMITTED, "role");
        }
        if (!trusted.acquirerMandateRefs().contains(attestation.acquirerRef())) {
            throw fail(C4VerificationViolation.MANDATE_BINDING, "acquirerRef");
        }

        verifyEd25519(
                trusted.publicKey(), attestation.toCanonicalBytes(), envelope.signature(),
                "signature");

        return new C4VerifiedAttestation(
                attestation.environmentId(),
                attestation.evidenceId(),
                attestation.producerPrincipal(),
                attestation.signingPrincipal(),
                envelope.keyId(),
                trusted.role(),
                type,
                attestation.authorizationId(),
                attestation.transactionId(),
                attestation.acquirerRef(),
                attestation.instructionSha256(),
                candidate.responseBodySha256(),
                candidate.canonicalAttestationSha256(),
                forwarded
                        ? C4VerifiedAttestation.VerificationScope
                                .DIRECT_ED25519_AND_TRUST_BINDINGS_VERIFIED_VIA_AUTHORIZED_FORWARD
                        : C4VerifiedAttestation.VerificationScope
                                .DIRECT_ED25519_AND_TRUST_BINDINGS_VERIFIED,
                null);
    }

    private static C4VerifiedAttestation verifyRelay(
            C4CarrierCandidate candidate,
            C4AuthenticatedTransportContext context,
            C4KeyRegistry registry,
            C4CarrierEnvelope envelope,
            C4EvidenceAttestation attestation,
            boolean forwarded) {
        C4RelayAttestation relay = envelope.relay();
        if (relay == null) {
            throw fail(C4VerificationViolation.RELAY_BINDING, "relay");
        }
        if (!C4RelayAttestation.PRINCIPAL.equals(relay.principal())) {
            throw fail(C4VerificationViolation.RELAY_BINDING, "relayPrincipal");
        }
        if (!forwarded && !context.authenticatedSender().equals(relay.principal())) {
            throw fail(C4VerificationViolation.AUTHENTICATED_SENDER, "relayPrincipal");
        }
        if (!relay.responseSha256().equals(candidate.responseBodySha256())
                || !relay.responseSha256().equals(attestation.responseSha256())) {
            throw fail(C4VerificationViolation.RELAY_BINDING, "relayResponseSha256");
        }
        if (!relay.attestationSha256().equals(candidate.canonicalAttestationSha256())
                || !relay.attestationSha256().equals(attestation.canonicalSha256())) {
            throw fail(
                    C4VerificationViolation.RELAY_BINDING, "relayAttestationSha256");
        }

        C4TrustedPrincipalKey issuer = registry
                .resolve(attestation.environmentId(), attestation.signingPrincipal(),
                        envelope.keyId())
                .orElseThrow(() -> fail(C4VerificationViolation.UNKNOWN_KEY, "issuerKeyId"));
        requireTrustedBinding(
                issuer,
                attestation.environmentId(),
                attestation.signingPrincipal(),
                envelope.keyId(),
                "issuerKeyId");
        if (issuer.role() != C4PrincipalRole.ISSUER_SIM) {
            throw fail(C4VerificationViolation.ROLE_NOT_PERMITTED, "issuerRole");
        }
        C4AttestationType type = issuerType(attestation);
        if (type == null) {
            throw fail(C4VerificationViolation.ROLE_NOT_PERMITTED, "role");
        }
        if (!issuer.acquirerMandateRefs().contains(attestation.acquirerRef())) {
            throw fail(C4VerificationViolation.MANDATE_BINDING, "issuerMandate");
        }

        C4TrustedPrincipalKey relayKey = registry
                .resolve(attestation.environmentId(), relay.principal(), relay.keyId())
                .orElseThrow(() -> fail(C4VerificationViolation.UNKNOWN_KEY, "relayKeyId"));
        requireTrustedBinding(
                relayKey,
                attestation.environmentId(),
                relay.principal(),
                relay.keyId(),
                "relayKeyId");
        if (relayKey.role() != C4PrincipalRole.NETWORK_SIM) {
            throw fail(C4VerificationViolation.ROLE_NOT_PERMITTED, "relayRole");
        }
        if (!relayKey.acquirerMandateRefs().contains(attestation.acquirerRef())) {
            throw fail(C4VerificationViolation.MANDATE_BINDING, "relayMandate");
        }

        verifyEd25519(
                relayKey.publicKey(), relay.signedBytes(), relay.signature(),
                "relaySignature");
        verifyEd25519(
                issuer.publicKey(), attestation.toCanonicalBytes(), envelope.signature(),
                "issuerSignature");

        return new C4VerifiedAttestation(
                attestation.environmentId(),
                attestation.evidenceId(),
                attestation.producerPrincipal(),
                attestation.signingPrincipal(),
                envelope.keyId(),
                issuer.role(),
                type,
                attestation.authorizationId(),
                attestation.transactionId(),
                attestation.acquirerRef(),
                attestation.instructionSha256(),
                candidate.responseBodySha256(),
                candidate.canonicalAttestationSha256(),
                forwarded
                        ? C4VerifiedAttestation.VerificationScope
                                .RELAY_ED25519_AND_TRUST_BINDINGS_VERIFIED_VIA_AUTHORIZED_FORWARD
                        : C4VerifiedAttestation.VerificationScope
                                .RELAY_ED25519_AND_TRUST_BINDINGS_VERIFIED,
                new C4VerifiedAttestation.RelayBinding(
                        relay.principal(),
                        relay.keyId(),
                        relay.receiptId(),
                        relay.deliveredAt()));
    }

    private static void requireTrustedBinding(
            C4TrustedPrincipalKey trusted,
            String environmentId,
            String principal,
            String keyId,
            String field) {
        if (!trusted.environmentId().equals(environmentId)
                || !trusted.principal().equals(principal)
                || !trusted.keyId().equals(keyId)) {
            throw fail(C4VerificationViolation.KEY_BINDING, field);
        }
    }

    /**
     * Exact P5 role to P4 row map. A null result means the signed actor/stage/DE39/result/
     * reason combination is not permitted for that role; ACL-AUTH never signs evidence.
     */
    private static C4AttestationType attestationType(
            C4PrincipalRole role, C4EvidenceAttestation a) {
        return switch (role) {
            case ISSUER_SIM -> issuerType(a);
            case PROCESSOR_AUTH -> processorType(a);
            case NETWORK_SIM -> networkType(a);
            case ACL_AUTH -> null;
        };
    }

    private static C4AttestationType issuerType(C4EvidenceAttestation a) {
        if (is(a, "ISSUER", "ISSUER_REPLIED", "00", "APPROVED", "APPROVED")) {
            return C4AttestationType.ISSUER_APPROVAL;
        }
        if (is(a, "ISSUER", "ISSUER_REPLIED", "05", "DECLINED", "DELIBERATE_DECLINE")) {
            return C4AttestationType.ISSUER_DECLINE;
        }
        return null;
    }

    private static C4AttestationType processorType(C4EvidenceAttestation a) {
        if ("PROCESSOR".equals(a.actor()) && "BEFORE_NETWORK".equals(a.stage())
                && "30".equals(a.DE39()) && "PROCESSOR_REJECTED".equals(a.result())
                && ("FORMAT".equals(a.reason()) || "DISALLOWED".equals(a.reason()))) {
            return C4AttestationType.PROCESSOR_REJECTION_BEFORE_NETWORK;
        }
        if (is(a, "PROCESSOR", "POSSIBLE_ISSUER", "91", "NOT_CONFIRMED", "UNCERTAIN")
                || is(a, "PROCESSOR", "POSSIBLE_ISSUER", "91", "NOT_CONFIRMED", "UNAVAILABLE")) {
            return C4AttestationType.POSSIBLE_ISSUER_UNCERTAINTY;
        }
        return null;
    }

    private static C4AttestationType networkType(C4EvidenceAttestation a) {
        if ("NETWORK".equals(a.actor()) && "BEFORE_ISSUER".equals(a.stage())
                && "30".equals(a.DE39()) && "PROCESSOR_REJECTED".equals(a.result())
                && ("FORMAT".equals(a.reason()) || "DISALLOWED".equals(a.reason()))) {
            return C4AttestationType.NETWORK_REJECTION_BEFORE_ISSUER;
        }
        if (is(a, "NETWORK", "BEFORE_ISSUER", "91", "PROCESSOR_REJECTED", "UNAVAILABLE")) {
            return C4AttestationType.NETWORK_NON_FORWARDING_REJECTION;
        }
        if (is(a, "NETWORK", "POSSIBLE_ISSUER", "91", "NOT_CONFIRMED", "UNCERTAIN")
                || is(a, "NETWORK", "POSSIBLE_ISSUER", "91", "NOT_CONFIRMED", "UNAVAILABLE")) {
            return C4AttestationType.POSSIBLE_ISSUER_UNCERTAINTY;
        }
        return null;
    }

    private static boolean is(
            C4EvidenceAttestation a,
            String actor,
            String stage,
            String de39,
            String result,
            String reason) {
        return actor.equals(a.actor())
                && stage.equals(a.stage())
                && de39.equals(a.DE39())
                && result.equals(a.result())
                && reason.equals(a.reason());
    }

    private static void verifyEd25519(
            PublicKey key, byte[] signedBytes, byte[] signature, String field) {
        try {
            Signature verifier = Signature.getInstance("Ed25519");
            verifier.initVerify(key);
            verifier.update(signedBytes);
            if (!verifier.verify(signature)) {
                throw fail(C4VerificationViolation.BAD_SIGNATURE, field);
            }
        } catch (GeneralSecurityException e) {
            if (e instanceof SignatureException) {
                throw fail(C4VerificationViolation.BAD_SIGNATURE, field);
            }
            throw fail(C4VerificationViolation.SIGNATURE_UNAVAILABLE, field);
        }
    }

    private static C4VerificationException fail(
            C4VerificationViolation violation, String field) {
        return C4VerificationException.of(violation, field);
    }
}
