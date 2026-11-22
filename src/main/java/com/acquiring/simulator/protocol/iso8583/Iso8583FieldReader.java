package com.acquiring.simulator.protocol.iso8583;

/**
 * Strict structural cursor over a SIM-ISO87A-v1 ASCII body.
 *
 * <p>The cursor exposes the four P1 encodings only: fixed numeric, fixed
 * uppercase alphanumeric, LL numeric and LLL printable ASCII. LL/LLL length
 * prefixes are ASCII decimal byte lengths that exclude the prefix octets.
 * Reads never pad, trim, round or truncate: a fixed-width read consumes exactly
 * that many octets, and a variable read consumes exactly the declared length.</p>
 *
 * <p>Scope: this primitive checks framing, body bounds and the per-encoding
 * alphabet. It does not check bitmap field presence, DE48 key=value ordering or
 * bounds, amounts, dates, currencies, fixtures, admission, dispatch or any
 * domain rule. Those remain later codec/profile phases.</p>
 */
public final class Iso8583FieldReader {

    private final String body;
    private int position;

    private Iso8583FieldReader(String body, int offset) {
        if (body == null) {
            throw new Iso8583FieldException(
                    Iso8583FieldException.Rule.BODY_TOO_SHORT, "body must not be null");
        }
        if (offset < 0 || offset > body.length()) {
            throw new Iso8583FieldException(
                    Iso8583FieldException.Rule.START_OUT_OF_BOUNDS,
                    "cursor offset " + offset + " is outside body length " + body.length());
        }
        this.body = body;
        this.position = offset;
    }

    /** Cursor positioned at the first octet of {@code body}. */
    public static Iso8583FieldReader over(String body) {
        return new Iso8583FieldReader(body, 0);
    }

    /** Cursor positioned at {@code offset} of {@code body}. */
    public static Iso8583FieldReader startingAt(String body, int offset) {
        return new Iso8583FieldReader(body, offset);
    }

    /** Absolute index of the next unconsumed octet. */
    public int position() {
        return position;
    }

    /** Number of unconsumed octets up to the end of the body. */
    public int remaining() {
        return body.length() - position;
    }

    public boolean hasRemaining() {
        return position < body.length();
    }

    /**
     * Fixed numeric field: exactly {@code width} ASCII digits.
     *
     * @throws Iso8583FieldException with {@code BODY_TOO_SHORT} when fewer than
     *         {@code width} octets remain, or {@code INVALID_NUMERIC} when any
     *         octet is not an ASCII digit
     */
    public String readFixedDigits(int width) {
        requirePositiveWidth(width);
        String value = peek(width);
        requireDigits(value, position, Iso8583FieldException.Rule.INVALID_NUMERIC);
        position += width;
        return value;
    }

    /**
     * Fixed uppercase alphanumeric field: exactly {@code width} octets from
     * {@code A-Z} or {@code 0-9}. No space padding or lowercase is accepted.
     *
     * @throws Iso8583FieldException with {@code BODY_TOO_SHORT} when fewer than
     *         {@code width} octets remain, or {@code INVALID_UPPER_ALNUM} when
     *         any octet is outside the allowed alphabet
     */
    public String readFixedUpperAlnum(int width) {
        requirePositiveWidth(width);
        String value = peek(width);
        requireUpperAlnum(value, position);
        position += width;
        return value;
    }

    /**
     * LL numeric field as declared by P1: a two-digit ASCII decimal byte length
     * followed by that many digits. The prefix excludes itself, so consumption
     * is {@code 2 + length}.
     *
     * @param minDigits smallest accepted declared value length, at least 1
     * @param maxDigits largest accepted declared value length
     * @throws Iso8583FieldException with {@code BODY_TOO_SHORT} for a truncated
     *         prefix or value, {@code INVALID_LENGTH_PREFIX} for a non-decimal
     *         prefix, {@code LENGTH_OUT_OF_RANGE} for a declared length outside
     *         {@code minDigits..maxDigits}, or {@code INVALID_NUMERIC} for a
     *         non-digit value octet
     */
    public String readLlDigits(int minDigits, int maxDigits) {
        requireRange(minDigits, maxDigits, 99);
        int length = readLengthPrefix(2, minDigits, maxDigits);
        String value = peekAt(position + 2, length);
        requireDigits(value, position + 2, Iso8583FieldException.Rule.INVALID_NUMERIC);
        position += 2 + length;
        return value;
    }

