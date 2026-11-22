package com.acquiring.simulator.protocol.iso8583;

/**
 * Parsed header of a SIM-ISO87A-v1 body.
 *
 * @param mti    four ASCII digits
 * @param bitmap primary bitmap, secondary indicator already cleared
 * @param headerLength number of ASCII octets consumed (always 20)
 */
public record Iso8583Header(String mti, PrimaryBitmap bitmap, int headerLength) {

    public Iso8583Header {
        if (mti == null || bitmap == null) {
            throw new IllegalArgumentException("mti and bitmap are required");
        }
    }
}
