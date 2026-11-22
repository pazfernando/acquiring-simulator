package com.acquiring.simulator.channel;

import java.util.Objects;

/**
 * Sanitized rejection from the pure signed-P5/P6 field-binding step.
 *
 * <p>{@link #violation()} is one fixed category, {@link #component()} names at most one
 * field or phase, and {@link #sourceRule()} optionally names the fixed rule constant of the
 * composed lower layer (domain, P4 matrix or instruction binding). None of them contains a
 * received value, a payload, a key or a signature.</p>
 */
public final class C4P6BindingException extends RuntimeException {

    private final C4P6BindingViolation violation;
    private final String component;
    private final String sourceRule;

    public C4P6BindingException(
            C4P6BindingViolation violation, String component, String sourceRule) {
        super(message(violation, component, sourceRule));
        this.violation = Objects.requireNonNull(violation, "violation");
        this.component = component;
        this.sourceRule = sourceRule;
    }

    public C4P6BindingViolation violation() {
        return violation;
    }

    /** Failing field or phase name; never its raw value. */
    public String component() {
        return component;
    }

    /** Fixed lower-layer rule constant, or {@code null} for a binder-local rule. */
    public String sourceRule() {
        return sourceRule;
    }

    private static String message(
            C4P6BindingViolation violation, String component, String sourceRule) {
        StringBuilder text = new StringBuilder("signed P5/P6 binding rejected: ")
                .append(violation.name());
        if (component != null) {
            text.append(" at ").append(component);
        }
        if (sourceRule != null) {
            text.append(" (").append(sourceRule).append(')');
        }
        return text.toString();
    }
}
