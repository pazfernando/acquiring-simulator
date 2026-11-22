package com.acquiring.simulator.domain.event;

/**
 * Environment-bound business context of one functional-event partition (observation contract O2a).
 *
 * <p>The collector fixes this context from the authenticated request; a producer cannot silently
 * reassign it. The event partition key is derived from exactly these four references.
 */
public record EventContext(
        String environment,
        String acquirerRef,
        String merchantRef,
        String transactionId) {

    public EventContext {
        environment(environment);
        reference("acquirerRef", acquirerRef, 128);
        reference("merchantRef", merchantRef, 128);
        reference("transactionId", transactionId, 64);
    }

    private static void environment(String value) {
        if (value == null || value.isBlank() || value.length() > 32) {
            throw new EventValidationException(EventViolation.MISSING_REQUIRED, "environment",
                    "1-32 characters required");
        }
        EventSanitizer.requireSafe("environment", value);
    }

    private static void reference(String field, String value, int max) {
        if (value == null || value.isBlank()) {
            throw new EventValidationException(EventViolation.MISSING_REQUIRED, field,
                    "1-" + max + " characters required");
        }
        if (value.length() > max) {
            throw new EventValidationException(EventViolation.OUT_OF_BOUNDS, field,
                    "maximum " + max + " characters");
        }
        EventSanitizer.requireSafe(field, value);
    }
}
