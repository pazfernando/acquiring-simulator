package com.acquiring.simulator.channel;

import java.security.PublicKey;
import java.security.interfaces.EdECPublicKey;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * One configured public key with its environment, principal, P5 role and acquirer mandate.
 *
 * <p>Only a public key is represented. Construction rejects non-Ed25519 material and
 * malformed identifiers; it never accepts or logs a private key. The key's role and
 * mandate are configuration facts resolved outside request data.</p>
 */
public record C4TrustedPrincipalKey(
        String environmentId,
        String principal,
        String keyId,
        C4PrincipalRole role,
        Set<String> acquirerMandateRefs,
        PublicKey publicKey) {

    public C4TrustedPrincipalKey {
        C4CarrierSchema.requireId("environmentId", environmentId, 64);
        C4CarrierSchema.requireId("principal", principal, 64);
        C4CarrierSchema.requireId("keyId", keyId, 64);
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(acquirerMandateRefs, "acquirerMandateRefs");
        Set<String> mandates = new LinkedHashSet<>();
        for (String ref : acquirerMandateRefs) {
            mandates.add(C4CarrierSchema.requireId("acquirerMandateRef", ref, 128));
        }
        acquirerMandateRefs = Set.copyOf(mandates);
        Objects.requireNonNull(publicKey, "publicKey");
        if (!(publicKey instanceof EdECPublicKey edKey)
                || !"Ed25519".equals(edKey.getParams().getName())) {
            throw new IllegalArgumentException("publicKey must be Ed25519");
        }
    }

    /** Never renders key material. */
    @Override
    public String toString() {
        return "C4TrustedPrincipalKey[environmentId=" + environmentId
                + ", principal=" + principal
                + ", keyId=" + keyId
                + ", role=" + role
                + ", mandateCount=" + acquirerMandateRefs.size() + "]";
    }
}
