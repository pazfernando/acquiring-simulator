package com.acquiring.simulator.protocol.iso8583;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Strict parser for the SIM-ISO87A-v1 canonical DE48 request extension.
 *
 * <p>Canonical form: ordered ASCII {@code key=value} pairs separated by a
 * single semicolon, no terminal delimiter, no spaces, no escaping, no percent
 * encoding and no nested data. Keys are case-sensitive and must appear in the
 * approved request order; every key except optional trailing {@code prior} is
 * required. Empty values are invalid. Nothing is normalized: an input that is
 * not already canonical is rejected.</p>
 *
 * <p>First-failure order follows P6's DE48 phase: aggregate size, pair
 * separators, non-empty key/value, known keys, duplicate keys, order and
 * required presence, then per-key byte bounds, value alphabet, literals, enums
 * and the {@code gen} integer form. The {@code sent} check here is digits and
 * width only; Gregorian calendar validity and DE7 consistency are domain
 * checks. Amount, currency, freshness, the 826-byte exchange budget, trusted
 * evidence, response construction and dispatch are out of scope.</p>
 */
public final class Iso8583De48RequestParser {

    /** Structural DE48 maximum from P1; the exchange budget (826) is separate. */
    public static final int MAX_BYTES = 999;

    /** Approved canonical request order; {@code prior} is the only optional key. */
    public static final List<String> REQUEST_KEYS = List.of(
            "v", "op", "aid", "cid", "tid", "att", "acq", "mid", "term", "pay",
            "gen", "fixture", "cvm", "crypto", "risk", "sent", "prior");

    private static final List<String> REQUIRED_KEYS = REQUEST_KEYS.subList(0, 16);

    private static final int GEN_MAX = 2_147_483_647;

    private static final String VALUE_ALPHABET =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789._:-";

    private static final Set<String> CVM_VALUES = Set.of("VERIFIED", "NOT_REQUIRED");
    private static final Set<String> CRYPTO_VALUES = Set.of("0", "1");
    private static final Set<String> RISK_VALUES = Set.of("PASS", "REJECT");

    private Iso8583De48RequestParser() {
    }

    /**
     * Parse one canonical DE48 request extension.
     *
     * @param de48 exact extension bytes as carried by DE48 (ASCII one char per
     *             octet)
     * @throws Iso8583De48Exception on any canonical-schema violation
     */
    public static Iso8583De48Request parse(String de48) {
        if (de48 == null || de48.isEmpty()) {
            throw new Iso8583De48Exception(
                    Iso8583De48Exception.Rule.EMPTY_FIELD, "", "DE48 must not be empty");
        }
        if (de48.length() > MAX_BYTES) {
            throw new Iso8583De48Exception(
                    Iso8583De48Exception.Rule.DE48_TOO_LARGE,
                    "",
                    "DE48 is " + de48.length() + " octets, maximum is " + MAX_BYTES);
        }
        if (de48.endsWith(";")) {
            throw new Iso8583De48Exception(
                    Iso8583De48Exception.Rule.TRAILING_DELIMITER,
                    "",
                    "DE48 must not end with a terminal semicolon");
        }

        List<String> keys = new ArrayList<>();
        List<String> values = new ArrayList<>();
        for (String segment : de48.split(";", -1)) {
            if (segment.isEmpty()) {
                throw new Iso8583De48Exception(
                        Iso8583De48Exception.Rule.EMPTY_FIELD,
                        "",
                        "empty key=value pair at segment " + keys.size());
            }
            int equals = segment.indexOf('=');
            if (equals < 0 || segment.indexOf('=', equals + 1) >= 0) {
                throw new Iso8583De48Exception(
                        Iso8583De48Exception.Rule.MALFORMED_FIELD,
                        "",
                        "segment " + keys.size() + " is not exactly one key=value pair");
            }
            String key = segment.substring(0, equals);
            String value = segment.substring(equals + 1);
            if (key.isEmpty() || value.isEmpty()) {
                throw new Iso8583De48Exception(
                        Iso8583De48Exception.Rule.EMPTY_FIELD,
                        key,
                        "key and value must both be non-empty");
            }
            if (!REQUEST_KEYS.contains(key)) {
                throw new Iso8583De48Exception(
                        Iso8583De48Exception.Rule.UNKNOWN_KEY,
                        key,
                        "unknown canonical request key: " + key);
            }
            if (keys.contains(key)) {
                throw new Iso8583De48Exception(
                        Iso8583De48Exception.Rule.DUPLICATE_KEY,
                        key,
                        "duplicate canonical request key: " + key);
            }
            keys.add(key);
            values.add(value);
        }

        requireOrderAndPresence(keys);

        Map<String, String> accepted = new LinkedHashMap<>();
        for (int i = 0; i < keys.size(); i++) {
            String key = keys.get(i);
            String value = values.get(i);
            requireCanonicalValue(key, value);
            accepted.put(key, value);
        }
        return new Iso8583De48Request(accepted);
    }

