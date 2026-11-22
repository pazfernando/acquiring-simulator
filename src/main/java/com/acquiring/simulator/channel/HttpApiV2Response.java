package com.acquiring.simulator.channel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * One decoded payload v2 octet-stream response, before any C4 interpretation.
 *
 * <p>Headers are retained as a multi-value map so duplicate evidence headers cannot
 * collapse before the C4 parser sees them. The record carries no authenticated sender:
 * transport identity is a separate platform fact and is never read from a response
 * header or body.</p>
 */
public record HttpApiV2Response(
        int statusCode,
        Map<String, List<String>> headers,
        byte[] body) {

    public HttpApiV2Response {
        Objects.requireNonNull(headers, "headers");
        Objects.requireNonNull(body, "body");
        Map<String, List<String>> copy = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
            copy.put(entry.getKey(),
                    Collections.unmodifiableList(new ArrayList<>(entry.getValue())));
        }
        headers = Collections.unmodifiableMap(copy);
        body = body.clone();
    }

    @Override
    public byte[] body() {
        return body.clone();
    }

    /** Never renders a header value or the body bytes. */
    @Override
    public String toString() {
        return "HttpApiV2Response[statusCode=" + statusCode
                + ", headerNames=" + headers.size()
                + ", bodyLength=" + body.length + "]";
    }
}
