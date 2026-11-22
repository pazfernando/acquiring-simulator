package com.acquiring.simulator.protocol.iso8583;

import java.util.Objects;

/**
 * One ordered, pure validation pipeline for a SIM-ISO87A-v1 {@code 0110}
 * payment-authorization <em>response</em>.
 *
 * <p>The phases run exactly in the approved P6 order and stop at the first
 * failure:</p>
 *
 * <ol>
 *   <li>{@link Iso8583Scanner}: MTI and primary bitmap, then the presence
 *       matrix, then ascending bitmap-driven field framing/alphabet, then exact
 *       end of body;</li>
 *   <li>{@link Iso8583De48ResponseParser}: canonical DE48 response schema;</li>
 *   <li>{@link Iso8583DomainValidator}: profile codes, DE4 amount, DE49
 *       currency, Gregorian echoed {@code sent}, then DE7 consistency;</li>
 *   <li>{@link Iso8583P4MatrixValidator}: actor/stage/DE39/result/reason;</li>
 *   <li>{@link Iso8583InstructionBindingValidator}: trusted original-wire hash,
 *       complete instruction/context echo, then exact DE38
 *       authorization-reference presence and value.</li>
 * </ol>
 *
 * <p>Because the phases are sequential, a schema failure wins over a domain,
 * P4 or binding failure; a domain failure wins over P4 and binding; and a P4
 * failure wins over binding. The order is not tuned to any fixture: it is the
 * approved P6 response order, so a multiply-invalid response has one
 * deterministic first failure.</p>
 *
 * <p><strong>Trusted-record precondition.</strong> Authentication and
 * attestation verification are <em>not</em> implemented here. The caller must
 * supply the already-verified persisted original instruction and the
 * already-verified trusted-evidence projection from the internal trusted seam.
 * A missing record fails closed at {@link
 * Iso8583ResponseValidationException.Stage#TRUST}; a malformed trusted record
 * fails closed the same way. The pipeline never hashes the received response,
 * never re-encodes the original, and never substitutes one hash for another.</p>
 *
 * <p>This pipeline is not authentication, attestation, evidence resolution,
 * duplicate/business-conflict admission, claim, storage receipt, response
 * construction, C4 carriage, HTTP or dispatch. It applies no freshness window
 * or 826-byte request budget to a response, and reading a historical
 * instruction never fabricates a {@code 0110} for a timeout. A successful
 * envelope carries no financial or trust verdict.</p>
 */
public final class Iso8583ResponseValidationPipeline {

    /** The only MTI accepted by this response pipeline. */
    public static final String RESPONSE_MTI = "0110";

    private Iso8583ResponseValidationPipeline() {
    }

    /**
     * Run every applicable phase in the approved order and return the first
     * failure, or the validated structural/domain envelope when all phases
     * accept.
     *
     * @param body     complete ASCII response body, exactly as received
     * @param original already-verified persisted original instruction
     * @param trusted  already-verified trusted-evidence projection
     * @return validated structural/domain envelope, not an admission decision
     * @throws Iso8583ResponseValidationException on the first ordered failure
     */
    public static Iso8583ResponseValidation evaluate(
            String body,
            Iso8583OriginalInstruction original,
            Iso8583TrustedEvidenceRecord trusted) {
        requireTrustedRecord(original, "originalInstruction");
        requireTrustedRecord(trusted, "trustedRecord");

        Iso8583Message message;
        try {
            message = Iso8583Scanner.scan(body);
        } catch (Iso8583HeaderParseException
                 | Iso8583PresenceException
                 | Iso8583FieldException structureFailure) {
            throw structuralFailure(structureFailure);
        }
        if (!RESPONSE_MTI.equals(message.mti())) {
            throw new Iso8583ResponseValidationException(
                    Iso8583ResponseValidationException.Stage.STRUCTURE,
                    "PARSER_MTI",
                    "response pipeline requires MTI " + RESPONSE_MTI + ", got " + message.mti(),
                    null);
        }

        Iso8583De48Response extension;
        try {
            extension = Iso8583De48ResponseParser.parse(message.field(48));
        } catch (Iso8583De48Exception schemaFailure) {
            throw schemaFailure(schemaFailure);
        }

        Iso8583DomainDecision domain;
        try {
            domain = Iso8583DomainValidator.validateResponse(message, extension);
        } catch (Iso8583DomainException domainFailure) {
            throw domainFailure(domainFailure);
        }

        Iso8583P4MatrixDecision p4;
        try {
            p4 = Iso8583P4MatrixValidator.validate(
                    extension.value("actor"),
                    extension.value("stage"),
                    message.field(39),
                    extension.value("result"),
                    extension.value("reason"));
        } catch (Iso8583P4MatrixException p4Failure) {
            throw new Iso8583ResponseValidationException(
                    Iso8583ResponseValidationException.Stage.P4,
                    "EVIDENCE_MATRIX",
                    "P4 matrix rejected the claimed response tuple: " + p4Failure.getMessage(),
                    p4Failure);
        }

        Iso8583InstructionBindingDecision binding;
        try {
            binding = Iso8583InstructionBindingValidator.validate(
                    original, trusted, message, extension);
        } catch (Iso8583InstructionBindingException bindingFailure) {
            throw bindingFailure(bindingFailure);
        }

        return new Iso8583ResponseValidation(message, extension, domain, p4, binding);
    }

    private static void requireTrustedRecord(Object value, String component) {
        if (value == null) {
            throw new Iso8583ResponseValidationException(
                    Iso8583ResponseValidationException.Stage.TRUST,
                    "TRUSTED_RECORD_REQUIRED",
                    "already-verified " + component + " is a required precondition",
                    null);
        }
    }

