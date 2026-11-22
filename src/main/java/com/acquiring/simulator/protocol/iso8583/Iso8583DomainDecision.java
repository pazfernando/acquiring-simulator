package com.acquiring.simulator.protocol.iso8583;

/**
 * Successful domain-profile projection of one scanned SIM-ISO87A-v1 message.
 *
 * <p>Carries only the values the domain phase validated: DE4 minor units, DE49
 * currency and the {@code sent}/DE7 UTC projection. It is not admission, trust
 * or a financial outcome; no registry, mandate, fingerprint, evidence or
 * duplicate state is represented.</p>
 *
 * @param amountMinor approved DE4 minor-unit amount, 100..999999 inclusive
 * @param currency    approved DE49 currency, always {@code 840}
 * @param sent        original request UTC timestamp, {@code YYYYMMDDhhmmss}
 * @param de7         DE7 month/day/time projection of {@code sent}
 */
public record Iso8583DomainDecision(
        long amountMinor, String currency, String sent, String de7) {

    public Iso8583DomainDecision {
        if (amountMinor < Iso8583DomainValidator.MIN_AMOUNT_MINOR
                || amountMinor > Iso8583DomainValidator.MAX_AMOUNT_MINOR) {
            throw new IllegalArgumentException("amountMinor is outside the approved range");
        }
        if (currency == null || sent == null || de7 == null) {
            throw new IllegalArgumentException("currency, sent and de7 are required");
        }
    }
}
