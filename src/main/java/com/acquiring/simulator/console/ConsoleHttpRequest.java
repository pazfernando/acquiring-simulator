package com.acquiring.simulator.console;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * One HTTP request as the public console browser would send it.
 *
 * <p>This is a transport view and nothing else. It carries no trusted principal, no
 * acquirer/merchant mandate, no registry fixture and no decision: the only identity
 * evidence is the caller-supplied {@code Authorization} header, which a separate seam
 * must verify before any business action.</p>
 *
 * <p>There is no relay and no loopback listener behind this type. It models the request
 * an HTTP API / CloudFront edge would hand to the console Lambda (channel contract C1
 * route {@code POST /console/v1/authorizations}), so the route can be exercised locally
 * without opening a socket and without any browser-held service secret.</p>
 */
public record ConsoleHttpRequest(
        String method,
        String path,
        Map<String, List<String>> headers,
        byte[] body,
        Map<String, List<String>> query) {

    public ConsoleHttpRequest {
        Objects.requireNonNull(method, "method");
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(headers, "headers");
        Map<String, List<String>> copy = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
            Objects.requireNonNull(entry.getKey(), "header name");
            copy.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        headers = Collections.unmodifiableMap(copy);
        Map<String, List<String>> queryCopy = new LinkedHashMap<>();
        if (query != null) {
            for (Map.Entry<String, List<String>> entry : query.entrySet()) {
                Objects.requireNonNull(entry.getKey(), "query name");
                queryCopy.put(entry.getKey(), List.copyOf(entry.getValue()));
            }
        }
        query = Collections.unmodifiableMap(queryCopy);
        body = body == null ? new byte[0] : body.clone();
    }

    /**
     * Convenience constructor for a request with no query parameters, so existing callers and the
     * POST submission path are unchanged.
     */
    public ConsoleHttpRequest(String method, String path, Map<String, List<String>> headers,
            byte[] body) {
        this(method, path, headers, body, Map.of());
    }

    @Override
    public byte[] body() {
        return body.clone();
    }

    /**
     * Every value supplied for one header name, matched case-insensitively as HTTP
     * requires. Duplicates are preserved rather than collapsed, so a caller cannot
     * smuggle a second credential past a single-value reader.
     */
    public List<String> headerValues(String name) {
        Objects.requireNonNull(name, "name");
        List<String> values = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(name)) {
                values.addAll(entry.getValue());
            }
        }
        return List.copyOf(values);
    }

    /**
     * Every value supplied for one query parameter. Query parameter names are
     * case-sensitive, so this is an exact match and duplicates are preserved.
     */
    public List<String> queryValues(String name) {
        Objects.requireNonNull(name, "name");
        List<String> values = query.get(name);
        return values == null ? List.of() : List.copyOf(values);
    }

    /** Never renders a header value or the body bytes. */
    @Override
    public String toString() {
        return "ConsoleHttpRequest[method=" + method
                + ", path=" + path
                + ", headerNames=" + headers.size()
                + ", bodyLength=" + body.length + "]";
    }
}
