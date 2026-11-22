package com.acquiring.simulator.channel;

/**
 * First violated internal HTTP transport rule, in the order the adapter evaluates it.
 *
 * <p>Every value is a stable, value-free label. It names the rule and at most a header or
 * field name; it never contains a body, base64 value, header value, identity claim or
 * payment result. Callers may map these to a sanitized HTTP 400/413/415 response; they are
 * not themselves a channel outcome.</p>
 */
public enum HttpApiV2TransportViolation {

    /** Raw API Gateway payload v2 event exceeds the transport guard bound. */
    EVENT_SIZE,

    /** Event is not a strict JSON object with the required payload v2 members. */
    EVENT_SCHEMA,

    /** Path or method is not the approved internal octet-stream route. */
    ROUTE,

    /** Body was not supplied with the platform base64 flag, or was not textual. */
    UNSUPPORTED_ENCODING,

    /** Body is not canonical padded RFC4648 standard base64. */
    BASE64_NONCANONICAL,

    /** Decoded octet-stream body is empty or exceeds the 2048-byte C2 bound. */
    BODY_SIZE,

    /** Content-Type is not exactly {@code application/octet-stream}. */
    UNSUPPORTED_MEDIA,

    /** {@code X-Simulator-Profile} is not exactly {@code SIM-ISO87A-v1}. */
    UNSUPPORTED_PROFILE,

    /** Content-Encoding or a recognised compressed container was presented. */
    COMPRESSION,

    /** A caller-supplied header tried to assert sender or principal identity. */
    IDENTITY_ASSERTION,

    /** Header appears more than once, including across case variants. */
    DUPLICATE_HEADER,

    /** C4 evidence header exceeds its encoded or decoded carriage bound. */
    CARRIER_SIZE,

    /** Total response headers exceed the 8192-byte C4 bound. */
    HEADERS_TOO_LARGE,

    /** Declared Content-Length does not equal the exact decoded body length. */
    CONTENT_LENGTH_MISMATCH
}
