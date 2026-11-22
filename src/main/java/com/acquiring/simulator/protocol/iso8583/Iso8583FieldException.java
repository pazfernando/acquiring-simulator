package com.acquiring.simulator.protocol.iso8583;

/**
 * Structural failure while reading a SIM-ISO87A-v1 field.
 *
 * <p>The rule codes describe field syntax only: length framing, alphabet and
 * body bounds. They do not evaluate field presence, the canonical DE48
 * key=value schema, amounts, dates, currencies, fixtures or any business rule.</p>
 */
public final class Iso8583FieldException extends RuntimeException {

    public enum Rule {
        START_OUT_OF_BOUNDS,
        BODY_TOO_SHORT,
        INVALID_LENGTH_PREFIX,
        LENGTH_OUT_OF_RANGE,
        INVALID_NUMERIC,
        INVALID_UPPER_ALNUM,
        NON_PRINTABLE_ASCII,
        TRAILING_BYTES
    }

    private final Rule rule;

    public Iso8583FieldException(Rule rule, String message) {
        super(message);
        this.rule = rule;
    }

    public Rule rule() {
        return rule;
    }
}
