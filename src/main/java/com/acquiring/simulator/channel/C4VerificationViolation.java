package com.acquiring.simulator.channel;

/**
 * First failed C4 cryptographic/binding rule, in verifier evaluation order.
 *
 * <p>Every value is a stable, value-free label. It names the rule and at most a field, never
 * the received signature, public key, body, attestation values or caller identity. A
 * rejection is not an HTTP status, a payment outcome or a business admission.</p>
 */
public enum C4VerificationViolation {

    /**
     * Relay object is absent, its principal is not the fixed NETWORK-SIM literal, or its
     * receipt/time/body-hash/inner-attestation-hash binding disagrees with the envelope.
     */
    RELAY_BINDING,

    /** Candidate body hash does not equal the signed {@code responseSha256}. */
    HASH_BINDING,

    /** Candidate canonical-attestation hash does not equal the attestation bytes. */
    ATTESTATION_HASH_BINDING,

    /** Authenticated transport environment does not equal the signed environment. */
    ENVIRONMENT_BINDING,

    /** Outer key, signed key, registry key or registry principal disagree. */
    KEY_BINDING,

    /** No configured key resolves for (environment, signing principal, key id). */
    UNKNOWN_KEY,

    /** Authenticated transport sender does not equal the signed signing principal. */
    AUTHENTICATED_SENDER,

    /** Role is not permitted to sign this P4 actor/stage/DE39/result/reason row. */
    ROLE_NOT_PERMITTED,

    /** Configured acquirer mandate does not cover the signed acquirer reference. */
    MANDATE_BINDING,

    /**
     * A late-delivery transport sender that is neither the DIRECT signer nor the RELAY
     * principal has no explicit scoped diagnostic-forwarding permission. Fail closed.
     */
    DIAGNOSTIC_FORWARD_PERMISSION,

    /** Ed25519 verification rejected the signature bytes. */
    BAD_SIGNATURE,

    /** The runtime cannot provide Ed25519 verification; fail closed. */
    SIGNATURE_UNAVAILABLE
}