    /**
     * LLL printable ASCII field as declared by P1: a three-digit ASCII decimal
     * byte length followed by that many printable ASCII octets (U+0020..U+007E).
     * The prefix excludes itself, so consumption is {@code 3 + length}.
     *
     * <p>This is structural only. Canonical DE48 key=value grammar, key order,
     * duplicate/unknown keys, individual key bounds and admission limits are
     * checked by later phases; the profile's no-space rule is part of that
     * canonical schema, not of this byte-length check.</p>
     *
     * @param minBytes smallest accepted declared value length, at least 1
     * @param maxBytes largest accepted declared value length, at most 999
     */
    public String readLllPrintableAscii(int minBytes, int maxBytes) {
        requireRange(minBytes, maxBytes, 999);
        int length = readLengthPrefix(3, minBytes, maxBytes);
        String value = peekAt(position + 3, length);
        requirePrintableAscii(value, position + 3);
        position += 3 + length;
        return value;
    }

    /**
     * Reject any unconsumed octet. There is no implicit trailing-ignore: a body
     * must end exactly after its last field.
     *
     * @throws Iso8583FieldException with {@code TRAILING_BYTES} when octets
     *         remain at the cursor
     */
    public void requireEnd() {
        if (hasRemaining()) {
            throw new Iso8583FieldException(
                    Iso8583FieldException.Rule.TRAILING_BYTES,
                    remaining() + " trailing octet(s) at body offset " + position);
        }
    }

    private String peek(int count) {
        return peekAt(position, count);
    }

    private String peekAt(int start, int count) {
        if (start + count > body.length()) {
            throw new Iso8583FieldException(
                    Iso8583FieldException.Rule.BODY_TOO_SHORT,
                    "need " + count + " octet(s) at body offset " + start
                            + ", only " + (body.length() - start) + " remain");
        }
        return body.substring(start, start + count);
    }

    /**
     * Decode a fixed-width ASCII decimal prefix without advancing the cursor.
     * The declared length is validated before any value octet is inspected, so
     * an over-limit prefix fails even when matching value octets are present.
     */
    private int readLengthPrefix(int prefixWidth, int min, int max) {
        String prefix = peek(prefixWidth);
        requireDigits(prefix, position, Iso8583FieldException.Rule.INVALID_LENGTH_PREFIX);
        int length = Integer.parseInt(prefix);
        if (length < min || length > max) {
            throw new Iso8583FieldException(
                    Iso8583FieldException.Rule.LENGTH_OUT_OF_RANGE,
                    "declared length " + length + " at body offset " + position
                            + " is outside " + min + ".." + max);
        }
        return length;
    }

    private static void requirePositiveWidth(int width) {
        if (width < 1) {
            throw new IllegalArgumentException("fixed field width must be at least 1: " + width);
        }
    }

    private static void requireRange(int min, int max, int encodableMax) {
        if (min < 1 || max < min || max > encodableMax) {
            throw new IllegalArgumentException(
                    "invalid declared length range " + min + ".." + max
                            + " for a prefix with encodable maximum " + encodableMax);
        }
    }

    private static void requireDigits(
            String value, int absoluteStart, Iso8583FieldException.Rule rule) {
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c < '0' || c > '9') {
                throw new Iso8583FieldException(
                        rule,
                        "octet at body offset " + (absoluteStart + i)
                                + " is not an ASCII decimal digit: U+"
                                + String.format("%04X", (int) c));
            }
        }
    }

    private static void requireUpperAlnum(String value, int absoluteStart) {
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            boolean upper = c >= 'A' && c <= 'Z';
            boolean digit = c >= '0' && c <= '9';
            if (!upper && !digit) {
                throw new Iso8583FieldException(
                        Iso8583FieldException.Rule.INVALID_UPPER_ALNUM,
                        "octet at body offset " + (absoluteStart + i)
                                + " is not uppercase A-Z or 0-9: U+"
                                + String.format("%04X", (int) c));
            }
        }
    }

    private static void requirePrintableAscii(String value, int absoluteStart) {
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c < 0x20 || c > 0x7E) {
                throw new Iso8583FieldException(
                        Iso8583FieldException.Rule.NON_PRINTABLE_ASCII,
                        "octet at body offset " + (absoluteStart + i)
                                + " is not printable ASCII: U+"
                                + String.format("%04X", (int) c));
            }
        }
    }
}
