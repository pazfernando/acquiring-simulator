package com.acquiring.simulator.protocol.iso8583;

/**
 * Structural failure of the P1 presence matrix.
 *
 * <p>Presence is decided from the MTI and the primary bitmap only: a required
 * data element is absent, or a data element the profile forbids for that MTI is
 * present. DE38 on a 0110 is conditional on trusted issuer evidence, so the
 * scanner never requires or rejects it; that check belongs to the binding
 * layer.</p>
 */
public final class Iso8583PresenceException extends RuntimeException {

    public enum Rule {
        MISSING_REQUIRED_FIELD,
        FORBIDDEN_FIELD_PRESENT
    }

    private final Rule rule;
    private final int dataElement;

    public Iso8583PresenceException(Rule rule, int dataElement, String message) {
        super(message);
        this.rule = rule;
        this.dataElement = dataElement;
    }

    public Rule rule() {
        return rule;
    }

    public int dataElement() {
        return dataElement;
    }
}
