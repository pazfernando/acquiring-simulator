package com.acquiring.simulator.channel;

/**
 * Sanitized C4 verification rejection. The message names the first failed rule and, when
 * useful, the field; it never contains the signature, public key, body bytes, attestation
 * values or caller identity.
 */
public final class C4VerificationException extends IllegalArgumentException {

    private final C4VerificationViolation violation;
    private final String field;

    private C4VerificationException(C4VerificationViolation violation, String field) {
        super(violation.name() + (field == null ? "" : " field=" + field));
        this.violation = violation;
        this.field = field;
    }

    public static C4VerificationException of(C4VerificationViolation violation) {
        return new C4VerificationException(violation, null);
    }

    public static C4VerificationException of(
            C4VerificationViolation violation, String field) {
        return new C4VerificationException(violation, field);
    }

    public C4VerificationViolation violation() {
        return violation;
    }

    /** Declared field name, or {@code null} when the rule is exchange-wide. */
    public String field() {
        return field;
    }
}
