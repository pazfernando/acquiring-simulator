package com.acquiring.simulator.channel;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.HexFormat;
import java.util.regex.Pattern;

/**
 * Shared closed-shape validators and hash helpers for C4 carrier types.
 *
 * <p>Inputs are ASCII-only by contract, so the string rules are deliberately narrow:
 * identifiers use the profile alphabet, timestamps are UTC RFC3339 with a literal
 * {@code Z}, and hashes are lowercase 64-character hex. Nothing here reads a clock,
 * resolves a key, authenticates a sender or verifies a signature.</p>
 */
final class C4CarrierSchema {

    static final Pattern PROFILE_ALPHABET = Pattern.compile("[A-Za-z0-9._:-]+");
    static final Pattern PRINTABLE_ASCII = Pattern.compile("[\\x20-\\x7e]+");
    static final Pattern UPPERCASE_ALNUM = Pattern.compile("[A-Z0-9]+");
    static final Pattern HEX_64 = Pattern.compile("[0-9a-f]{64}");
    static final Pattern RFC3339_UTC =
            Pattern.compile("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(?:\\.\\d{1,9})?Z");

    private C4CarrierSchema() {
    }

    static String requireId(String name, String value, int maxLength) {
        requireNonNull(name, value);
        if (value.isEmpty() || value.length() > maxLength
                || !PROFILE_ALPHABET.matcher(value).matches()) {
            throw C4CarrierException.of(C4CarrierViolation.CARRIER_SCHEMA, name);
        }
        return value;
    }

    static String requireText(String name, String value, int maxLength) {
        requireNonNull(name, value);
        if (value.isEmpty() || value.length() > maxLength
                || !PRINTABLE_ASCII.matcher(value).matches()) {
            throw C4CarrierException.of(C4CarrierViolation.CARRIER_SCHEMA, name);
        }
        return value;
    }

    static String requireDigits(String name, String value, int exactLength) {
        requireNonNull(name, value);
        if (value.length() != exactLength) {
            throw C4CarrierException.of(C4CarrierViolation.CARRIER_SCHEMA, name);
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c < '0' || c > '9') {
                throw C4CarrierException.of(C4CarrierViolation.CARRIER_SCHEMA, name);
            }
        }
        return value;
    }

    static String requireHex64(String name, String value) {
        requireNonNull(name, value);
        if (!HEX_64.matcher(value).matches()) {
            throw C4CarrierException.of(C4CarrierViolation.CARRIER_SCHEMA, name);
        }
        return value;
    }

    /**
     * Exact-width uppercase letters/digits, matching the P1 fixed character fields such as
     * DE37 (12) and DE38 (6).
     */
    static String requireUppercaseAlnumExact(String name, String value, int exactLength) {
        requireNonNull(name, value);
        if (value.length() != exactLength
                || !UPPERCASE_ALNUM.matcher(value).matches()) {
            throw C4CarrierException.of(C4CarrierViolation.CARRIER_SCHEMA, name);
        }
        return value;
    }

    /** Null or an exact-width uppercase letters/digits value; used for optional DE38. */
    static String requireNullableUppercaseAlnumExact(
            String name, String value, int exactLength) {
        if (value == null) {
            return null;
        }
        return requireUppercaseAlnumExact(name, value, exactLength);
    }

    /** Inclusive integer range. */
    static int requireIntRange(String name, int value, int min, int max) {
        if (value < min || value > max) {
            throw C4CarrierException.of(C4CarrierViolation.CARRIER_SCHEMA, name);
        }
        return value;
    }

    static String requireIsoInstantUtc(String name, String value) {
        requireNonNull(name, value);
        if (!RFC3339_UTC.matcher(value).matches()) {
            throw C4CarrierException.of(C4CarrierViolation.CARRIER_SCHEMA, name);
        }
        try {
            Instant.parse(value);
        } catch (DateTimeParseException e) {
            throw C4CarrierException.of(C4CarrierViolation.CARRIER_SCHEMA, name);
        }
        return value;
    }

    static String requireExact(String name, String value, String expected) {
        requireNonNull(name, value);
        if (!expected.equals(value)) {
            throw C4CarrierException.of(C4CarrierViolation.CARRIER_SCHEMA, name);
        }
        return value;
    }

    static int requirePositiveInt(String name, int value, int max) {
        if (value < 1 || value > max) {
            throw C4CarrierException.of(C4CarrierViolation.CARRIER_SCHEMA, name);
        }
        return value;
    }

    private static void requireNonNull(String name, String value) {
        if (value == null) {
            throw C4CarrierException.of(C4CarrierViolation.CARRIER_SCHEMA, name);
        }
    }

    static String sha256Hex(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    static String canonicalBase64(byte[] bytes) {
        return Base64.getEncoder().encodeToString(bytes);
    }

    static byte[] asciiBytes(String value) {
        return value.getBytes(StandardCharsets.US_ASCII);
    }
}
