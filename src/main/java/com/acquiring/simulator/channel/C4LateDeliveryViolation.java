package com.acquiring.simulator.channel;

/**
 * First violated C4 late-delivery envelope rule, in parser evaluation order.
 *
 * <p>Every value is a stable, value-free label. It names the rule and at most a field
 * name, never the raw payload, decoded JSON, base64 body, nested envelope, signature,
 * delivery receipt or a caller-supplied identifier. These labels are not an HTTP status,
 * a payment outcome or a business admission.</p>
 */
public enum C4LateDeliveryViolation {

    /** Raw late-delivery bytes are empty or exceed the 8192-byte contract bound. */
    LATE_ENVELOPE_SIZE,

    /** Bytes are not strict UTF-8, or contain CR/LF line folding. */
    LATE_ENVELOPE_ENCODING,

    /** The payload begins with a recognised compressed-container signature. */
    COMPRESSION,

    /** Closed four-key shape, version, receipt id, types or canonical form is violated. */
    LATE_ENVELOPE_SCHEMA,

    /** {@code responseBodyBase64} is not canonical padded RFC4648 standard base64. */
    RESPONSE_BODY_ENCODING,

    /** Decoded original response body is empty or exceeds the 2048-byte C2 bound. */
    RESPONSE_BODY_SIZE,

    /** Canonical inner C4 evidence envelope exceeds the 4096-byte bound. */
    EVIDENCE_SIZE,

    /** SHA-256 of the exact decoded original body does not equal the signed hash. */
    BODY_HASH_BINDING
}
