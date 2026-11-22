package com.acquiring.simulator.protocol.iso8583;

/**
 * Structural failure while packing one SIM-ISO87A-v1 body.
 *
 * <p>The rule codes describe encoding constraints only: MTI/bitmap header,
 * enabled-field membership, the P1 presence matrix and per-field width,
 * alphabet or length framing. They do not evaluate the canonical DE48 key=value
 * schema, DE38 trusted evidence, amounts, dates, currencies, fixtures,
 * freshness, admission, trust or any financial rule.</p>
 */
public final class Iso8583PackException extends RuntimeException {

    public enum Rule {
        UNSUPPORTED_MTI,
        UNKNOWN_FIELD,
        MISSING_REQUIRED_FIELD,
        FORBIDDEN_FIELD_PRESENT,
        MISSING_VALUE,
        INVALID_WIDTH,
        INVALID_NUMERIC,
        INVALID_UPPER_ALNUM,
        NON_PRINTABLE_ASCII,
        LENGTH_OUT_OF_RANGE
    }

    private final Rule rule;
    private final int dataElement;

    public Iso8583PackException(Rule rule, int dataElement, String message) {
        super(message);
        this.rule = rule;
        this.dataElement = dataElement;
    }

    public Rule rule() {
        return rule;
    }

    /**
     * @return the failing data element, or {@code -1} when the failure concerns
     *         the MTI or the supplied map rather than one field
     */
    public int dataElement() {
        return dataElement;
    }
}
