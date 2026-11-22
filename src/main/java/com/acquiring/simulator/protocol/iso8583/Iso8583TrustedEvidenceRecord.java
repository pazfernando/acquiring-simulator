package com.acquiring.simulator.protocol.iso8583;

import java.util.Objects;

/**
 * Minimal trusted-evidence projection consumed by the instruction-binding
 * slice.
 *
 * <p>The caller must supply these two values from an already-verified trusted
 * record (a verified attestation envelope), not from response text, not from a
 * caller-asserted boolean and not from a re-encoded request. This type is a
 * seam: it performs no signature, principal, mandate or admission check and
 * cannot claim that any of them happened.</p>
 *
 * <p>{@code instructionSha256} is the evidence claim about the exact original
 * {@code 0100} octets. {@code authorizationReference} is the trusted assertion
 * about the exact DE38 issuer reference, or {@code null} when the trusted
 * record asserts no reference. A null assertion is not a minted code: it means
 * the response must carry no DE38.</p>
 *
 * <p>The rest of the P5 envelope — environment, producer/relay principals,
 * evidence identifier, authorization/transaction/correlation/attempt repeats,
 * context, generation, amount, currency, STAN, RRN, DE7, sent, actor, stage,
 * DE39, result, reason, availableAt, issuer attestation and transport receipt —
 * is deliberately not represented here. Those fields either belong to the
 * already-run P4/domain layers or are explicitly deferred to the later
 * attestation/carriage and business-admission slices.</p>
 *
 * @param instructionSha256    trusted claim about the original-wire hash
 * @param authorizationReference trusted DE38 assertion, or {@code null}
 */
public record Iso8583TrustedEvidenceRecord(
        String instructionSha256, String authorizationReference) {

    public Iso8583TrustedEvidenceRecord {
        Objects.requireNonNull(instructionSha256, "instructionSha256");
    }

    /** @return {@code true} when the trusted record asserts an exact DE38 value */
    public boolean hasAuthorizationReference() {
        return authorizationReference != null;
    }
}
