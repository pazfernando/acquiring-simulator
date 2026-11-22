package com.acquiring.simulator.protocol.iso8583;

import java.util.Objects;

/**
 * Validated structural/domain envelope of one SIM-ISO87A-v1 {@code 0110}
 * response after the ordered codec pipeline has accepted every applicable
 * phase.
 *
 * <p>It carries only what the pipeline proved: the structurally scanned
 * message, the canonical DE48 response extension, the domain
 * amount/currency/Gregorian projection, P4 matrix eligibility and the
 * equality-only original-instruction/DE38 binding. It is deliberately
 * <em>not</em> an authentication, attestation, admission, business-conflict or
 * financial-outcome record, and it exposes no verified/accepted/authorized
 * flag.</p>
 *
 * <p>There is intentionally no freshness or 826-byte budget component: those
 * belong to a NEW request, never to a response or to a historical instruction
 * read. Authentication, attestation signature/evidence resolution, duplicate
 * and business-conflict admission, response construction, C4 carriage, HTTP
 * and runtime wiring remain separate downstream seams.</p>
 *
 * @param message           structurally scanned {@code 0110} message
 * @param extension         accepted canonical DE48 response extension
 * @param domain            validated domain projection
 * @param p4                P4 matrix eligibility for the claimed tuple
 * @param instructionBinding equality-only original-instruction/DE38 binding
 */
public record Iso8583ResponseValidation(
        Iso8583Message message,
        Iso8583De48Response extension,
        Iso8583DomainDecision domain,
        Iso8583P4MatrixDecision p4,
        Iso8583InstructionBindingDecision instructionBinding) {

    public Iso8583ResponseValidation {
        Objects.requireNonNull(message, "scanned message is required");
        Objects.requireNonNull(extension, "canonical response extension is required");
        Objects.requireNonNull(domain, "domain projection is required");
        Objects.requireNonNull(p4, "P4 matrix decision is required");
        Objects.requireNonNull(instructionBinding, "instruction binding is required");
        if (!Iso8583ResponseValidationPipeline.RESPONSE_MTI.equals(message.mti())) {
            throw new IllegalArgumentException(
                    "response validation envelope only holds MTI "
                            + Iso8583ResponseValidationPipeline.RESPONSE_MTI);
        }
    }
}
