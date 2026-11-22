package com.acquiring.simulator.channel;

/**
 * Provisioned P5 service principal role.
 *
 * <p>Roles are scoped by environment and acquirer mandate in the key registry. They are
 * never supplied by a request, header, body assertion or browser flag. A role alone does
 * not grant signing authority: only the P4 rows in {@link C4EvidenceVerifier} map a role
 * to a permitted actor, stage, DE39, result and reason.</p>
 */
public enum C4PrincipalRole {

    /** May attest issuer decisions only (P4 {@code ISSUER_REPLIED}). */
    ISSUER_SIM,

    /** May attest processor rejection and possible-issuer uncertainty. */
    PROCESSOR_AUTH,

    /**
     * May attest network rejection and possible-issuer uncertainty, and may countersign a
     * relayed issuer attestation without replacing the issuer signature.
     */
    NETWORK_SIM,

    /** Channel authentication role; has no evidence-signing authority. */
    ACL_AUTH
}
