package com.acquiring.simulator.channel;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Immutable, closed C3 channel response envelope.
 *
 * <p>The approved channel contract C3 defines exactly THIRTEEN keys, no more:
 * {@code contractVersion, authorizationId, transactionId, attemptId, correlationId,
 * originalCorrelationId, outcome, disposition, businessVersion, asOf, observation,
 * serverFreshness, error}. This type is that closed set; there is no free-form map, no
 * extension field and no response authentication flag. Raw ISO bytes, attestation,
 * signatures and issuer-private values are not representable here.</p>
 *
 * <p>Validation enforces the contract's shape rather than any one branch's values:
 * identifiers are null or inside the profile reference alphabet/bounds; {@code outcome}
 * is null exactly when {@code disposition} is one of {@code REJECTED},
 * {@code NOT_DISPATCHED} or {@code UNAVAILABLE}; {@code businessVersion} is null or a
 * positive committed integer; {@code asOf} is required; enum values and the error
 * allowlist are closed; and the canonical JSON form never exceeds 4096 UTF-8 bytes.
 * Which error belongs to which branch, and which identifiers a branch may expose, is
 * decided by {@link C3ResponseBuilder} from trusted internal facts.</p>
 *
 * <p>{@link #toCanonicalJson()} emits the exact compact, alphabetically key-sorted form
 * used by the approved {@code response-vectors.json} oracles. It is deterministic for a
 * given envelope and performs no clock read.</p>
 */
public record C3ResponseEnvelope(
        String contractVersion,
        String authorizationId,
        String transactionId,
        String attemptId,
        String correlationId,
        String originalCorrelationId,
        Outcome outcome,
        Disposition disposition,
        Long businessVersion,
        Instant asOf,
        Observation observation,
        ServerFreshness serverFreshness,
        ErrorCode error) {

    /** Approved C3 contract version literal. */
    public static final String CONTRACT_VERSION = "1";

    /** Approved maximum UTF-8 size of one JSON response. */
    public static final int MAX_UTF8_BYTES = 4096;

    /** The exact closed C3 key set, in canonical serialization order. */
    public static final java.util.List<String> KEYS = java.util.List.of(
            "asOf", "attemptId", "authorizationId", "businessVersion", "contractVersion",
            "correlationId", "disposition", "error", "observation", "originalCorrelationId",
            "outcome", "serverFreshness", "transactionId");

    private static final Pattern PROFILE_ALPHABET = Pattern.compile("[A-Za-z0-9._:-]+");
    private static final int MAX_ID_LENGTH = 128;
    private static final int MAX_CORRELATION_ID_LENGTH = 64;

    public C3ResponseEnvelope {
        if (!CONTRACT_VERSION.equals(contractVersion)) {
            throw new IllegalArgumentException("contractVersion must be \"" + CONTRACT_VERSION + "\"");
        }
        requireIdentifier("authorizationId", authorizationId, MAX_ID_LENGTH);
        requireIdentifier("transactionId", transactionId, MAX_ID_LENGTH);
        requireIdentifier("attemptId", attemptId, MAX_ID_LENGTH);
        requireIdentifier("correlationId", correlationId, MAX_CORRELATION_ID_LENGTH);
        requireIdentifier("originalCorrelationId", originalCorrelationId,
                MAX_CORRELATION_ID_LENGTH);
        Objects.requireNonNull(disposition, "disposition");
        Objects.requireNonNull(asOf, "asOf");
        Objects.requireNonNull(observation, "observation");
        Objects.requireNonNull(serverFreshness, "serverFreshness");
        if (businessVersion != null && businessVersion < 1) {
            throw new IllegalArgumentException("businessVersion must be a positive committed integer");
        }
        requireOutcomeDispositionCoupling(outcome, disposition);
        if (observation == Observation.UNKNOWN && serverFreshness != ServerFreshness.UNKNOWN) {
            throw new IllegalArgumentException(
                    "UNKNOWN observation requires UNKNOWN serverFreshness");
        }
        requireUtf8Budget(canonicalJson(contractVersion, authorizationId, transactionId,
                attemptId, correlationId, originalCorrelationId, outcome, disposition,
                businessVersion, asOf, observation, serverFreshness, error));
    }

    /** Caller-visible business outcome; {@code null} means no payment decision is asserted. */
    public enum Outcome {
        APPROVED,
        DECLINED,
        PROCESSOR_REJECTED,
        NOT_CONFIRMED
    }

    /** Exchange disposition as enumerated by C3. */
    public enum Disposition {
        COMPLETED,
        PENDING,
        REPLAY,
        REJECTED,
        NOT_DISPATCHED,
        UNAVAILABLE
    }

    /** Evidence completeness, independent of payment certainty. */
    public enum Observation {
        COMPLETE,
        PARTIAL,
        UNKNOWN
    }

    /** Retained-source/notification freshness, never payment certainty. */
    public enum ServerFreshness {
        CURRENT,
        STALE,
        UNKNOWN
    }

    /** The C3 error allowlist; no other code may cross the channel boundary. */
    public enum ErrorCode {
        UNAUTHENTICATED,
        CONTEXT_DENIED,
        INVALID_INPUT,
        UNSUPPORTED_MEDIA,
        BODY_TOO_LARGE,
        PAYLOAD_CONFLICT,
        INVALID_REFERENCE,
        NOT_FOUND,
        EVENT_HISTORY_UNAVAILABLE,
        LIVE_NOTIFICATIONS_UNAVAILABLE,
        LIVE_DELIVERY_LAG,
        RESERVED_UNSENT,
        BUSINESS_ADMISSION_UNAVAILABLE,
        EVENT_CAPACITY,
        BUSINESS_JOURNAL_UNAVAILABLE
    }

    /** Exact compact JSON used by the approved response-vector oracles. */
    public String toCanonicalJson() {
        return canonicalJson(contractVersion, authorizationId, transactionId, attemptId,
                correlationId, originalCorrelationId, outcome, disposition, businessVersion,
                asOf, observation, serverFreshness, error);
    }

    /** UTF-8 byte length of {@link #toCanonicalJson()}. */
    public int utf8Length() {
        return toCanonicalJson().getBytes(StandardCharsets.UTF_8).length;
    }

    private static void requireIdentifier(String name, String value, int maxLength) {
        if (value == null) {
            return;
        }
        if (value.isEmpty() || value.length() > maxLength
                || !PROFILE_ALPHABET.matcher(value).matches()) {
            throw new IllegalArgumentException(name + " is outside the approved profile bounds");
        }
    }

    private static void requireOutcomeDispositionCoupling(Outcome outcome, Disposition disposition) {
        boolean noPaymentDecision = disposition == Disposition.REJECTED
                || disposition == Disposition.NOT_DISPATCHED
                || disposition == Disposition.UNAVAILABLE;
        if (noPaymentDecision && outcome != null) {
            throw new IllegalArgumentException(
                    disposition + " cannot carry a payment outcome");
        }
        if (!noPaymentDecision && outcome == null) {
            throw new IllegalArgumentException(
                    disposition + " requires a payment outcome");
        }
    }

    private static void requireUtf8Budget(String json) {
        int length = json.getBytes(StandardCharsets.UTF_8).length;
        if (length > MAX_UTF8_BYTES) {
            throw new IllegalArgumentException(
                    "canonical response is " + length + " UTF-8 bytes, above "
                            + MAX_UTF8_BYTES);
        }
    }

    private static String canonicalJson(String contractVersion, String authorizationId,
            String transactionId, String attemptId, String correlationId,
            String originalCorrelationId, Outcome outcome, Disposition disposition,
            Long businessVersion, Instant asOf, Observation observation,
            ServerFreshness serverFreshness, ErrorCode error) {
        StringBuilder out = new StringBuilder(384);
        out.append('{');
        field(out, "asOf", quote(asOf.toString())).append(',');
        field(out, "attemptId", quoteOrNull(attemptId)).append(',');
        field(out, "authorizationId", quoteOrNull(authorizationId)).append(',');
        field(out, "businessVersion",
                businessVersion == null ? "null" : businessVersion.toString()).append(',');
        field(out, "contractVersion", quote(contractVersion)).append(',');
        field(out, "correlationId", quoteOrNull(correlationId)).append(',');
        field(out, "disposition", quote(disposition.name())).append(',');
        field(out, "error", error == null ? "null" : quote(error.name())).append(',');
        field(out, "observation", quote(observation.name())).append(',');
        field(out, "originalCorrelationId", quoteOrNull(originalCorrelationId)).append(',');
        field(out, "outcome", outcome == null ? "null" : quote(outcome.name())).append(',');
        field(out, "serverFreshness", quote(serverFreshness.name())).append(',');
        field(out, "transactionId", quoteOrNull(transactionId));
        out.append('}');
        return out.toString();
    }

    private static StringBuilder field(StringBuilder out, String name, String jsonValue) {
        out.append(quote(name)).append(':').append(jsonValue);
        return out;
    }

    private static String quoteOrNull(String value) {
        return value == null ? "null" : quote(value);
    }

    private static String quote(String value) {
        StringBuilder out = new StringBuilder(value.length() + 2);
        out.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.append('"').toString();
    }
}
