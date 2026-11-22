package com.acquiring.simulator.protocol.iso8583;

/**
 * First deterministic failure of the ordered request-validation pipeline.
 *
 * <p>The stage names the layer that rejected the request; the failure label is
 * the stable first-failure oracle value used by the approved packed vectors
 * ({@code PARSER_*}, {@code SCHEMA_*}, {@code DOMAIN_*},
 * {@code ADMISSION_FRESHNESS}, {@code ADMISSION_RESPONSE_CAPACITY}). This type
 * never represents admission, authorization, trust or a financial outcome.</p>
 */
public final class Iso8583RequestValidationException extends RuntimeException {

    /** Ordered pipeline layer that produced the first failure. */
    public enum Stage {
        /** MTI, bitmap, presence, ascending field framing or exact end of body. */
        STRUCTURE,
        /** Canonical DE48 {@code key=value} schema. */
        SCHEMA,
        /** Amount, currency, profile codes, Gregorian {@code sent} and DE7. */
        DOMAIN,
        /** NEW first-dispatch 300-second freshness window. */
        FRESHNESS,
        /** NEW first-dispatch 826-byte exchange budget. */
        BUDGET
    }

    private final Stage stage;
    private final String failureLabel;

    public Iso8583RequestValidationException(
            Stage stage, String failureLabel, String message, Throwable cause) {
        super(message, cause);
        this.stage = stage;
        this.failureLabel = failureLabel;
    }

    public Stage stage() {
        return stage;
    }

    /** Stable first-failure label, never a financial or admission verdict. */
    public String failureLabel() {
        return failureLabel;
    }
}
