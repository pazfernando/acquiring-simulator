package com.acquiring.simulator.protocol.iso8583;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Immutable canonical DE48 response extension.
 *
 * <p>{@code values} holds the accepted key=value pairs in the exact canonical
 * order: the echoed request keys (with optional {@code prior}), followed by
 * {@code actor,stage,result,reason,evidence,at}. Values are byte-for-byte as
 * received. Nothing is trimmed, normalized or re-ordered once the parser has
 * accepted the input.</p>
 *
 * <p>Scope: canonical response schema only. The P4 actor/stage/result decision
 * matrix, DE38 trusted-evidence binding, domain calendar/DE7/amount/currency
 * checks and any admission decision have no representation here.</p>
 *
 * @param values ordered immutable key=value pairs
 */
public record Iso8583De48Response(Map<String, String> values) {

    public Iso8583De48Response {
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
