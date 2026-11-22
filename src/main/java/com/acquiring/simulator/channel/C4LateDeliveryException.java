package com.acquiring.simulator.channel;

/**
 * Sanitized C4 late-delivery rejection.
 *
 * <p>The message names the first violated rule and, when useful, the field. It never
 * contains the raw payload, decoded JSON, body bytes, base64, nested envelope, signature,
 * delivery receipt or caller identity, so it is safe to record under the no-log rule.</p>
 */
public final class C4LateDeliveryException extends IllegalArgumentException {

    private final C4LateDeliveryViolation violation;
    private final String field;

    private C4LateDeliveryException(C4LateDeliveryViolation violation, String field) {
        super(violation.name() + (field == null ? "" : " field=" + field));
        this.violation = violation;
        this.field = field;
    }

    public static C4LateDeliveryException of(C4LateDeliveryViolation violation) {
        return new C4LateDeliveryException(violation, null);
    }

    public static C4LateDeliveryException of(
            C4LateDeliveryViolation violation, String field) {
        return new C4LateDeliveryException(violation, field);
    }

    public C4LateDeliveryViolation violation() {
        return violation;
    }

    /** Declared field name, or {@code null} when the rule is payload-wide. */
    public String field() {
        return field;
    }
}
