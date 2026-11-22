package com.acquiring.simulator.domain.event;

/**
 * Rejection of an event payload or wrapper. The message names the field path and violation code
 * only; values are never repeated so a rejected JWT/PAN cannot leak through the error.
 */
public final class EventValidationException extends IllegalArgumentException {

    private final EventViolation violation;
    private final String field;

    public EventValidationException(EventViolation violation, String field, String message) {
        super(message == null || message.isBlank()
                ? violation.name() + " at " + field
                : violation.name() + " at " + field + ": " + message);
        this.violation = violation;
        this.field = field;
    }

    public EventViolation violation() {
        return violation;
    }

    public String field() {
        return field;
    }
}
