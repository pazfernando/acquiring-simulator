package com.acquiring.simulator.protocol.iso8583;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Strict parser for the SIM-ISO87A-v1 canonical DE48 response extension.
 *
 * <p>Canonical form: the sixteen required request keys echoed in approved
 * request order, the optional {@code prior}, then the mandatory response tail
 * {@code actor,stage,result,reason,evidence,at}. Pairs are ASCII
 * {@code key=value} separated by a single semicolon with no terminal delimiter,
 * spaces, escaping, percent encoding or nested data. Keys are case-sensitive,
 * every tail key is required and nothing is normalized: an input that is not
 * already canonical is rejected.</p>
 *
 * <p>The canonical lexical rules (alphabet, per-key bounds, literals, enums,
 * digit widths) are the same ones the request parser enforces. The response
 * order is checked independently over the complete key sequence; this parser
 * never delegates the echoed prefix to the request parser, which would accept a
 * request-only key set and an optional trailing {@code prior} without proving
 * the mandatory response tail.</p>
 *
 * <p>First-failure order follows P6's DE48 phase: aggregate size, pair
 * separators, non-empty key/value, known keys, duplicate keys, full response
 * order and required presence, then per-key byte bounds, value alphabet,
 * literals, enums and digit forms. {@code sent} and {@code at} are checked for
 * digits and width only: Gregorian calendar validity, DE7 consistency, actual
 * evidence time, the P4 actor/stage/result matrix, DE38 binding, freshness, the
 * 826-byte exchange budget, trusted evidence, response construction, packing,
 * HTTP carriage and dispatch are out of scope.</p>
 */
public final class Iso8583De48ResponseParser {

    /** Structural DE48 maximum from P1; the exchange budget (826) is separate. */
    public static final int MAX_BYTES = 999;

    /** Required request keys echoed by a response, in approved order. */
    public static final List<String> REQUEST_ECHO_KEYS = List.of(
            "v", "op", "aid", "cid", "tid", "att", "acq", "mid", "term", "pay",
            "gen", "fixture", "cvm", "crypto", "risk", "sent");

    /** Optional authorized original authorization ID, after {@code sent}. */
    public static final String PRIOR_KEY = "prior";

    /** Mandatory response tail, in approved order. */
    public static final List<String> RESPONSE_KEYS = List.of(
            "actor", "stage", "result", "reason", "evidence", "at");

    private static final List<String> KNOWN_KEYS = knownKeys();

    private static final int GEN_MAX = 2_147_483_647;

    private static final String VALUE_ALPHABET =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789._:-";

    private static final Set<String> CVM_VALUES = Set.of("VERIFIED", "NOT_REQUIRED");
    private static final Set<String> CRYPTO_VALUES = Set.of("0", "1");
    private static final Set<String> RISK_VALUES = Set.of("PASS", "REJECT");
    private static final Set<String> ACTOR_VALUES = Set.of("ISSUER", "NETWORK", "PROCESSOR");
    private static final Set<String> STAGE_VALUES =
            Set.of("ISSUER_REPLIED", "BEFORE_ISSUER", "POSSIBLE_ISSUER", "BEFORE_NETWORK");
    private static final Set<String> RESULT_VALUES =
            Set.of("APPROVED", "DECLINED", "PROCESSOR_REJECTED", "NOT_CONFIRMED");
    private static final Set<String> REASON_VALUES = Set.of(
            "APPROVED", "DELIBERATE_DECLINE", "FORMAT", "DISALLOWED", "UNAVAILABLE", "UNCERTAIN");

    private Iso8583De48ResponseParser() {
    }

