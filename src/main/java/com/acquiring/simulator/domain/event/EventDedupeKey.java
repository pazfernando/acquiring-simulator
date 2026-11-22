package com.acquiring.simulator.domain.event;

/**
 * Environment-bound dedupe identity (observation contract O1a). It is stable across restarts and
 * ignores the boot instance; the environment prefix keeps two environments from colliding.
 */
public record EventDedupeKey(String environment, String producerId, String eventId) {

    public EventDedupeKey {
        if (environment == null || environment.isBlank()) {
            throw new EventValidationException(EventViolation.MISSING_REQUIRED, "environment", null);
        }
        if (producerId == null || producerId.isBlank() || eventId == null || eventId.isBlank()) {
            throw new EventValidationException(EventViolation.MISSING_REQUIRED, "dedupeKey",
                    "producerId and eventId are required");
        }
        EventSanitizer.requireSafe("environment", environment);
        EventSanitizer.requireSafe("producerId", producerId);
        EventSanitizer.requireSafe("eventId", eventId);
    }

    public static EventDedupeKey of(EventContext context, CanonicalEvent event) {
        return new EventDedupeKey(context.environment(), event.producerId(), event.eventId());
    }
}
