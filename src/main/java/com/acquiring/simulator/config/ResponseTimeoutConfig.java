package com.acquiring.simulator.config;

import java.time.Duration;
import java.util.Locale;

/**
 * Approved authorization response-timeout default and the single seam that resolves the
 * AWS cold-start environment override.
 *
 * <p>The approved simulator policy (AUTH-001 R20, HD-02) is a response deadline of
 * <strong>30 seconds from dispatch</strong> by default. The same value is declared for the
 * Spring path as {@code simulator.authorization.response-timeout=PT30S}. The AWS cold-start
 * wire therefore resolves its port from this class rather than from an inline literal.</p>
 *
 * <p>Resolution rules:</p>
 * <ul>
 *   <li>{@code null} (variable not set) resolves to {@link #APPROVED_DEFAULT_RESPONSE_TIMEOUT}.</li>
 *   <li>A positive whole number of milliseconds is preserved as an explicit owner override.</li>
 *   <li>Anything else fails closed at cold start: blank, non-numeric, negative, zero or
 *       overflowing values raise {@link IllegalStateException}. No fallback value is invented,
 *       and the failure never becomes a decline because no authorization is evaluated.</li>
 * </ul>
 *
 * <p>This class holds configuration only. It does not change evidence ordering, the deadline
 * equality rule, transport-timeout to outcome mapping, or business state.</p>
 */
public final class ResponseTimeoutConfig {

    /** Environment variable that carries the explicit millisecond override. */
    public static final String RESPONSE_TIMEOUT_MILLIS_VARIABLE = "RESPONSE_TIMEOUT_MS";

    /** Approved AUTH-001 R20 simulator default: 30 seconds from dispatch. */
    public static final Duration APPROVED_DEFAULT_RESPONSE_TIMEOUT = Duration.ofSeconds(30);

    private ResponseTimeoutConfig() {}

    /**
     * Resolves the effective response timeout from a raw environment value.
     *
     * @param rawMillis raw {@value #RESPONSE_TIMEOUT_MILLIS_VARIABLE} value, or {@code null}
     *     when the variable is absent
     * @return the approved default when absent, otherwise the explicit override
     * @throws IllegalStateException when the value is present but blank, non-numeric,
     *     non-positive or outside the {@code long} millisecond range
     */
    public static Duration resolve(String rawMillis) {
        if (rawMillis == null) {
            return APPROVED_DEFAULT_RESPONSE_TIMEOUT;
        }
        String trimmed = rawMillis.trim();
        if (trimmed.isEmpty()) {
            throw invalid(rawMillis, "is blank");
        }
        long millis;
        try {
            millis = Long.parseLong(trimmed);
        } catch (NumberFormatException notANumber) {
            var failure = new IllegalStateException(RESPONSE_TIMEOUT_MILLIS_VARIABLE
                    + " must be a whole number of milliseconds but was '" + rawMillis + "'",
                    notANumber);
            throw failure;
        }
        if (millis <= 0L) {
            throw invalid(rawMillis, "must be positive");
        }
        return Duration.ofMillis(millis);
    }

    private static IllegalStateException invalid(String rawMillis, String problem) {
        return new IllegalStateException(String.format(Locale.ROOT,
                "%s %s; observed '%s'", RESPONSE_TIMEOUT_MILLIS_VARIABLE, problem, rawMillis));
    }
}
