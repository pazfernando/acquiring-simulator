package com.acquiring.simulator.channel;

/**
 * First violated C4 carrier rule, in the order the parser evaluates them.
 *
 * <p>Every value is a stable, value-free label: it names the rule and at most a field
 * name, never the received header, decoded envelope, body bytes, signature material or a
 * caller-supplied identifier. Callers may map these to a sanitized external error; they
 * are not themselves a channel response, an HTTP status or a payment outcome.</p>
 */
public enum C4CarrierViolation {

    /** Response was not a normal HTTP 200 carried exchange. */
    TRANSPORT_FAILURE,

    /** Transport facts report more than 8192 bytes of response headers. */
    HEADERS_TOO_LARGE,

    /** Decoded octet-stream body is empty or exceeds the 2048-byte C2 bound. */
    BODY_SIZE,

    /** No {@code X-Simulator-Evidence} header value reached the parser. */
    MISSING_PROOF,

    /** More than one evidence header value, or a transport-coalesced comma value. */
    DUPLICATE_HEADER,

    /** Evidence header value contains CR/LF or transport line folding. */
    FOLDED_HEADER,

    /** Encoded length exceeds 5464 characters or decoded length exceeds 4096 bytes. */
    CARRIER_SIZE,

    /** Value is not canonical padded RFC4648 standard base64. */
    BASE64_NONCANONICAL,

    /** Closed envelope or attestation shape, key set, type or value bound is violated. */
    CARRIER_SCHEMA,

    /** Outer {@code keyId} does not equal the signed attestation {@code keyId}. */
    KEY_BINDING,

    /** {@code responseSha256} does not equal SHA-256 of the exact received body bytes. */
    HASH_BINDING,

    /** Authenticated transport sender does not match the DIRECT or RELAY binding. */
    AUTHENTICATED_SENDER,

    /** RELAY object hash, principal or time binding does not match the envelope. */
    RELAY_BINDING
}
