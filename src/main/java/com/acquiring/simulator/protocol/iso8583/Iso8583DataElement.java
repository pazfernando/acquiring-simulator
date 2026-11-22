package com.acquiring.simulator.protocol.iso8583;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Structural encoding declaration for every data element enabled by
 * SIM-ISO87A-v1 (profile table P1).
 *
 * <p>This enum carries framing and alphabet only: width/bounds and the
 * fixed/LL/LLL encoding. It says nothing about presence, which depends on the
 * MTI and is applied by {@link Iso8583Scanner}, and nothing about DE48 canonical
 * key=value schema, DE38 trusted-evidence semantics, amounts, dates, currencies,
 * fixtures or admission.</p>
 */
public enum Iso8583DataElement {

    DE3(3, Encoding.FIXED_NUMERIC, 6, 6),
    DE4(4, Encoding.FIXED_NUMERIC, 12, 12),
    DE7(7, Encoding.FIXED_NUMERIC, 10, 10),
    DE11(11, Encoding.FIXED_NUMERIC, 6, 6),
    DE22(22, Encoding.FIXED_NUMERIC, 3, 3),
    DE25(25, Encoding.FIXED_NUMERIC, 2, 2),
    DE32(32, Encoding.LL_NUMERIC, 1, 11),
    DE37(37, Encoding.FIXED_UPPER_ALNUM, 12, 12),
    DE38(38, Encoding.FIXED_UPPER_ALNUM, 6, 6),
    DE39(39, Encoding.FIXED_NUMERIC, 2, 2),
    DE41(41, Encoding.FIXED_UPPER_ALNUM, 8, 8),
    DE42(42, Encoding.FIXED_UPPER_ALNUM, 15, 15),
    DE48(48, Encoding.LLL_PRINTABLE_ASCII, 1, 999),
    DE49(49, Encoding.FIXED_NUMERIC, 3, 3);

    /** The four P1 encodings; no other encoding exists in this profile. */
    public enum Encoding {
        FIXED_NUMERIC,
        FIXED_UPPER_ALNUM,
        LL_NUMERIC,
        LLL_PRINTABLE_ASCII
    }

    private static final Map<Integer, Iso8583DataElement> BY_DATA_ELEMENT;

    static {
        Map<Integer, Iso8583DataElement> byDataElement = new java.util.LinkedHashMap<>();
        for (Iso8583DataElement element : values()) {
            byDataElement.put(element.de, element);
        }
        BY_DATA_ELEMENT = Collections.unmodifiableMap(byDataElement);
    }

    private final int de;
    private final Encoding encoding;
    private final int minLength;
    private final int maxLength;

    Iso8583DataElement(int de, Encoding encoding, int minLength, int maxLength) {
        this.de = de;
        this.encoding = encoding;
        this.minLength = minLength;
        this.maxLength = maxLength;
    }

    public int de() {
        return de;
    }

    public Encoding encoding() {
        return encoding;
    }

    /** Exact width for fixed encodings; smallest accepted length otherwise. */
    public int minLength() {
        return minLength;
    }

    /** Exact width for fixed encodings; largest accepted length otherwise. */
    public int maxLength() {
        return maxLength;
    }

    /** Ascending data element numbers enabled by the profile. */
    public static Set<Integer> enabledFields() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(BY_DATA_ELEMENT.keySet()));
    }

    /**
     * @throws IllegalArgumentException when the data element is disabled by
     *         the profile; callers must reject it at bitmap level first
     */
    public static Iso8583DataElement forDe(int de) {
        Iso8583DataElement element = BY_DATA_ELEMENT.get(de);
        if (element == null) {
            throw new IllegalArgumentException(
                    "data element is not enabled by SIM-ISO87A-v1: DE" + de);
        }
        return element;
    }

    /**
     * Read this element at the cursor.
     *
     * @throws Iso8583FieldException on any framing, bound or alphabet violation
     */
    public String read(Iso8583FieldReader reader) {
        return switch (encoding) {
            case FIXED_NUMERIC -> reader.readFixedDigits(minLength);
            case FIXED_UPPER_ALNUM -> reader.readFixedUpperAlnum(minLength);
            case LL_NUMERIC -> reader.readLlDigits(minLength, maxLength);
            case LLL_PRINTABLE_ASCII -> reader.readLllPrintableAscii(minLength, maxLength);
        };
    }
}
