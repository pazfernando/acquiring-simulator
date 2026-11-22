package com.acquiring.simulator.channel;

import java.util.Objects;

/**
 * Composes the closed C4 late-delivery envelope with the existing real DIRECT/RELAY
 * Ed25519 verifier.
 *
 * <p>The late-delivery transport sender must be the authenticated DIRECT signer or the
 * configured RELAY principal. A different sender is admitted only when the explicit
 * registry-scoped {@link C4KeyRegistry#diagnosticForwardPermitted} seam returns true for
 * the exact environment, principal and acquirer; otherwise it fails closed. A diagnostic
 * forwarder gains no role, signing or outcome authority: the unchanged inner proof is
 * still verified, and its issuer identity remains the decision evidence.</p>
 *
 * <p>This verifier performs no payment, send, resend, storage, admission, receipt-durability
 * inference, clock read or I/O. The delivery receipt id is an envelope label with no
 * decision authority. A successful result is cryptographic and transport binding only.</p>
 */
public final class C4LateDeliveryVerifier {

    private C4LateDeliveryVerifier() {
    }

    /**
     * Verify exactly one parsed late-delivery candidate.
     *
     * @throws C4VerificationException on the first failed cryptographic, trust or delivery
     *     sender rule; never returns a partially verified result
     * @throws C4CarrierException if a defensive hash/shape recheck fails
     */
    public static C4VerifiedLateDelivery verify(
            C4LateDeliveryCandidate candidate,
            C4AuthenticatedTransportContext context,
            C4KeyRegistry registry) {
        Objects.requireNonNull(candidate, "candidate");
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(registry, "registry");

        C4CarrierEnvelope envelope = candidate.lateEnvelope().evidence();
        C4EvidenceAttestation attestation = envelope.attestation();

        if (!candidate.responseBodySha256().equals(attestation.responseSha256())) {
            throw C4VerificationException.of(
                    C4VerificationViolation.HASH_BINDING, "responseSha256");
        }
        if (!candidate.canonicalAttestationSha256().equals(attestation.canonicalSha256())) {
            throw C4VerificationException.of(
                    C4VerificationViolation.ATTESTATION_HASH_BINDING, "attestation");
        }

        String proofPrincipal = switch (envelope.mode()) {
            case DIRECT -> attestation.signingPrincipal();
            case RELAY -> envelope.relay().principal();
        };

        C4CarrierCandidate.SenderBinding senderBinding;
        boolean forwarded;
        if (context.authenticatedSender().equals(proofPrincipal)) {
            senderBinding = envelope.mode() == C4CarrierEnvelope.Mode.DIRECT
                    ? C4CarrierCandidate.SenderBinding.DIRECT_SENDER_MATCHED
                    : C4CarrierCandidate.SenderBinding.RELAY_SENDER_MATCHED;
            forwarded = false;
        } else {
            if (!registry.diagnosticForwardPermitted(
                    context.environmentId(),
                    context.authenticatedSender(),
                    attestation.acquirerRef())) {
                throw C4VerificationException.of(
                        C4VerificationViolation.DIAGNOSTIC_FORWARD_PERMISSION,
                        "diagnosticForwardPermission");
            }
            senderBinding = C4CarrierCandidate.SenderBinding.DIAGNOSTIC_FORWARD_PERMITTED;
            forwarded = true;
        }

        C4CarrierCandidate carrier = new C4CarrierCandidate(
                envelope,
                candidate.responseBodySha256(),
                candidate.canonicalAttestationSha256(),
                senderBinding,
                C4CarrierCandidate.AttestationState.UNVERIFIED_CANDIDATE,
                C4CarrierCandidate.KeyResolution.UNRESOLVED);

        C4VerifiedAttestation verified = forwarded
                ? C4EvidenceVerifier.verifyForwarded(carrier, context, registry)
                : C4EvidenceVerifier.verify(carrier, context, registry);

        return new C4VerifiedLateDelivery(
                candidate.lateEnvelope().deliveryReceiptId(),
                verified,
                senderBinding,
                candidate.canonicalLateEnvelopeSha256());
    }
}
