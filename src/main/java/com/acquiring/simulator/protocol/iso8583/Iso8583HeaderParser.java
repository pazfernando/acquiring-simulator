package com.acquiring.simulator.protocol.iso8583;

import java.util.Set;

/**
 * Strict, parser-only primitive for the SIM-ISO87A-v1 header.
 *
 * <p>Accepted syntax is exactly: MTI (4 ASCII digits) followed by the primary
 * bitmap (16 uppercase hexadecimal ASCII characters, bit 1 = first/most
 * significant bit of the first hexadecimal digit). No header, delimiter, length
 * frame, BOM or secondary bitmap is accepted.</p>
 *
 * <p>Scope: this type checks the header prefix only. It does not parse fields,
 * enforce the DE presence matrices, or claim amount/date/domain validation,
 * financial admission, response budgeting or dispatch. Those remain later
 * phases of the profile.</p>
 */
public final class Iso8583HeaderParser {

    public static final int MTI_LENGTH = 4;
    public static final int BITMAP_LENGTH = 16;
    public static final int HEADER_LENGTH = MTI_LENGTH + BITMAP_LENGTH;

    /** MTIs selected by the approved authorization-A profile. */
    public static final Set<String> SUPPORTED_MTIS = Set.of("0100", "0110");

    /**
     * Data elements structurally representable by the selected profile.
     * DE38/DE39 are response-conditional and presence-checked later, never here.
     */
    public static final Set<Integer> PROFILE_FIELDS =
            Set.of(3, 4, 7, 11, 22, 25, 32, 37, 38, 39, 41, 42, 48, 49);

    private Iso8583HeaderParser() {
    }

    /**
     * Parse the header prefix of an ASCII body.
     *
     * @param body complete ASCII body; only the first 20 octets are inspected
     * @return parsed MTI and primary bitmap
     * @throws Iso8583HeaderParseException when any header rule is violated
     */
    public static Iso8583Header parse(String body) {
        if (body == null) {
            throw new Iso8583HeaderParseException(
                    Iso8583HeaderParseException.Rule.BODY_TOO_SHORT, "body must not be null");
        }
        if (body.length() < HEADER_LENGTH) {
            throw new Iso8583HeaderParseException(
                    Iso8583HeaderParseException.Rule.BODY_TOO_SHORT,
                    "header requires " + HEADER_LENGTH + " ASCII octets, got " + body.length());
        }

        String header = body.substring(0, HEADER_LENGTH);
        requirePrintableAscii(header);

        String mti = header.substring(0, MTI_LENGTH);
        requireDigits(mti);
        if (!SUPPORTED_MTIS.contains(mti)) {
            throw new Iso8583HeaderParseException(
                    Iso8583HeaderParseException.Rule.UNSUPPORTED_MTI,
                    "MTI not selected by SIM-ISO87A-v1: " + mti);
        }

        String bitmapText = header.substring(MTI_LENGTH);
        long bits = parseBitmap(bitmapText);

        if ((bits & (1L << 63)) != 0L) {
            throw new Iso8583HeaderParseException(
                    Iso8583HeaderParseException.Rule.SECONDARY_BITMAP_FORBIDDEN,
                    "secondary bitmap indicator is forbidden");
        }

        for (int de = 1; de <= 64; de++) {
            boolean set = (bits & (1L << (64 - de))) != 0L;
            if (set && !PROFILE_FIELDS.contains(de)) {
                throw new Iso8583HeaderParseException(
                        Iso8583HeaderParseException.Rule.UNSUPPORTED_FIELD,
                        "bitmap sets field not enabled by SIM-ISO87A-v1: DE" + de);
            }
        }

        return new Iso8583Header(mti, PrimaryBitmap.of(bits), HEADER_LENGTH);
    }

    private static void requirePrintableAscii(String header) {
        for (int i = 0; i < header.length(); i++) {
            char c = header.charAt(i);
            if (c < 0x20 || c > 0x7E) {
                throw new Iso8583HeaderParseException(
                        Iso8583HeaderParseException.Rule.NON_ASCII,
                        "header octet " + i + " is not printable ASCII: U+"
                                + String.format("%04X", (int) c));
            }
        }
    }

    private static void requireDigits(String mti) {
        for (int i = 0; i < mti.length(); i++) {
            char c = mti.charAt(i);
            if (c < '0' || c > '9') {
                throw new Iso8583HeaderParseException(
                        Iso8583HeaderParseException.Rule.INVALID_MTI,
                        "MTI must be four ASCII digits");
            }
        }
    }

    private static long parseBitmap(String text) {
        long bits = 0L;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            int nibble;
            if (c >= '0' && c <= '9') {
                nibble = c - '0';
            } else if (c >= 'A' && c <= 'F') {
                nibble = c - 'A' + 10;
            } else {
                throw new Iso8583HeaderParseException(
                        Iso8583HeaderParseException.Rule.INVALID_BITMAP,
                        "bitmap must be 16 uppercase hexadecimal ASCII characters");
            }
            bits = (bits << 4) | nibble;
        }
        return bits;
    }
}
