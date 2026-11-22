package com.acquiring.simulator.channel;

/**
 * Sanitized C4 carrier rejection. The message names the first violated rule and, when
 * useful, the field; it never contains the header value, decoded JSON, body bytes or
 * signature material, so it is safe to record without disabling the no-log rule.
 */
public final class C4CarrierException extends IllegalArgumentException {

    private final C4CarrierViolation violation;
    private final String field;

    private C4CarrierException(C4CarrierViolation violation, String field) {
        super(violation.name() + (field == null ? "" : " field=" + field));
        this.violation = violation;
        this.field = field;
    }

    public static C4CarrierException of(C4CarrierViolation violation) {
        return new C4CarrierException(violation, null);
    }

    public static C4CarrierException of(C4CarrierViolation violation, String field) {
        return new C4CarrierException(violation, field);
    }

    public C4CarrierViolation violation() {
        return violation;
    }

    /** Declared field name, or {@code null} when the rule is exchange-wide. */
    public String field() {
        return field;
    }
}
