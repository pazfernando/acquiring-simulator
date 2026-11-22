package com.acquiring.simulator.channel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable transport-layer facts for one carried 0110 exchange, before any C4 parsing.
 *
 * <p>This is the seam between HTTP transport and the carrier parser: the adapter supplies
 * the numeric status, the exact decoded octet-stream body, the authenticated sender taken
 * from the service transport context (never an X header or body assertion), the measured
 * total response-header size and the header values <em>as a multi-value map</em>. Header
 * values stay a list so duplicate evidence headers cannot collapse into one before the
 * parser sees them. Header names are matched case-insensitively by the parser.</p>
 *
 * <p>No HTTP endpoint, browser surface, CORS exposure or logging is implemented here.</p>
 */
public record C4TransportResponse(
        int statusCode,
        Map<String, List<String>> headers,
        byte[] body,
        String authenticatedSender,
        int totalHeaderBytes) {

    public C4TransportResponse {
        Objects.requireNonNull(headers, "headers");
        Objects.requireNonNull(body, "body");
        if (totalHeaderBytes < 0) {
            throw new IllegalArgumentException("totalHeaderBytes must not be negative");
        }
        Map<String, List<String>> copy = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
            copy.put(entry.getKey(),
                    Collections.unmodifiableList(new ArrayList<>(entry.getValue())));
        }
        headers = Map.copyOf(copy);
        body = body.clone();
    }

    @Override
    public byte[] body() {
        return body.clone();
    }

    /** Never renders the body or header values. */
    @Override
    public String toString() {
        return "C4TransportResponse[statusCode=" + statusCode
                + ", headerNames=" + headers.size()
                + ", bodyLength=" + body.length
                + ", totalHeaderBytes=" + totalHeaderBytes
                + ", senderPresent=" + (authenticatedSender != null) + "]";
    }
}
