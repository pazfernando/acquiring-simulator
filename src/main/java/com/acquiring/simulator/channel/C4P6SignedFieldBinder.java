package com.acquiring.simulator.channel;

import com.acquiring.simulator.protocol.iso8583.Iso8583De48Request;
import com.acquiring.simulator.protocol.iso8583.Iso8583De48RequestParser;
import com.acquiring.simulator.protocol.iso8583.Iso8583De48Response;
import com.acquiring.simulator.protocol.iso8583.Iso8583De48ResponseParser;
import com.acquiring.simulator.protocol.iso8583.Iso8583DomainException;
import com.acquiring.simulator.protocol.iso8583.Iso8583DomainValidator;
import com.acquiring.simulator.protocol.iso8583.Iso8583InstructionBindingException;
import com.acquiring.simulator.protocol.iso8583.Iso8583InstructionBindingValidator;
import com.acquiring.simulator.protocol.iso8583.Iso8583Message;
import com.acquiring.simulator.protocol.iso8583.Iso8583OriginalInstruction;
import com.acquiring.simulator.protocol.iso8583.Iso8583P4MatrixException;
import com.acquiring.simulator.protocol.iso8583.Iso8583P4MatrixValidator;
import com.acquiring.simulator.protocol.iso8583.Iso8583PhysicalInstruction;
import com.acquiring.simulator.protocol.iso8583.Iso8583Scanner;
import com.acquiring.simulator.protocol.iso8583.Iso8583TrustedEvidenceRecord;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Objects;

/**
 * One pure signed-P5/P6 field binding over the exact persisted original instruction and the
 * exact received response bytes.
 *
 * <p>Inputs are an already-produced {@link C4VerifiedAttestation} or
 * {@link C4VerifiedLateDelivery} from {@link C4EvidenceVerifier}/{@link
 * C4LateDeliveryVerifier}, the exact signed {@link C4EvidenceAttestation} content whose
 * canonical hash the verified result names, the <em>current</em> trusted
 * {@link C4AuthenticatedTransportContext}, the immutable persisted original {@code 0100}
 * octets and the exact received {@code 0110} octets. No caller boolean, no
 * "signature-verified" flag and no arbitrary trusted-evidence DTO is accepted: the signed
 * attestation is usable only while its canonical SHA-256 still equals the verified
 * {@code canonicalAttestationSha256} and its identity fields still equal the verified
 * record.</p>
 *
 * <p>The binder never re-encodes an instruction or a response. It hashes the exact
 * persisted/current byte arrays, then runs the approved P6 response order — domain →
 * P4 actor/stage/DE39/result/reason → original instruction/context and exact DE38 binding —
 * and finally checks the signed business fields the P6 layers cannot see (signed identity
 * and result tuple, evidence identifier and {@code availableAt}). Environment, signing
 * principal, relay principal, key id and acquirer reference are re-bound to the current
 * trusted context; a different environment or sender is rejected rather than substituted.
 * The registry mandate is not re-resolved here, but the acquirer reference the mandate was
 * checked against cannot change.</p>
 *
 * <p>Deliberately absent: authentication, issuer truth, key custody/rotation, correctness of
 * the Ed25519 provider, durable processor receipt, duplicate/conflict resolution, atomic
 * evidence+receipt+version+event admission, the financial outcome, HTTP, storage and any
 * runtime wiring. The result carries signed claims only; no result is known or confirmed
 * before the atomic receipt transaction and no identifier is minted.</p>
 */
public final class C4P6SignedFieldBinder {

    /** Response {@code at} projection width: {@code YYYYMMDDhhmmss} UTC. */
    private static final DateTimeFormatter RESPONSE_AT =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss").withZone(ZoneOffset.UTC);

    private C4P6SignedFieldBinder() {
    }

