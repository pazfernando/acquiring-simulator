package com.acquiring.simulator.channel;

/**
 * Safe external error codes for one C1 channel submission.
 *
 * <p>These are the C3-enumerated codes that apply before business admission.
 * This slice implements only the two codes reachable without HTTP transport,
 * authentication or history: malformed/schema/type/literal/reference input and
 * an oversized body. {@code UNSUPPORTED_MEDIA}, {@code PAYLOAD_CONFLICT},
 * {@code INVALID_REFERENCE} conflicts and authentication codes belong to later
 * HTTP, registry and history seams respectively and are deliberately absent
 * here rather than defaulted.</p>
 */
public enum ChannelErrorCode {

    /** Malformed JSON, schema, type, literal, reference or amount-profile input. */
    INVALID_INPUT,

    /** Body above the approved C1 4096-byte limit. */
    BODY_TOO_LARGE
}