    private static Iso8583ResponseValidationException structuralFailure(
            RuntimeException failure) {
        if (failure instanceof Iso8583HeaderParseException header) {
            String label = switch (header.rule()) {
                case BODY_TOO_SHORT -> "PARSER_TRUNCATED";
                case NON_ASCII -> "PARSER_ASCII";
                case INVALID_MTI, UNSUPPORTED_MTI -> "PARSER_MTI";
                case INVALID_BITMAP -> "PARSER_BITMAP";
                case SECONDARY_BITMAP_FORBIDDEN -> "PARSER_SECONDARY";
                case UNSUPPORTED_FIELD -> "PARSER_FORBIDDEN";
            };
            return new Iso8583ResponseValidationException(
                    Iso8583ResponseValidationException.Stage.STRUCTURE,
                    label,
                    "header/bitmap rejected the response: " + header.getMessage(),
                    header);
        }
        if (failure instanceof Iso8583PresenceException presence) {
            String label = switch (presence.rule()) {
                case MISSING_REQUIRED_FIELD -> "PARSER_REQUIRED";
                case FORBIDDEN_FIELD_PRESENT -> "PARSER_FORBIDDEN";
            };
            return new Iso8583ResponseValidationException(
                    Iso8583ResponseValidationException.Stage.STRUCTURE,
                    label,
                    "presence matrix rejected the response: " + presence.getMessage(),
                    presence);
        }
        Iso8583FieldException field = (Iso8583FieldException) failure;
        String label = switch (field.rule()) {
            case START_OUT_OF_BOUNDS, BODY_TOO_SHORT -> "PARSER_TRUNCATED";
            case INVALID_LENGTH_PREFIX, LENGTH_OUT_OF_RANGE -> "PARSER_LENGTH";
            case INVALID_NUMERIC, INVALID_UPPER_ALNUM -> "PARSER_ALPHABET";
            case NON_PRINTABLE_ASCII -> "PARSER_ASCII";
            case TRAILING_BYTES -> "PARSER_END_BODY";
        };
        return new Iso8583ResponseValidationException(
                Iso8583ResponseValidationException.Stage.STRUCTURE,
                label,
                "ascending field validation rejected the response: " + field.getMessage(),
                field);
    }

    private static Iso8583ResponseValidationException schemaFailure(
            Iso8583De48Exception failure) {
        String label = switch (failure.rule()) {
            case DE48_TOO_LARGE, VALUE_LENGTH_OUT_OF_RANGE -> "SCHEMA_LENGTH";
            case INVALID_LITERAL, INVALID_ENUM, INVALID_DIGITS, INVALID_NUMBER -> "SCHEMA_ENUM";
            case EMPTY_FIELD, TRAILING_DELIMITER, MALFORMED_FIELD, UNKNOWN_KEY,
                 DUPLICATE_KEY, OUT_OF_ORDER_KEY, MISSING_REQUIRED_KEY,
                 INVALID_VALUE_ALPHABET -> "SCHEMA_CANONICAL";
        };
        return new Iso8583ResponseValidationException(
                Iso8583ResponseValidationException.Stage.SCHEMA,
                label,
                "canonical DE48 schema rejected the response: " + failure.getMessage(),
                failure);
    }

    private static Iso8583ResponseValidationException domainFailure(
            Iso8583DomainException failure) {
        String label = switch (failure.rule()) {
            case MISSING_FIELD, MALFORMED_FIELD -> "DOMAIN_FORMAT";
            case PROFILE_CODE_UNSUPPORTED -> "DOMAIN_PROFILE";
            case AMOUNT_OUT_OF_RANGE -> "DOMAIN_AMOUNT";
            case CURRENCY_UNSUPPORTED -> "DOMAIN_CURRENCY";
            case INVALID_GREGORIAN_DATE -> "DOMAIN_DATE";
            case DE7_MISMATCH -> "DOMAIN_DE7";
        };
        return new Iso8583ResponseValidationException(
                Iso8583ResponseValidationException.Stage.DOMAIN,
                label,
                "domain validation rejected the response: " + failure.getMessage(),
                failure);
    }

    private static Iso8583ResponseValidationException bindingFailure(
            Iso8583InstructionBindingException failure) {
        return switch (failure.rule()) {
            case INSTRUCTION_HASH_MISMATCH, REQUIRED_ECHO_MISSING,
                 INSTRUCTION_FIELD_MISMATCH ->
                    new Iso8583ResponseValidationException(
                            Iso8583ResponseValidationException.Stage.INSTRUCTION,
                            "BINDING_INSTRUCTION",
                            "instruction binding rejected the response: "
                                    + failure.getMessage(),
                            failure);
            case AUTHORIZATION_REFERENCE_MISSING, AUTHORIZATION_REFERENCE_UNSOLICITED,
                 AUTHORIZATION_REFERENCE_MISMATCH ->
                    new Iso8583ResponseValidationException(
                            Iso8583ResponseValidationException.Stage.AUTH_REFERENCE,
                            "BINDING_AUTH_REFERENCE",
                            "authorization-reference binding rejected the response: "
                                    + failure.getMessage(),
                            failure);
            case MISSING_TRUSTED_RECORD, MALFORMED_TRUSTED_RECORD ->
                    new Iso8583ResponseValidationException(
                            Iso8583ResponseValidationException.Stage.TRUST,
                            "TRUSTED_RECORD_INVALID",
                            "trusted-record seam rejected the response: "
                                    + failure.getMessage(),
                            failure);
        };
    }
}
