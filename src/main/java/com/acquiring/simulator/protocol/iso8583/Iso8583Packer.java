package com.acquiring.simulator.protocol.iso8583;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Deterministic structural packer for one SIM-ISO87A-v1 body.
 *
 * <p>Input is an already-validated structural field map keyed by enabled data
 * element number. Output is exactly: MTI (4 ASCII digits) + primary bitmap
 * (16 uppercase hexadecimal ASCII octets, high bit first) + ascending data
 * elements. Every value is the caller's exact ASCII representation; this class
 * never pads, trims, rounds, truncates, normalizes aliases or rewrites DE48.
 * There is deliberately no typed amount/STAN overload, so no implicit
 * zero-formatting exists: callers supply the exact zero-formatted wire value.</p>
 *
 * <p>The packer reuses the profile's P1 declarations and presence matrices and
 * enforces them before emission: unsupported MTI, unknown/disabled data element,
 * missing required field, forbidden field present, absent value, fixed-width
 * mismatch, wrong alphabet, LL/LLL length outside bounds, and DE48 above the
 * structural 999-byte maximum. It does <em>not</em> apply the 826-byte exchange
 * admission budget, the canonical DE48 key=value schema, DE38 trusted-evidence
 * semantics, domain amount/date/currency rules, P4 matrix, instruction binding,
 * attestation, dispatch or any financial outcome. Those remain separate
 * downstream layers; a successful pack is structural representability only.</p>
 *
 * <p>DE38 is encoded only when the supplied map contains it. The packer never
 * mints an authorization reference or approval code, and it neither requires nor
 * forbids DE38 on a {@code 0110}: that conditional belongs to trusted binding.
 * A {@code 0100} still forbids DE38/DE39 through the same P1 matrix the strict
 * scanner applies.</p>
 */
public final class Iso8583Packer {

    /** Structural DE48 maximum from P1; the 826 exchange budget is admission. */
    public static final int STRUCTURAL_DE48_MAX_BYTES = Iso8583DataElement.DE48.maxLength();

    private static final int SECONDARY_BITMAP_MASK_DE = 1;

    private Iso8583Packer() {
    }

    /**
     * Encode one structural field map to canonical ASCII bytes.
     *
     * @param mti    four ASCII digits, one of the profile's selected MTIs
     * @param fields enabled data element number to exact ASCII value, in any map
     *               iteration order; duplicate or extra fields are impossible
     * @return complete ASCII body, deterministic for the same logical input
     * @throws Iso8583PackException on the first structural violation
     */
    public static String pack(String mti, Map<Integer, String> fields) {
        requireMti(mti);
        if (fields == null) {
            throw new Iso8583PackException(
                    Iso8583PackException.Rule.MISSING_VALUE, -1, "field map must not be null");
        }
        requireEnabledFields(fields.keySet());

        Map<Integer, String> ascending = new TreeMap<>(fields);
        requirePresence(mti, ascending.keySet());

        StringBuilder body = new StringBuilder(64);
        body.append(mti);
        body.append(bitmapHex(ascending.keySet()));
        for (Map.Entry<Integer, String> entry : ascending.entrySet()) {
            encode(body, entry.getKey(), entry.getValue());
        }
        return body.toString();
    }

    private static void requireMti(String mti) {
        if (mti == null || mti.length() != Iso8583HeaderParser.MTI_LENGTH) {
            throw new Iso8583PackException(
                    Iso8583PackException.Rule.UNSUPPORTED_MTI,
                    -1,
                    "MTI must be exactly four ASCII digits");
        }
        for (int i = 0; i < mti.length(); i++) {
            char c = mti.charAt(i);
            if (c < '0' || c > '9') {
                throw new Iso8583PackException(
                        Iso8583PackException.Rule.UNSUPPORTED_MTI,
                        -1,
                        "MTI must be exactly four ASCII digits");
            }
        }
        if (!Iso8583HeaderParser.SUPPORTED_MTIS.contains(mti)) {
            throw new Iso8583PackException(
                    Iso8583PackException.Rule.UNSUPPORTED_MTI,
                    -1,
                    "MTI is not selected by SIM-ISO87A-v1: " + mti);
        }
    }

    private static void requireEnabledFields(Set<Integer> dataElements) {
        for (Integer de : dataElements) {
            if (de == null) {
                throw new Iso8583PackException(
                        Iso8583PackException.Rule.UNKNOWN_FIELD,
                        -1,
                        "data element number must not be null");
            }
            if (de == SECONDARY_BITMAP_MASK_DE || !Iso8583HeaderParser.PROFILE_FIELDS.contains(de)) {
                throw new Iso8583PackException(
                        Iso8583PackException.Rule.UNKNOWN_FIELD,
                        de,
                        "data element is not enabled by SIM-ISO87A-v1: DE" + de);
            }
        }
    }