    /**
     * Bind one DIRECT or RELAY verified attestation.
     *
     * @param verified       cryptographic/trust result from the actual verifier
     * @param signed         exact signed attestation content that result names
     * @param currentContext current trusted transport context; never a client value
     * @param persistedOriginal0100 immutable persisted original request octets
     * @param receivedResponse0110 exact received response octets
     * @throws C4P6BindingException on the first failing binding category
     */
    public static C4P6BindingDecision bind(
            C4VerifiedAttestation verified,
            C4EvidenceAttestation signed,
            C4AuthenticatedTransportContext currentContext,
            byte[] persistedOriginal0100,
            byte[] receivedResponse0110) {
        Objects.requireNonNull(verified, "verified");
        Objects.requireNonNull(signed, "signed");
        Objects.requireNonNull(currentContext, "currentContext");
        if (persistedOriginal0100 == null || persistedOriginal0100.length == 0
                || receivedResponse0110 == null || receivedResponse0110.length == 0) {
            throw new C4P6BindingException(
                    C4P6BindingViolation.MISSING_INPUT, "wireBytes", null);
        }

        requireSignedContentMatchesVerified(verified, signed);
        requireCurrentContext(verified, currentContext);

        String originalHash = C4CarrierSchema.sha256Hex(persistedOriginal0100);
        if (!originalHash.equals(signed.instructionSha256())) {
            throw new C4P6BindingException(
                    C4P6BindingViolation.INSTRUCTION_HASH_MISMATCH,
                    "instructionSha256", null);
        }
        String responseHash = C4CarrierSchema.sha256Hex(receivedResponse0110);
        if (!responseHash.equals(signed.responseSha256())) {
            throw new C4P6BindingException(
                    C4P6BindingViolation.RESPONSE_HASH_MISMATCH, "responseSha256", null);
        }

        Iso8583OriginalInstruction original =
                originalOf(originalHash, persistedOriginal0100);
        Iso8583Message responseMessage = responseOf(receivedResponse0110);
        Iso8583De48Response response = responseExtensionOf(responseMessage);

        // Approved P6 response order: domain, then P4 matrix, then instruction/DE38.
        requireDomain(responseMessage, response);
        requireP4(responseMessage, response);
        requireSignedResultTuple(signed, responseMessage, response);
        requireInstructionBinding(original, signed, responseMessage, response);
        requireSignedInstructionFields(signed, original);
        requireSignedEvidence(signed, response);
        requireSignedAvailability(signed, response);

        return new C4P6BindingDecision(
                signed.evidenceId(),
                signed.authorizationId(),
                signed.transactionId(),
                signed.amountMinor(),
                signed.currency(),
                signed.DE39(),
                signed.result(),
                signed.reason(),
                responseMessage.field(38),
                C4P6BindingDecision.BindingScope
                        .SIGNED_P5_FIELDS_BOUND_TO_PERSISTED_INSTRUCTION_AND_RESPONSE);
    }

    /**
     * Bind one verified late-delivery result. Only the nested cryptographic result is used;
     * the delivery receipt identifier stays a carriage label and grants no authority.
     */
    public static C4P6BindingDecision bindLate(
            C4VerifiedLateDelivery verifiedLate,
            C4EvidenceAttestation signed,
            C4AuthenticatedTransportContext currentContext,
            byte[] persistedOriginal0100,
            byte[] receivedResponse0110) {
        Objects.requireNonNull(verifiedLate, "verifiedLate");
        return bind(
                verifiedLate.attestation(),
                signed,
                currentContext,
                persistedOriginal0100,
                receivedResponse0110);
    }

    // --- Signature-result re-binding ------------------------------------------

    private static void requireSignedContentMatchesVerified(
            C4VerifiedAttestation verified, C4EvidenceAttestation signed) {
        requireVerified(
                "canonicalAttestationSha256",
                verified.canonicalAttestationSha256(),
                signed.canonicalSha256());
        requireVerified("environmentId", verified.environmentId(), signed.environmentId());
        requireVerified("evidenceId", verified.evidenceId(), signed.evidenceId());
        requireVerified(
                "producerPrincipal", verified.producerPrincipal(), signed.producerPrincipal());
        requireVerified(
                "signingPrincipal", verified.signingPrincipal(), signed.signingPrincipal());
        requireVerified("keyId", verified.keyId(), signed.keyId());
        requireVerified("acquirerRef", verified.acquirerRef(), signed.acquirerRef());
        requireVerified(
                "authorizationId", verified.authorizationId(), signed.authorizationId());
        requireVerified("transactionId", verified.transactionId(), signed.transactionId());
        requireVerified(
                "instructionSha256",
                verified.instructionSha256(),
                signed.instructionSha256());
        requireVerified(
                "responseBodySha256",
                verified.responseBodySha256(),
                signed.responseSha256());
    }

    private static void requireVerified(
            String component, String verifiedValue, String signedValue) {
        if (!verifiedValue.equals(signedValue)) {
            throw new C4P6BindingException(
                    C4P6BindingViolation.VERIFIED_BINDING, component, null);
        }
    }

    private static void requireCurrentContext(
            C4VerifiedAttestation verified,
            C4AuthenticatedTransportContext currentContext) {
        if (!currentContext.environmentId().equals(verified.environmentId())) {
            throw new C4P6BindingException(
                    C4P6BindingViolation.ENVIRONMENT_BINDING, "environmentId", null);
        }
        switch (verified.verificationScope()) {
            case DIRECT_ED25519_AND_TRUST_BINDINGS_VERIFIED ->
                    requireSender(
                            currentContext.authenticatedSender(),
                            verified.signingPrincipal(),
                            "signingPrincipal");
            case RELAY_ED25519_AND_TRUST_BINDINGS_VERIFIED ->
                    requireSender(
                            currentContext.authenticatedSender(),
                            verified.relayBinding().principal(),
                            "relayPrincipal");
            case DIRECT_ED25519_AND_TRUST_BINDINGS_VERIFIED_VIA_AUTHORIZED_FORWARD,
                    RELAY_ED25519_AND_TRUST_BINDINGS_VERIFIED_VIA_AUTHORIZED_FORWARD -> {
                // The late-delivery seam already authorised this transport sender as an
                // explicit diagnostic forwarder for the exact scope. The proof identity
                // above is unchanged; no new role or outcome authority is conferred.
            }
        }
    }