    private static void requireOrderAndPresence(List<String> keys) {
        int compared = Math.min(keys.size(), REQUIRED_KEYS.size());
        for (int i = 0; i < compared; i++) {
            String actual = keys.get(i);
            String expected = REQUEST_KEYS.get(i);
            if (!actual.equals(expected)) {
                if (keys.subList(i, keys.size()).contains(expected)) {
                    throw new Iso8583De48Exception(
                            Iso8583De48Exception.Rule.OUT_OF_ORDER_KEY,
                            actual,
                            "key " + actual + " at position " + i
                                    + " is out of canonical order; expected " + expected);
                }
                throw new Iso8583De48Exception(
                        Iso8583De48Exception.Rule.MISSING_REQUIRED_KEY,
                        expected,
                        "missing required canonical request key: " + expected);
            }
        }
        if (keys.size() < REQUIRED_KEYS.size()) {
            String missing = REQUIRED_KEYS.get(keys.size());
            throw new Iso8583De48Exception(
                    Iso8583De48Exception.Rule.MISSING_REQUIRED_KEY,
                    missing,
                    "missing required canonical request key: " + missing);
        }
    }

    private static void requireCanonicalValue(String key, String value) {
        int min = minimumLength(key);
        int max = maximumLength(key);
        if (value.length() < min || value.length() > max) {
            throw new Iso8583De48Exception(
                    Iso8583De48Exception.Rule.VALUE_LENGTH_OUT_OF_RANGE,
                    key,
                    "value for " + key + " is " + value.length()
                            + " octets, expected " + min + ".." + max);
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (VALUE_ALPHABET.indexOf(c) < 0) {
                throw new Iso8583De48Exception(
                        Iso8583De48Exception.Rule.INVALID_VALUE_ALPHABET,
                        key,
                        "value for " + key + " contains a disallowed octet at offset " + i
                                + ": U+" + String.format("%04X", (int) c));
            }
        }
        switch (key) {
            case "v" -> requireLiteral(key, value, "1");
            case "op" -> requireLiteral(key, value, "A");
            case "gen" -> requireGeneration(value);
            case "cvm" -> requireEnum(key, value, CVM_VALUES);
            case "crypto" -> requireEnum(key, value, CRYPTO_VALUES);
            case "risk" -> requireEnum(key, value, RISK_VALUES);
            case "sent" -> requireDigits(key, value);
            default -> {
                // References and fixture are checked by bounds and alphabet only.
            }
        }
    }

    private static int minimumLength(String key) {
        return switch (key) {
            case "v", "op", "crypto" -> 1;
            case "sent" -> 14;
            default -> 1;
        };
    }

    private static int maximumLength(String key) {
        return switch (key) {
            case "v", "op", "crypto" -> 1;
            case "aid", "cid", "tid", "att", "fixture", "prior" -> 64;
            case "acq", "mid", "term", "pay" -> 128;
            case "gen" -> 10;
            case "cvm" -> 16;
            case "risk" -> 6;
            case "sent" -> 14;
            default -> throw new IllegalStateException("unmapped canonical key: " + key);
        };
    }

    private static void requireLiteral(String key, String value, String literal) {
        if (!value.equals(literal)) {
            throw new Iso8583De48Exception(
                    Iso8583De48Exception.Rule.INVALID_LITERAL,
                    key,
                    "value for " + key + " must be the literal " + literal);
        }
    }

    private static void requireEnum(String key, String value, Set<String> allowed) {
        if (!allowed.contains(value)) {
            throw new Iso8583De48Exception(
                    Iso8583De48Exception.Rule.INVALID_ENUM,
                    key,
                    "value for " + key + " is not one of " + allowed);
        }
    }

    private static void requireGeneration(String value) {
        requireDigits("gen", value);
        if (value.length() > 1 && value.charAt(0) == '0') {
            throw new Iso8583De48Exception(
                    Iso8583De48Exception.Rule.INVALID_NUMBER,
                    "gen",
                    "generation must not have leading zeros: " + value);
        }
        long generation = Long.parseLong(value);
        if (generation < 1 || generation > GEN_MAX) {
            throw new Iso8583De48Exception(
                    Iso8583De48Exception.Rule.INVALID_NUMBER,
                    "gen",
                    "generation is outside 1.." + GEN_MAX + ": " + value);
        }
    }

    private static void requireDigits(String key, String value) {
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c < '0' || c > '9') {
                throw new Iso8583De48Exception(
                        Iso8583De48Exception.Rule.INVALID_DIGITS,
                        key,
                        "value for " + key + " must be ASCII digits only; octet " + i
                                + " is U+" + String.format("%04X", (int) c));
            }
        }
    }
}
