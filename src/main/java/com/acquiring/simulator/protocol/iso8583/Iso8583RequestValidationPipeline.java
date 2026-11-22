package com.acquiring.simulator.protocol.iso8583;

import java.time.Instant;
import java.util.Objects;

/**
 * One ordered, pure validation pipeline for a SIM-ISO87A-v1 {@code 0100}
 * payment-authorization <em>request</em>.
 *
 * <p>The phases run exactly in the approved P6 order and stop at the first
 * failure:</p>
 *
 * <ol>
 *   <li>{@link Iso8583Scanner}: MTI and primary bitmap, then the P1 presence
 *       matrix, then ascending bitmap-driven field framing/alphabet, then exact
 *       end of body;</li>
 *   <li>{@link Iso8583De48RequestParser}: canonical DE48 {@code key=value}
 *       grammar, order, presence, bounds, alphabet, literals and enums;</li>
 *   <li>{@link Iso8583DomainValidator}: profile codes, DE4 amount, DE49
 *       currency, Gregorian {@code sent}, then DE7 consistency;</li>
 *   <li>{@link Iso8583FreshnessValidator}: the 300-second NEW first-dispatch
 *       window;</li>
 *   <li>{@link Iso8583ExchangeBudgetValidator}: the derived 826-byte NEW
 *       request budget.</li>
 * </ol>
 *
 * <p>Because the phases are sequential, a DE48 schema failure wins over a
 * stale or over-budget request, and a domain failure wins over freshness and
 * budget. The order is not tuned to any individual fixture: it is the approved
 * codec/P6 order, so a multiply-invalid request has one deterministic first
 * failure.</p>
 *
 * <p>The admission mode is a trusted server-side seam, never a wire field. For
 * {@link Iso8583AdmissionMode#AUTHORIZED_HISTORICAL_REPLAY} and
 * {@link Iso8583AdmissionMode#LATE_EVIDENCE}, structural, schema and domain
 * projections are still validated, but freshness and the 826-byte budget are
 * {@code NOT_APPLICABLE}: the persisted original request is authoritative and is
 * not re-encoded, re-admitted or re-dispatched as a new wire claim.</p>
 *
 * <p>This pipeline is not admission, authorization, authentication, registry
 * or mandate resolution, duplicate/conflict handling, claim, dispatch or
 * response construction. A successful envelope means the ordered structural and
 * domain checks and any applicable new-exchange checks passed; it carries no
 * financial or trust verdict.</p>
 */
public final class Iso8583RequestValidationPipeline {

    /** The only MTI accepted by this request pipeline. */
    public static final String REQUEST_MTI = "0100";

    private Iso8583RequestValidationPipeline() {
    }

    /**
     * Run every applicable phase in the approved order and return the first
     * failure, or the validated structural/domain envelope when all applicable
     * phases accept.
     *
     * @param body       complete ASCII request body, exactly as received
     * @param receivedAt trusted server receipt instant; required for
     *                   {@link Iso8583AdmissionMode#NEW_FIRST_DISPATCH}
     * @param mode       trusted server-side mode; never decoded from the wire
     * @return validated structural/domain envelope, not an admission decision
     * @throws Iso8583RequestValidationException on the first ordered failure
     */
    public static Iso8583RequestValidation evaluate(
            String body, Instant receivedAt, Iso8583AdmissionMode mode) {
        Objects.requireNonNull(mode, "admission mode must be supplied by the server");
        if (mode == Iso8583AdmissionMode.NEW_FIRST_DISPATCH) {
            Objects.requireNonNull(receivedAt, "server receipt is required for a new dispatch");
        }

        Iso8583Message message;
        try {
            message = Iso8583Scanner.scan(body);
        } catch (Iso8583HeaderParseException
                 | Iso8583PresenceException
                 | Iso8583FieldException structureFailure) {
            throw structuralFailure(structureFailure);
        }
        if (!REQUEST_MTI.equals(message.mti())) {
            throw new Iso8583RequestValidationException(
                    Iso8583RequestValidationException.Stage.STRUCTURE,
                    "PARSER_MTI",
                    "request pipeline requires MTI " + REQUEST_MTI + ", got " + message.mti(),
                    null);
        }

        Iso8583De48Request extension;
        try {
            extension = Iso8583De48RequestParser.parse(message.field(48));
        } catch (Iso8583De48Exception schemaFailure) {
            throw schemaFailure(schemaFailure);
        }

        Iso8583DomainDecision domain;
        try {
            domain = Iso8583DomainValidator.validateRequest(message, extension);
        } catch (Iso8583DomainException domainFailure) {
            throw domainFailure(domainFailure);
        }

        Iso8583FreshnessDecision freshness =
                Iso8583FreshnessValidator.evaluate(domain.sent(), receivedAt, mode);
        if (freshness == Iso8583FreshnessDecision.STALE) {
            throw new Iso8583RequestValidationException(
                    Iso8583RequestValidationException.Stage.FRESHNESS,
                    freshness.failureRule(),
                    "new first dispatch is outside the approved freshness window",
                    null);
        }

        Iso8583ExchangeBudgetDecision exchangeBudget =
                Iso8583ExchangeBudgetValidator.evaluate(message.field(48).length(), mode);
        if (exchangeBudget == Iso8583ExchangeBudgetDecision.REJECTED) {
            throw new Iso8583RequestValidationException(
                    Iso8583RequestValidationException.Stage.BUDGET,
                    exchangeBudget.failureRule(),
                    "new request exceeds the derived exchange budget",
                    null);
        }

        return new Iso8583RequestValidation(
                mode, message, extension, domain, freshness, exchangeBudget);
    }

