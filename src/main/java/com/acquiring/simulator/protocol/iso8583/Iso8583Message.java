package com.acquiring.simulator.protocol.iso8583;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

/**
 * Immutable structural decode of one SIM-ISO87A-v1 message.
 *
 * <p>{@code fields} holds exactly the data elements announced by the primary
 * bitmap, keyed by ascending data element number, with the decoded ASCII value.
 * The record is a structural view only: it carries no amount, date, currency,
 * fixture, DE48 canonical schema, DE38 evidence or admission meaning.</p>
 *
 * @param mti          four ASCII digits (0100 or 0110)
 * @param bitmap       parsed primary bitmap, secondary indicator cleared
 * @param headerLength ASCII octets consumed by MTI + bitmap (always 20)
 * @param bodyLength   total ASCII octets in the decoded body
 * @param fields       ascending immutable data element values
 */
public record Iso8583Message(
        String mti,
        PrimaryBitmap bitmap,
        int headerLength,
        int bodyLength,
        Map<Integer, String> fields) {

    public Iso8583Message {
        if (mti == null || bitmap == null || fields == null) {
            throw new IllegalArgumentException("mti, bitmap and fields are required");
        }
        if (headerLength != Iso8583HeaderParser.HEADER_LENGTH) {
            throw new IllegalArgumentException(
                    "headerLength must be " + Iso8583HeaderParser.HEADER_LENGTH
                            + ": " + headerLength);
        }
        if (bodyLength < headerLength) {
            throw new IllegalArgumentException(
                    "bodyLength " + bodyLength + " is shorter than header " + headerLength);
        }
        fields = Collections.unmodifiableMap(new TreeMap<>(fields));
        if (!fields.keySet().equals(bitmap.setFields())) {
            throw new IllegalArgumentException(
                    "decoded fields " + fields.keySet()
                            + " do not match bitmap " + bitmap.setFields());
        }
    }

    /** @return decoded value, or {@code null} when the bitmap does not set it */
    public String field(int de) {
        return fields.get(de);
    }
}
