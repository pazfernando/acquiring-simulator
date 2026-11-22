package com.acquiring.simulator.adapter.aws;

import com.acquiring.simulator.channel.C4AuthenticatedTransportContext;
import com.acquiring.simulator.channel.C4CarrierCandidate;
import com.acquiring.simulator.channel.C4CarrierEnvelope;
import com.acquiring.simulator.channel.C4EvidenceAttestation;
import com.acquiring.simulator.channel.C4KeyRegistry;
import com.acquiring.simulator.channel.C4LateDeliveryCandidate;
import com.acquiring.simulator.channel.C4LateDeliveryEnvelope;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Objects;

/**
 * One bounded internal signed-evidence service facade (observation contract O3/P5; channel
 * contract C4/P6 late-delivery paragraph; OBS-08/OBS-10).
 *
 * <p>This class only routes. It owns no verification rule, no storage, no receipt, no business
 * transition and no conflict classification of its own: the already-authenticated transport
 * context and the exact C4 delivery envelope are handled by the existing
 * {@link SignedEvidenceReceiptGateway} and {@link LateSignedEvidenceReceiptGateway}, which keep
 * the real C4 DIRECT/RELAY verifier, the real signed-P5/P6 binder, the trusted-context
 * authorization before any storage read, and the atomic receipt transactions.</p>
 *
 * <p>Routing is decided by the signed evidence-time classification produced inside the late
 * gateway, never by the caller and never by the delivery time. An envelope carrying evidence that
 * became available at or before the deadline (on time, or a delivery delay) is handed to the
 * normal signed admission exactly once, so that path remains the single owner of the write; no
 * late metadata, receipt or label is created for it. Evidence that became available strictly
 * after the deadline is admitted by the late gateway exactly once. There is no second attempt,
 * resend, retry of the business write, reversal or downstream call.</p>
 *
 * <p>The facade accepts no "verified", "bound", "late" or "already admitted" flag and no
 * pre-verified reply. It also does not accept a caller-selected route: it recomputes the carriage
 * hashes from the exact envelope content, derives the candidate's sender binding only from the
 * authenticated transport context and the envelope mode, and then lets the gateways re-run the
 * full verifier and binder. Diagnostic forwarding is not admitted by this facade, because the
 * signed receipt gateways verify without the separate forwarding permission; such a delivery is
 * refused before any storage read rather than being silently degraded.</p>
 *
 * <p>An unexpected runtime failure is not converted into a new success or a new
 * "not committed" claim; the underlying gateway decisions, including {@code UNKNOWN},
 * {@code CONTENT_CONFLICT}, replay and persisted-version semantics, are returned unchanged.</p>
 *
 * <p><strong>Not production-wired.</strong> This class is not referenced by
 * {@code AuthorizationService} or {@code AwsRuntime}. It has no HTTP route, no endpoint, no
 * runtime activation, no key custody and no downstream call. It does not authenticate a sender,
 * approve keys, approve capacity or invent a deadline; those inputs must come from the transport
 * and composition boundaries. The runtime stays inactive and P1 G6 Security remains open.</p>
 */
public final class SignedEvidenceAdmissionService {

    /** Which existing signed-admission corridor handled exactly one attempt. */
    public enum Route {
        /** The exact delivery envelope itself was refused before any gateway or storage access. */
        NONE,
        /** The late classification handed on-time evidence to the normal signed admission once. */
        NORMAL,
        /** The evidence was strictly late and the late signed admission owned the attempt. */
        LATE
    }

    /**
     * @param route  corridor that handled this attempt
     * @param late   late-gateway decision (also carries the hand-off classification for
     *               {@link Route#NORMAL}); never null except for {@link Route#NONE}
     * @param normal normal-gateway decision when {@link Route#NORMAL}; otherwise null
     * @param detail facade-level refusal detail, or null when a gateway produced the decision
     */
    public record Admission(Route route, LateSignedEvidenceReceiptGateway.Decision late,
            SignedEvidenceReceiptGateway.Decision normal, String detail) {

        public Admission {
            Objects.requireNonNull(route, "route");
        }

        /**
         * True only when the corridor that handled this attempt read back a confirmed state,
         * persisted committed-version evidence and a receipt. It grants no downstream action.
         */
        public boolean recorded() {
            return (route == Route.LATE && late != null && late.recorded())
                    || (route == Route.NORMAL && normal != null && normal.recorded());
        }
    }

    private final SignedEvidenceReceiptGateway normalAdmission;
    private final LateSignedEvidenceReceiptGateway lateAdmission;