    private static Iso8583RequestValidationException structuralFailure(
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
            return new Iso8583RequestValidationException(
                    Iso8583RequestValidationException.Stage.STRUCTURE,
                    label,
                    "header/bitmap rejected the request: " + header.getMessage(),
                    header);
        }
        if (failure instanceof Iso8583PresenceException presence) {
            String label = switch (presence.rule()) {
                case MISSING_REQUIRED_FIELD -> "PARSER_REQUIRED";
                case FORBIDDEN_FIELD_PRESENT -> "PARSER_FORBIDDEN";
            };
            return new Iso8583RequestValidationException(
                    Iso8583RequestValidationException.Stage.STRUCTURE,
                    label,
                    "presence matrix rejected the request: " + presence.getMessage(),
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
        return new Iso8583RequestValidationException(
                Iso8583RequestValidationException.Stage.STRUCTURE,
                label,
                "ascending field validation rejected the request: " + field.getMessage(),
                field);
    }

    private static Iso8583RequestValidationException schemaFailure(
            Iso8583De48Exception failure) {
        String label = switch (failure.rule()) {
            case DE48_TOO_LARGE, VALUE_LENGTH_OUT_OF_RANGE -> "SCHEMA_LENGTH";
            case INVALID_LITERAL, INVALID_ENUM, INVALID_DIGITS, INVALID_NUMBER -> "SCHEMA_ENUM";
            case EMPTY_FIELD, TRAILING_DELIMITER, MALFORMED_FIELD, UNKNOWN_KEY,
                 DUPLICATE_KEY, OUT_OF_ORDER_KEY, MISSING_REQUIRED_KEY,
                 INVALID_VALUE_ALPHABET -> "SCHEMA_CANONICAL";
        };
        return new Iso8583RequestValidationException(
                Iso8583RequestValidationException.Stage.SCHEMA,
                label,
                "canonical DE48 schema rejected the request: " + failure.getMessage(),
                failure);
    }

    private static Iso8583RequestValidationException domainFailure(
            Iso8583DomainException failure) {
        String label = switch (failure.rule()) {
            case MISSING_FIELD, MALFORMED_FIELD -> "DOMAIN_FORMAT";
            case PROFILE_CODE_UNSUPPORTED -> "DOMAIN_PROFILE";
            case AMOUNT_OUT_OF_RANGE -> "DOMAIN_AMOUNT";
            case CURRENCY_UNSUPPORTED -> "DOMAIN_CURRENCY";
            case INVALID_GREGORIAN_DATE -> "DOMAIN_DATE";
            case DE7_MISMATCH -> "DOMAIN_DE7";
        };
        return new Iso8583RequestValidationException(
                Iso8583RequestValidationException.Stage.DOMAIN,
                label,
                "domain validation rejected the request: " + failure.getMessage(),
                failure);
    }
}
