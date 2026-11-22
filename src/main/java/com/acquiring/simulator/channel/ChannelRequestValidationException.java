package com.acquiring.simulator.channel;

/**
 * First deterministic failure of one C1 channel input validation.
 *
 * <p>The {@link ChannelErrorCode} is the safe external C3 code; the rule is the
 * stable internal first-failure label the approved channel vectors use
 * ({@code CHANNEL_SCHEMA_AMOUNT}, {@code CHANNEL_TYPE_AMOUNT},
 * {@code DOMAIN_AMOUNT}, {@code UNKNOWN_FIELD}, ...). This type never
 * represents admission, authentication, authorization or a financial outcome,
 * and it never carries a caller value, raw payload or issuer detail.</p>
 */
public final class ChannelRequestValidationException extends RuntimeException {

    private final ChannelErrorCode errorCode;
    private final String rule;
    private final String field;

    public ChannelRequestValidationException(
            ChannelErrorCode errorCode, String rule, String field) {
        super(message(errorCode, rule, field));
        this.errorCode = errorCode;
        this.rule = rule;
        this.field = field;
    }

    /** Safe external error code; never an outcome or decision. */
    public ChannelErrorCode errorCode() {
        return errorCode;
    }

    /** Stable first-failure label used by the approved channel vectors. */
    public String rule() {
        return rule;
    }

    /**
     * Declared field name the failure refers to, or {@code null} when naming it
     * would require echoing caller data (for example an unknown key).
     */
    public String field() {
        return field;
    }

    /**
     * Deterministic safe message. It contains only the fixed error code, the
     * fixed rule label and, for declared fields, the product-local field name.
     */
    private static String message(ChannelErrorCode errorCode, String rule, String field) {
        return field == null
                ? "channel request rejected: " + errorCode + "/" + rule
                : "channel request rejected: " + errorCode + "/" + rule + " (" + field + ")";
    }
}