    private static void requireSender(
            String currentSender, String verifiedPrincipal, String component) {
        if (!currentSender.equals(verifiedPrincipal)) {
            throw new C4P6BindingException(
                    C4P6BindingViolation.PRINCIPAL_BINDING, component, null);
        }
    }

    // --- Exact persisted/current bytes ---------------------------------------

    private static Iso8583OriginalInstruction originalOf(
            String originalHash, byte[] persistedOriginal0100) {
        Iso8583Message message;
        try {
            message = Iso8583Scanner.scan(ascii(
                    persistedOriginal0100,
                    C4P6BindingViolation.ORIGINAL_INSTRUCTION_INVALID,
                    "original0100"));
        } catch (C4P6BindingException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new C4P6BindingException(
                    C4P6BindingViolation.ORIGINAL_INSTRUCTION_INVALID, "original0100", null);
        }
        if (!"0100".equals(message.mti())) {
            throw new C4P6BindingException(
                    C4P6BindingViolation.ORIGINAL_INSTRUCTION_INVALID, "mti", null);
        }
        Iso8583De48Request extension;
        try {
            extension = Iso8583De48RequestParser.parse(message.field(48));
        } catch (RuntimeException e) {
            throw new C4P6BindingException(
                    C4P6BindingViolation.ORIGINAL_INSTRUCTION_INVALID, "de48", null);
        }
        try {
            Iso8583PhysicalInstruction physical = new Iso8583PhysicalInstruction(
                    message.field(3),
                    Long.parseLong(message.field(4)),
                    message.field(7),
                    message.field(11),
                    message.field(22),
                    message.field(25),
                    message.field(32),
                    message.field(37),
                    message.field(41),
                    message.field(42),
                    message.field(49));
            return new Iso8583OriginalInstruction(
                    originalHash,
                    extension.value("aid"),
                    extension.value("tid"),
                    extension.value("cid"),
                    extension.value("att"),
                    extension.value("acq"),
                    extension.value("mid"),
                    extension.value("term"),
                    extension.value("pay"),
                    Integer.parseInt(extension.value("gen")),
                    extension.value("fixture"),
                    extension.value("cvm"),
                    extension.value("crypto"),
                    extension.value("risk"),
                    extension.value("sent"),
                    extension.value("prior"),
                    physical);
        } catch (RuntimeException e) {
            throw new C4P6BindingException(
                    C4P6BindingViolation.ORIGINAL_INSTRUCTION_INVALID, "projection", null);
        }
    }

    private static Iso8583Message responseOf(byte[] receivedResponse0110) {
        Iso8583Message message;
        try {
            message = Iso8583Scanner.scan(ascii(
                    receivedResponse0110,
                    C4P6BindingViolation.RESPONSE_INVALID,
                    "response0110"));
        } catch (C4P6BindingException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new C4P6BindingException(
                    C4P6BindingViolation.RESPONSE_INVALID, "response0110", null);
        }
        if (!"0110".equals(message.mti())) {
            throw new C4P6BindingException(
                    C4P6BindingViolation.RESPONSE_INVALID, "mti", null);
        }
        return message;
    }

    private static Iso8583De48Response responseExtensionOf(Iso8583Message responseMessage) {
        try {
            return Iso8583De48ResponseParser.parse(responseMessage.field(48));
        } catch (RuntimeException e) {
            throw new C4P6BindingException(
                    C4P6BindingViolation.RESPONSE_INVALID, "de48", null);
        }
    }

    private static String ascii(
            byte[] bytes, C4P6BindingViolation violation, String component) {
        for (byte value : bytes) {
            if (value < 0x20 || value > 0x7e) {
                throw new C4P6BindingException(violation, component, null);
            }
        }
        return new String(bytes, StandardCharsets.US_ASCII);
    }

    // --- Composed P6 phases ---------------------------------------------------

    private static void requireDomain(
            Iso8583Message responseMessage, Iso8583De48Response response) {
        try {
            Iso8583DomainValidator.validateResponse(responseMessage, response);
        } catch (Iso8583DomainException e) {
            throw new C4P6BindingException(
                    C4P6BindingViolation.DOMAIN, e.component(), e.rule().name());
        }
    }

