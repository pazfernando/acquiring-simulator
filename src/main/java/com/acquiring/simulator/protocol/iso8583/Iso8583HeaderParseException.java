package com.acquiring.simulator.protocol.iso8583;

/**
 * Structural failure while parsing the ISO 8583 MTI and primary bitmap.
 *
 * <p>The rule codes mirror the profile's codec ordering (P1/P6). They describe only
 * header syntax; no field presence, alphabet, amount, date or business rule is
 * evaluated by this parser.</p>
 */
public final class Iso8583HeaderParseException extends RuntimeException {

    public enum Rule {
        BODY_TOO_SHORT,
        NON_ASCII,
        INVALID_MTI,
        UNSUPPORTED_MTI,
        INVALID_BITMAP,
        SECONDARY_BITMAP_FORBIDDEN,
        UNSUPPORTED_FIELD
    }

    private final Rule rule;

    public Iso8583HeaderParseException(Rule rule, String message) {
        super(message);
        this.rule = rule;
    }

    public Rule rule() {
        return rule;
    }
}
