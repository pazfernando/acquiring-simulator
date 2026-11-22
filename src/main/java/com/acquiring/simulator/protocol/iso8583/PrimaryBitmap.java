package com.acquiring.simulator.protocol.iso8583;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Immutable 64-bit primary bitmap. Bit 1 is the secondary-bitmap indicator and is
 * never set in an accepted header.
 */
public final class PrimaryBitmap {

    private final long bits;

    private PrimaryBitmap(long bits) {
        this.bits = bits;
    }

    static PrimaryBitmap of(long bits) {
        return new PrimaryBitmap(bits);
    }

    /**
     * @param de data element number 1..64
     * @return true when the corresponding bit is set
     */
    public boolean isSet(int de) {
        if (de < 1 || de > 64) {
            throw new IllegalArgumentException("data element out of primary bitmap range: " + de);
        }
        return (bits & (1L << (64 - de))) != 0L;
    }

    /** Ascending data element numbers whose bits are set. */
    public Set<Integer> setFields() {
        Set<Integer> fields = new LinkedHashSet<>();
        for (int de = 1; de <= 64; de++) {
            if (isSet(de)) {
                fields.add(de);
            }
        }
        return Collections.unmodifiableSet(fields);
    }

    long bits() {
        return bits;
    }
}
