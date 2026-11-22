package com.acquiring.simulator.channel;

/**
 * Sanitized internal HTTP transport rejection. The message names the first violated rule
 * and, when useful, the header or field name; it never contains body bytes, base64, a
 * header value, an identity claim or signature material.
 */
public final class HttpApiV2TransportException extends IllegalArgumentException {

    private final HttpApiV2TransportViolation violation;
    private final String field;

    private HttpApiV2TransportException(HttpApiV2TransportViolation violation, String field) {
        super(violation.name() + (field == null ? "" : " field=" + field));
        this.violation = violation;
        this.field = field;
    }

    public static HttpApiV2TransportException of(HttpApiV2TransportViolation violation) {
        return new HttpApiV2TransportException(violation, null);
    }

    public static HttpApiV2TransportException of(
            HttpApiV2TransportViolation violation, String field) {
        return new HttpApiV2TransportException(violation, field);
    }

    public HttpApiV2TransportViolation violation() {
        return violation;
    }

    /** Declared header or field name, or {@code null} when the rule is exchange-wide. */
    public String field() {
        return field;
    }
}
