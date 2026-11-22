package com.acquiring.simulator.channel;

/**
 * Fixed, sanitized failure category for the one pure signed-P5/P6 field-binding step.
 *
 * <p>Every category names a class of disagreement only. It never carries a received value,
 * a raw payload, a signature, a key or a caller-supplied message.</p>
 */
public enum C4P6BindingViolation {

    /** A required input was absent or the exact persisted/received bytes were empty. */
    MISSING_INPUT,

    /** The verified result does not name the supplied signed attestation content/identity. */
    VERIFIED_BINDING,

    /** The current trusted environment is not the environment the proof was verified in. */
    ENVIRONMENT_BINDING,

    /** The current authenticated sender is not the verified DIRECT signer or RELAY principal. */
    PRINCIPAL_BINDING,

    /** The signed instruction hash is not the SHA-256 of the exact persisted 0100 octets. */
    INSTRUCTION_HASH_MISMATCH,

    /** The signed response hash is not the SHA-256 of the exact received 0110 octets. */
    RESPONSE_HASH_MISMATCH,

    /** The persisted original 0100 octets are not a strict ASCII 0100 body. */
    ORIGINAL_INSTRUCTION_INVALID,

    /** The exact received response octets are not a strict ASCII 0110 body. */
    RESPONSE_INVALID,

    /** A signed identity/context/generation/amount/currency/STAN/RRN/DE7/sent value
     * differs from the persisted original instruction projection. */
    SIGNED_INSTRUCTION_MISMATCH,

    /** A signed actor/stage/DE39/result/reason value differs from the parsed response. */
    SIGNED_RESULT_MISMATCH,

    /** The signed evidence identifier differs from the response evidence echo. */
    SIGNED_EVIDENCE_MISMATCH,

    /** The signed availability time is not the response {@code at} second projection. */
    SIGNED_AVAILABILITY_MISMATCH,

    /** The composed P6 domain phase rejected the response. */
    DOMAIN,

    /** The composed P6 P4 actor/stage/DE39/result/reason matrix rejected the response. */
    P4_MATRIX,

    /** The composed P6 instruction/context and exact DE38 binding rejected the response. */
    INSTRUCTION_BINDING
}