    /**
     * Parse one canonical DE48 response extension.
     *
     * @param de48 exact extension bytes as carried by DE48 (ASCII one char per
     *             octet)
     * @throws Iso8583De48Exception on any canonical-schema violation
     */
    public static Iso8583De48Response parse(String de48) {
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
            if (!KNOWN_KEYS.contains(key)) {
                throw new Iso8583De48Exception(
                        Iso8583De48Exception.Rule.UNKNOWN_KEY,
                        key,
                        "unknown canonical response key: " + key);
            }
            if (keys.contains(key)) {
                throw new Iso8583De48Exception(
                        Iso8583De48Exception.Rule.DUPLICATE_KEY,
                        key,
                        "duplicate canonical response key: " + key);
            }
            keys.add(key);
            values.add(value);
        }

        requireResponseOrderAndPresence(keys);

        Map<String, String> accepted = new LinkedHashMap<>();
        for (int i = 0; i < keys.size(); i++) {
            String key = keys.get(i);
            String value = values.get(i);
            requireCanonicalValue(key, value);
            accepted.put(key, value);
        }
        return new Iso8583De48Response(accepted);
    }

    /**
     * Full-sequence order check: request echo keys, optional prior, then the
     * mandatory response tail. Deferring the echo prefix to the request parser
     * would not prove the tail, so the complete sequence is checked here.
     */
    private static void requireResponseOrderAndPresence(List<String> keys) {
        int index = 0;
        for (String expected : REQUEST_ECHO_KEYS) {
            index = requireKeyAt(keys, index, expected);
        }
        if (index < keys.size() && keys.get(index).equals(PRIOR_KEY)) {
            index++;
        }
        for (String expected : RESPONSE_KEYS) {
            index = requireKeyAt(keys, index, expected);
        }
        if (index < keys.size()) {
            String actual = keys.get(index);
            throw new Iso8583De48Exception(
                    Iso8583De48Exception.Rule.OUT_OF_ORDER_KEY,
                    actual,
                    "key " + actual + " at position " + index
                            + " appears after the canonical response tail");
        }
    }

    private static int requireKeyAt(List<String> keys, int index, String expected) {
        if (index >= keys.size()) {
            throw new Iso8583De48Exception(
                    Iso8583De48Exception.Rule.MISSING_REQUIRED_KEY,
                    expected,
                    "missing required canonical response key: " + expected);
        }
        String actual = keys.get(index);
        if (actual.equals(expected)) {
            return index + 1;
        }
        if (keys.subList(index, keys.size()).contains(expected)) {
            throw new Iso8583De48Exception(
                    Iso8583De48Exception.Rule.OUT_OF_ORDER_KEY,
                    actual,
                    "key " + actual + " at position " + index
                            + " is out of canonical order; expected " + expected);
        }
        throw new Iso8583De48Exception(
                Iso8583De48Exception.Rule.MISSING_REQUIRED_KEY,
                expected,
                "missing required canonical response key: " + expected);
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
            case "sent", "at" -> requireDigits(key, value);
            case "actor" -> requireEnum(key, value, ACTOR_VALUES);
            case "stage" -> requireEnum(key, value, STAGE_VALUES);
            case "result" -> requireEnum(key, value, RESULT_VALUES);
            case "reason" -> requireEnum(key, value, REASON_VALUES);
            default -> {
                // References and evidence are checked by bounds and alphabet only.
            }
        }
    }

    private static int minimumLength(String key) {
        return switch (key) {
            case "sent", "at" -> 14;
            default -> 1;
        };
    }

    private static int maximumLength(String key) {
        return switch (key) {
            case "v", "op", "crypto" -> 1;
            case "aid", "cid", "tid", "att", "fixture", PRIOR_KEY, "evidence" -> 64;
            case "acq", "mid", "term", "pay" -> 128;
            case "gen" -> 10;
            case "cvm" -> 16;
            case "risk" -> 6;
            case "sent", "at" -> 14;
            case "actor", "stage", "result", "reason" -> MAX_BYTES;
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

    private static List<String> knownKeys() {
        List<String> keys = new ArrayList<>(REQUEST_ECHO_KEYS);
        keys.add(PRIOR_KEY);
        keys.addAll(RESPONSE_KEYS);
        return List.copyOf(keys);
    }
}
