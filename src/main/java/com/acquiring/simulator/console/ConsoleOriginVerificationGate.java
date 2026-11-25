package com.acquiring.simulator.console;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * The N-02 origin-verification boundary gate.
 *
 * <p><strong>This is not an authentication factor.</strong> It compares the value CloudFront injects
 * as an origin custom header against the deployment-configured value and nothing else. It never
 * identifies a subject, never reads the verified principal and does not replace, weaken or order
 * around the JWT authorizer. Both controls are mandatory.</p>
 *
 * <h2>Classification is a closed, exhaustive set</h2>
 * <ul>
 *   <li>{@code ABSENT} — no occurrence of the header name;</li>
 *   <li>{@code EMPTY} — exactly one occurrence whose value is empty or only optional whitespace;</li>
 *   <li>{@code DUPLICATE} — more than one occurrence, one occurrence containing a comma (the HTTP
 *       API v2 coalesced form), or more than one map entry differing only in header-name case;</li>
 *   <li>{@code MISMATCH} — exactly one occurrence, non-empty, byte-unequal to the expected value;</li>
 *   <li>{@code VERIFIED} — exactly one occurrence, non-empty, byte-equal to the expected value.</li>
 * </ul>
 *
 * <p>All four refusal reasons are terminal and produce the same opaque response. There is no
 * partial acceptance, no log-and-continue, no default and no runtime switch.</p>
 *
 * <h2>Comparison discipline</h2>
 * <p>The expected value is read once at construction. A missing, empty, shorter-than-minimum or
 * out-of-alphabet value fails initialization rather than starting unverified. The received value is
 * compared byte-exactly and case-sensitively with a constant-time digest comparison; a length
 * difference fails without a content comparison. No trimming, case folding, percent-decoding or
 * normalization is applied to the received value.</p>
 */
public final class ConsoleOriginVerificationGate {

    /** The fixed header name shared with the CloudFront origin custom header and the policy check. */
    public static final String HEADER_NAME = "X-Origin-Verify";

    /** The deployment configuration variable that carries the expected value. */
    public static final String ENVIRONMENT_VARIABLE = "EDGE_ORIGIN_VERIFY";

    /** Minimum accepted configuration length; matches the authored Terraform validation. */
    public static final int MINIMUM_LENGTH = 32;

    /** Closed RFC 7230 token-safe alphabet, matching the authored Terraform validation. */
    private static final Pattern ALPHABET = Pattern.compile("[A-Za-z0-9._~-]+");

    private final byte[] expected;

    private ConsoleOriginVerificationGate(byte[] expected) {
        this.expected = expected;
    }

    /**
     * Validate an explicitly supplied expected value. Throws rather than defaulting so a composition
     * without the control cannot start.
     */
    public static ConsoleOriginVerificationGate configured(String value) {
        if (value == null || value.isEmpty()) {
            throw new IllegalStateException(
                    "origin verification requires a non-empty " + ENVIRONMENT_VARIABLE);
        }
        if (value.length() < MINIMUM_LENGTH) {
            throw new IllegalStateException("origin verification " + ENVIRONMENT_VARIABLE
                    + " must be at least " + MINIMUM_LENGTH + " characters");
        }
        if (!ALPHABET.matcher(value).matches()) {
            throw new IllegalStateException("origin verification " + ENVIRONMENT_VARIABLE
                    + " contains a character outside the permitted token alphabet");
        }
        return new ConsoleOriginVerificationGate(value.getBytes(StandardCharsets.US_ASCII));
    }

    /** Resolve and validate from the process environment; absent means initialization fails. */
    public static ConsoleOriginVerificationGate fromEnvironment() {
        return from(System::getenv);
    }

    /** Resolver seam so fail-closed initialization can be asserted without mutating the process. */
    public static ConsoleOriginVerificationGate from(Function<String, String> source) {
        Objects.requireNonNull(source, "source");
        return configured(source.apply(ENVIRONMENT_VARIABLE));
    }

    /**
     * Classify exactly one request. The result carries only the reason class: it never carries the
     * received value, its length, a prefix, a hash or a comparison artifact.
     */
    public Result evaluate(ConsoleHttpRequest request) {
        Objects.requireNonNull(request, "request");
        List<String> values = request.headerValues(HEADER_NAME);
        if (values.isEmpty()) {
            return Result.refused(Reason.ABSENT);
        }
        if (values.size() > 1) {
            return Result.refused(Reason.DUPLICATE);
        }
        String value = values.get(0);
        if (value.isEmpty() || isOptionalWhitespace(value)) {
            return Result.refused(Reason.EMPTY);
        }
        if (value.indexOf(',') >= 0) {
            return Result.refused(Reason.DUPLICATE);
        }
        byte[] received = value.getBytes(StandardCharsets.UTF_8);
        // MessageDigest.isEqual returns false without a content comparison on a length difference,
        // and compares content in constant time otherwise.
        if (MessageDigest.isEqual(expected, received)) {
            return new Result(Decision.CONTINUE, Reason.VERIFIED);
        }
        return Result.refused(Reason.MISMATCH);
    }

    /** Optional whitespace in the RFC 7230 sense: SP and HTAB only. */
    private static boolean isOptionalWhitespace(String value) {
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c != ' ' && c != '\t') {
                return false;
            }
        }
        return true;
    }

    /** The gate either lets the request proceed to the application or refuses it. */
    public enum Decision {
        CONTINUE,
        REFUSE
    }

    /** The closed set of classification reasons. */
    public enum Reason {
        VERIFIED,
        ABSENT,
        EMPTY,
        DUPLICATE,
        MISMATCH
    }

    /** Classification of one request. Never holds request data. */
    public record Result(Decision decision, Reason reason) {

        public Result {
            Objects.requireNonNull(decision, "decision");
            Objects.requireNonNull(reason, "reason");
        }

        private static Result refused(Reason reason) {
            return new Result(Decision.REFUSE, reason);
        }

        public boolean refused() {
            return decision == Decision.REFUSE;
        }
    }
}