    public SignedEvidenceAdmissionService(SignedEvidenceReceiptGateway normalAdmission,
            LateSignedEvidenceReceiptGateway lateAdmission) {
        this.normalAdmission = Objects.requireNonNull(normalAdmission, "normalAdmission");
        this.lateAdmission = Objects.requireNonNull(lateAdmission, "lateAdmission");
    }

    /**
     * Admit exactly one signed delivery carried by the exact C4 late-delivery envelope.
     *
     * @param delivery    parsed unverified envelope from {@code C4LateDeliveryParser}; its content
     *                    is re-hashed here and never trusted as a flag
     * @param context     authenticated transport context supplied by the internal service boundary
     * @param registry    configured principal/key registry
     * @param deadline    admission deadline from the durable dispatch policy; no default invented
     * @param deliveredAt authenticated delivery time; never used to relabel lateness
     */
    public Admission admit(C4LateDeliveryCandidate delivery,
            C4AuthenticatedTransportContext context, C4KeyRegistry registry, Instant deadline,
            Instant deliveredAt) {
        Objects.requireNonNull(delivery, "delivery");
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(registry, "registry");

        C4LateDeliveryEnvelope envelope = delivery.lateEnvelope();
        C4CarrierEnvelope evidence = envelope.evidence();
        C4EvidenceAttestation signed = evidence.attestation();

        // Recompute the exact carriage facts from the envelope content instead of accepting the
        // presented labels. These are integrity checks only: no signature, key, role, mandate,
        // payment result or receipt durability is established here.
        if (!delivery.canonicalLateEnvelopeSha256().equals(envelope.canonicalSha256())) {
            return none("delivery envelope bytes do not match the presented envelope hash");
        }
        if (!delivery.canonicalAttestationSha256().equals(signed.canonicalSha256())) {
            return none("delivery evidence does not match the presented attestation hash");
        }

        byte[] response;
        try {
            response = Base64.getDecoder().decode(envelope.responseBodyBase64());
        } catch (IllegalArgumentException invalidBase64) {
            return none("delivery response body is not canonical base64");
        }
        String responseSha256 = sha256Hex(response);
        if (!responseSha256.equals(delivery.responseBodySha256())
                || !responseSha256.equals(signed.responseSha256())) {
            return none("delivery response body does not match the signed response hash");
        }

        // The sender binding is derived only from the authenticated transport identity and the
        // envelope mode. The gateways still enforce the same equality themselves; a diagnostic
        // forwarder is refused here because the signed receipt gateways admit no forwarded sender.
        String proofPrincipal = evidence.mode() == C4CarrierEnvelope.Mode.DIRECT
                ? signed.signingPrincipal()
                : evidence.relay().principal();
        if (!context.authenticatedSender().equals(proofPrincipal)) {
            return none("delivery transport sender is not the direct signer or relay principal;"
                    + " diagnostic forwarding is not admitted by signed receipt admission");
        }
        C4CarrierCandidate.SenderBinding senderBinding =
                evidence.mode() == C4CarrierEnvelope.Mode.DIRECT
                        ? C4CarrierCandidate.SenderBinding.DIRECT_SENDER_MATCHED
                        : C4CarrierCandidate.SenderBinding.RELAY_SENDER_MATCHED;
        C4CarrierCandidate candidate = new C4CarrierCandidate(
                evidence,
                responseSha256,
                signed.canonicalSha256(),
                senderBinding,
                C4CarrierCandidate.AttestationState.UNVERIFIED_CANDIDATE,
                C4CarrierCandidate.KeyResolution.UNRESOLVED);

        LateSignedEvidenceReceiptGateway.Decision late = lateAdmission.admit(
                candidate, signed, context, registry, response, deadline, deliveredAt,
                envelope.deliveryReceiptId());

        if (late.status() == LateSignedEvidenceReceiptGateway.Status.NORMAL_RESULT_ADMISSION) {
            // Evidence time, not delivery time, decided this. The normal signed admission owns the
            // one and only write for this attempt; it is invoked once and only here.
            SignedEvidenceReceiptGateway.Decision normal = normalAdmission.admit(
                    candidate, signed, context, registry, response);
            return new Admission(Route.NORMAL, late, normal,
                    "on-time evidence delivered late was handed to the normal signed admission once");
        }
        return new Admission(Route.LATE, late, null, null);
    }

    private static Admission none(String detail) {
        return new Admission(Route.NONE, null, null, detail);
    }

    private static String sha256Hex(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException unavailable) {
            throw new IllegalStateException("SHA-256 is unavailable", unavailable);
        }
    }
}
