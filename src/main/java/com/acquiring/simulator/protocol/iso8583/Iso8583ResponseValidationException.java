package com.acquiring.simulator.protocol.iso8583;

/**
 * First deterministic failure of the ordered response-validation pipeline.
 *
 * <p>The stage names the layer that rejected the response; the failure label is
 * the stable first-failure oracle value used by the approved packed vectors
 * ({@code PARSER_*}, {@code SCHEMA_*}, {@code DOMAIN_*},
 * {@code EVIDENCE_MATRIX}, {@code BINDING_INSTRUCTION},
 * {@code BINDING_AUTH_REFERENCE}). {@link Stage#TRUST} is not a P6 codec phase:
 * it reports that the already-verified trusted-record seam was absent or
 * malformed before binding could run.</p>
 *
 * <p>This type never represents authentication, attestation verification,
 * business conflict, admission or a financial outcome.</p>
 */
public final class Iso8583ResponseValidationException extends RuntimeException {

    /** Ordered pipeline layer that produced the first failure. */
    public enum Stage {
        /**
         * Trusted-record precondition only. Authentication and attestation are
         * treated as already verified upstream and are not implemented here.
         */
        TRUST,
        /** MTI, bitmap, presence, ascending field framing or exact end of body. */
        STRUCTURE,
        /** Canonical DE48 {@code key=value} response schema. */
        SCHEMA,
        /** Amount, currency, profile codes, Gregorian {@code sent} and DE7. */
        DOMAIN,
        /** P4 actor/stage/DE39/result/reason matrix. */
        P4,
        /** Original-instruction hash and complete echo equality. */
        INSTRUCTION,
        /** Exact DE38 authorization-reference presence and value. */
        AUTH_REFERENCE
    }

    private final Stage stage;
    private final String failureLabel;

    public Iso8583ResponseValidationException(
            Stage stage, String failureLabel, String message, Throwable cause) {
        super(message, cause);
        this.stage = stage;
        this.failureLabel = failureLabel;
    }

    public Stage stage() {
        return stage;
    }

    /** Stable first-failure label, never a trust or admission verdict. */
    public String failureLabel() {
        return failureLabel;
    }
}
