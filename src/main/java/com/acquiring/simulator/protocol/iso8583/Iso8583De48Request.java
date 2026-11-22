package com.acquiring.simulator.protocol.iso8583;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Immutable canonical DE48 request extension.
 *
 * <p>{@code values} holds the accepted key=value pairs in the exact canonical
 * order declared by {@link Iso8583De48RequestParser#REQUEST_KEYS}, with the
 * values byte-for-byte as received. Nothing is trimmed, normalized or
 * re-ordered once the parser has accepted the input.</p>
 *
 * <p>Scope: canonical request schema only. Amount, currency, calendar date,
 * DE7 consistency, freshness, response capacity, evidence binding and dispatch
 * have no representation here.</p>
 *
 * @param values ordered immutable key=value pairs; {@code prior} is absent when
 *               it was not supplied
 */
public record Iso8583De48Request(Map<String, String> values) {

    public Iso8583De48Request {
        if (values == null) {
            throw new IllegalArgumentException("values are required");
        }
        values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    /** @return the received value, or {@code null} when the key was omitted */
    public String value(String key) {
        return values.get(key);
    }

    public boolean has(String key) {
        return values.containsKey(key);
    }
}