    private static void requireP4(
            Iso8583Message responseMessage, Iso8583De48Response response) {
        try {
            Iso8583P4MatrixValidator.validate(
                    response.value("actor"),
                    response.value("stage"),
                    responseMessage.field(39),
                    response.value("result"),
                    response.value("reason"));
        } catch (Iso8583P4MatrixException e) {
            throw new C4P6BindingException(
                    C4P6BindingViolation.P4_MATRIX, e.component(), e.rule().name());
        }
    }

    private static void requireInstructionBinding(
            Iso8583OriginalInstruction original,
            C4EvidenceAttestation signed,
            Iso8583Message responseMessage,
            Iso8583De48Response response) {
        Iso8583TrustedEvidenceRecord trusted = new Iso8583TrustedEvidenceRecord(
                signed.instructionSha256(), signed.authorizationReference());
        try {
            Iso8583InstructionBindingValidator.validate(
                    original, trusted, responseMessage, response);
        } catch (Iso8583InstructionBindingException e) {
            throw new C4P6BindingException(
                    C4P6BindingViolation.INSTRUCTION_BINDING, e.component(), e.rule().name());
        }
    }

    // --- Signed fields the P6 layers cannot see ------------------------------

    private static void requireSignedResultTuple(
            C4EvidenceAttestation signed,
            Iso8583Message responseMessage,
            Iso8583De48Response response) {
        requireSigned("DE39", signed.DE39(), responseMessage.field(39));
        requireSigned("actor", signed.actor(), response.value("actor"));
        requireSigned("stage", signed.stage(), response.value("stage"));
        requireSigned("result", signed.result(), response.value("result"));
        requireSigned("reason", signed.reason(), response.value("reason"));
    }

    private static void requireSignedInstructionFields(
            C4EvidenceAttestation signed, Iso8583OriginalInstruction original) {
        requireSigned("aid", signed.authorizationId(), original.authorizationId());
        requireSigned("tid", signed.transactionId(), original.transactionId());
        requireSigned("cid", signed.correlationId(), original.correlationId());
        requireSigned("att", signed.instructionAttemptId(), original.instructionAttemptId());
        requireSigned("acq", signed.acquirerRef(), original.acquirerRef());
        requireSigned("mid", signed.merchantRef(), original.merchantRef());
        requireSigned("term", signed.terminalRef(), original.terminalRef());
        requireSigned("pay", signed.paymentRef(), original.paymentRef());
        requireSigned("gen", Integer.toString(signed.generation()),
                Integer.toString(original.generation()));
        requireSigned("de4", Long.toString(signed.amountMinor()),
                Long.toString(original.physical().amountMinor()));
        requireSigned("de49", signed.currency(), original.physical().currency());
        requireSigned("de11", signed.STAN(), original.physical().stan());
        requireSigned("de37", signed.RRN(), original.physical().rrn());
        requireSigned("de7", signed.DE7(), original.physical().de7());
        requireSigned("sent", signed.sent(), original.sent());
    }

    private static void requireSignedEvidence(
            C4EvidenceAttestation signed, Iso8583De48Response response) {
        requireSigned("evidence", signed.evidenceId(), response.value("evidence"));
    }

    /**
     * Bind the signed {@code availableAt} instant to the response {@code at} second
     * projection without redefining the field's meaning. A sub-second value is not silently
     * truncated: it cannot equal a second-precision echo and is rejected.
     */
    private static void requireSignedAvailability(
            C4EvidenceAttestation signed, Iso8583De48Response response) {
        Instant availableAt;
        try {
            availableAt = Instant.parse(signed.availableAt());
        } catch (DateTimeParseException e) {
            throw new C4P6BindingException(
                    C4P6BindingViolation.SIGNED_AVAILABILITY_MISMATCH, "availableAt", null);
        }
        if (availableAt.getNano() != 0
                || !RESPONSE_AT.format(availableAt).equals(response.value("at"))) {
            throw new C4P6BindingException(
                    C4P6BindingViolation.SIGNED_AVAILABILITY_MISMATCH, "availableAt", null);
        }
    }

    private static void requireSigned(
            String component, String signedValue, String otherValue) {
        if (!Objects.equals(signedValue, otherValue)) {
            C4P6BindingViolation violation = switch (component) {
                case "DE39", "actor", "stage", "result", "reason" ->
                        C4P6BindingViolation.SIGNED_RESULT_MISMATCH;
                case "evidence" -> C4P6BindingViolation.SIGNED_EVIDENCE_MISMATCH;
                default -> C4P6BindingViolation.SIGNED_INSTRUCTION_MISMATCH;
            };
            throw new C4P6BindingException(violation, component, null);
        }
    }
}
