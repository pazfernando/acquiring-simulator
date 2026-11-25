package com.acquiring.simulator.console;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * One HTTP response the console route returns to the browser.
 *
 * <p>The status is chosen by the route from the closed C3 precedence table or, for the
 * few non-C3 cases this slice documents, from the transport rule that applies. This type
 * performs no transport I/O and never renders a body in {@link #toString()}.</p>
 */
public record ConsoleHttpResponse(
        int status,
        Map<String, List<String>> headers,
        byte[] body) {

    private static final String CACHE_CONTROL = "Cache-Control";
    private static final String NO_STORE = "no-store";

    public ConsoleHttpResponse {
        if (status < 100 || status > 599) {
            throw new IllegalArgumentException("status is outside the HTTP range");
        }
        Objects.requireNonNull(headers, "headers");
        Map<String, List<String>> copy = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
            copy.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        headers = Collections.unmodifiableMap(copy);
        body = body == null ? new byte[0] : body.clone();
    }

    @Override
    public byte[] body() {
        return body.clone();
    }

    /**
     * JSON body with {@code Content-Type: application/json}. No cache directive is
     * omitted: a payment or denial response is never cacheable.
     */
    public static ConsoleHttpResponse json(int status, String json) {
        Objects.requireNonNull(json, "json");
        Map<String, List<String>> headers = new LinkedHashMap<>();
        headers.put("Content-Type", List.of("application/json"));
        headers.put(CACHE_CONTROL, List.of(NO_STORE));
        return new ConsoleHttpResponse(status, headers,
                json.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Empty body with no content type. Used only where the pinned C3 envelope cannot
     * express the outcome without inventing a value (see the route's documentation).
     * No {@code Retry-After} is ever emitted.
     */
    public static ConsoleHttpResponse empty(int status) {
        Map<String, List<String>> headers = new LinkedHashMap<>();
        headers.put(CACHE_CONTROL, List.of(NO_STORE));
        return new ConsoleHttpResponse(status, headers, new byte[0]);
    }

    /** Never renders a header value or the body bytes. */
    @Override
    public String toString() {
        return "ConsoleHttpResponse[status=" + status
                + ", headerNames=" + new ArrayList<>(headers.keySet())
                + ", bodyLength=" + body.length + "]";
    }
}
