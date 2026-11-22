package com.acquiring.simulator.protocol.iso8583;

/**
 * Structural failure of the canonical DE48 key=value schema, shared by the
 * request and response extensions.
 *
 * <p>Rules cover only the P2 canonical grammar: separators, exactly one
 * {@code =} per pair, known/unique/ordered keys, per-key byte bounds, value
 * alphabet, literals, enums, the integer form of {@code gen} and fourteen-digit
 * {@code sent}/{@code at}. They do not validate amounts, currencies, calendar
 * dates, DE7 consistency, the P4 actor/stage/result matrix, DE38 binding,
 * freshness, the 826-byte exchange budget, trusted evidence, dispatch or
 * admission.</p>
 */
public final class Iso8583De48Exception extends RuntimeException {

    public enum Rule {
        EMPTY_FIELD,
        TRAILING_DELIMITER,
        MALFORMED_FIELD,
        UNKNOWN_KEY,
        DUPLICATE_KEY,
        OUT_OF_ORDER_KEY,
        MISSING_REQUIRED_KEY,
        DE48_TOO_LARGE,
        VALUE_LENGTH_OUT_OF_RANGE,
        INVALID_VALUE_ALPHABET,
        INVALID_LITERAL,
        INVALID_ENUM,
        INVALID_DIGITS,
        INVALID_NUMBER
    }

    private final Rule rule;
    private final String key;

    public Iso8583De48Exception(Rule rule, String key, String message) {
        super(message);
        this.rule = rule;
        this.key = key;
    }

    public Rule rule() {
        return rule;
    }

    /** Offending key, or the empty string when the failure is not key-specific. */
    public String key() {
        return key;
    }
}