    private static void requirePresence(String mti, Set<Integer> present) {
        Set<Integer> required = switch (mti) {
            case "0100" -> Iso8583Scanner.REQUEST_REQUIRED_FIELDS;
            case "0110" -> Iso8583Scanner.RESPONSE_REQUIRED_FIELDS;
            default -> throw new IllegalStateException(
                    "MTI check accepted an unsupported MTI: " + mti);
        };
        Set<Integer> forbidden = switch (mti) {
            case "0100" -> Iso8583Scanner.REQUEST_FORBIDDEN_FIELDS;
            case "0110" -> Iso8583Scanner.RESPONSE_FORBIDDEN_FIELDS;
            default -> throw new IllegalStateException(
                    "MTI check accepted an unsupported MTI: " + mti);
        };

        for (int de = 1; de <= 64; de++) {
            if (required.contains(de) && !present.contains(de)) {
                throw new Iso8583PackException(
                        Iso8583PackException.Rule.MISSING_REQUIRED_FIELD,
                        de,
                        "MTI " + mti + " requires DE" + de);
            }
        }
        for (int de = 1; de <= 64; de++) {
            if (forbidden.contains(de) && present.contains(de)) {
                throw new Iso8583PackException(
                        Iso8583PackException.Rule.FORBIDDEN_FIELD_PRESENT,
                        de,
                        "MTI " + mti + " forbids DE" + de);
            }
        }
    }

    private static String bitmapHex(Set<Integer> present) {
        long bits = 0L;
        for (int de : present) {
            bits |= 1L << (64 - de);
        }
        if ((bits & (1L << 63)) != 0L) {
            throw new Iso8583PackException(
                    Iso8583PackException.Rule.UNKNOWN_FIELD,
                    SECONDARY_BITMAP_MASK_DE,
                    "secondary bitmap indicator must not be set");
        }
        return String.format(Locale.ROOT, "%016X", bits);
    }

    private static void encode(StringBuilder body, int de, String value) {
        if (value == null) {
            throw new Iso8583PackException(
                    Iso8583PackException.Rule.MISSING_VALUE,
                    de,
                    "value for DE" + de + " must not be null");
        }
        Iso8583DataElement element = Iso8583DataElement.forDe(de);
        switch (element.encoding()) {
            case FIXED_NUMERIC -> {
                requireFixedWidth(element, de, value);
                requireDigits(de, value);
                body.append(value);
            }
            case FIXED_UPPER_ALNUM -> {
                requireFixedWidth(element, de, value);
                requireUpperAlnum(de, value);
                body.append(value);
            }
            case LL_NUMERIC -> {
                requireVariableLength(element, de, value);
                requireDigits(de, value);
                body.append(String.format(Locale.ROOT, "%02d", value.length()));
                body.append(value);
            }
            case LLL_PRINTABLE_ASCII -> {
                requireVariableLength(element, de, value);
                requirePrintableAscii(de, value);
                body.append(String.format(Locale.ROOT, "%03d", value.length()));
                body.append(value);
            }
        }
    }

    private static void requireFixedWidth(
            Iso8583DataElement element, int de, String value) {
        if (value.length() != element.minLength()) {
            throw new Iso8583PackException(
                    Iso8583PackException.Rule.INVALID_WIDTH,
                    de,
                    "value for DE" + de + " is " + value.length()
                            + " octets, expected exactly " + element.minLength());
        }
    }

    private static void requireVariableLength(
            Iso8583DataElement element, int de, String value) {
        if (value.length() < element.minLength() || value.length() > element.maxLength()) {
            throw new Iso8583PackException(
                    Iso8583PackException.Rule.LENGTH_OUT_OF_RANGE,
                    de,
                    "value for DE" + de + " is " + value.length()
                            + " octets, expected " + element.minLength()
                            + ".." + element.maxLength());
        }
    }

    private static void requireDigits(int de, String value) {
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c < '0' || c > '9') {
                throw new Iso8583PackException(
                        Iso8583PackException.Rule.INVALID_NUMERIC,
                        de,
                        "value for DE" + de + " has a non-digit octet at offset " + i);
            }
        }
    }

    private static void requireUpperAlnum(int de, String value) {
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            boolean upper = c >= 'A' && c <= 'Z';
            boolean digit = c >= '0' && c <= '9';
            if (!upper && !digit) {
                throw new Iso8583PackException(
                        Iso8583PackException.Rule.INVALID_UPPER_ALNUM,
                        de,
                        "value for DE" + de
                                + " has a non-uppercase-alphanumeric octet at offset " + i);
            }
        }
    }

    private static void requirePrintableAscii(int de, String value) {
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c < 0x20 || c > 0x7E) {
                throw new Iso8583PackException(
                        Iso8583PackException.Rule.NON_PRINTABLE_ASCII,
                        de,
                        "value for DE" + de
                                + " has a non-printable ASCII octet at offset " + i);
            }
        }
    }
}
