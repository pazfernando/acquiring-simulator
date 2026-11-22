package com.acquiring.simulator.protocol.iso8583;

/**
 * Domain-profile failure of the SIM-ISO87A-v1 amount/currency/profile-code and
 * Gregorian {@code sent}/DE7 checks.
 *
 * <p>Rules describe only pure projection validity. They do not authenticate a
 * producer, resolve a registry fixture or mandate, bind an original instruction
 * or DE38, apply freshness/826 admission, or decide any financial outcome.
 * Messages are fixed diagnostic text and never echo the supplied values.</p>
 */
public final class Iso8583DomainException extends RuntimeException {

    public enum Rule {
        /** A physical field or required extension value was absent. */
        MISSING_FIELD,
        /** A supplied value was present but not in the profile's syntactic form. */
        MALFORMED_FIELD,
        /** DE3, DE22 or DE25 is not an approved SIM-ISO87A-v1 profile code. */
        PROFILE_CODE_UNSUPPORTED,
        /** DE4 minor units are outside the approved 100..999999 range. */
        AMOUNT_OUT_OF_RANGE,
        /** DE49 is not the single approved currency 840 (USD). */
        CURRENCY_UNSUPPORTED,
        /** {@code sent} is not a strict Gregorian UTC timestamp. */
        INVALID_GREGORIAN_DATE,
        /** DE7 is not the month/day/time projection of the original {@code sent}. */
        DE7_MISMATCH
    }

    private final Rule rule;
    private final String component;

    public Iso8583DomainException(Rule rule, String component, String message) {
        super(message);
        this.rule = rule;
        this.component = component;
    }

    public Rule rule() {
        return rule;
    }

    /** Failing component name; never its raw value. */
    public String component() {
        return component;
    }
}
