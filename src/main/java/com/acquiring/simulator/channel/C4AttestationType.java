package com.acquiring.simulator.channel;

/**
 * Specific P4 attestation meaning established by an authenticated, verified DIRECT signer.
 *
 * <p>This is the evidence classification, not a payment outcome, not a processor receipt
 * and not business admission. Only {@link C4EvidenceVerifier} produces it, and only after
 * the signature, key, role, sender, environment, mandate and hash bindings hold.</p>
 */
public enum C4AttestationType {

    /** Issuer-approved decision at {@code ISSUER_REPLIED}, DE39 {@code 00}. */
    ISSUER_APPROVAL,

    /** Registered deliberate issuer decline at {@code ISSUER_REPLIED}, DE39 {@code 05}. */
    ISSUER_DECLINE,

    /** Processor validation rejection before network dispatch, DE39 {@code 30}. */
    PROCESSOR_REJECTION_BEFORE_NETWORK,

    /** Network format/validation rejection before issuer dispatch, DE39 {@code 30}. */
    NETWORK_REJECTION_BEFORE_ISSUER,

    /** Positive network evidence that the issuer was not forwarded, DE39 {@code 91}. */
    NETWORK_NON_FORWARDING_REJECTION,

    /** Possible issuer send without a confirmed result, DE39 {@code 91}. */
    POSSIBLE_ISSUER_UNCERTAINTY
}
