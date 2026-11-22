package com.acquiring.simulator.protocol.iso8583;

import java.util.Objects;

/**
 * Syntactic P4 matrix eligibility for one claimed response tuple.
 *
 * <p>Construction means only that the actor, stage, DE39, result and reason
 * match an approved P4 row. The record deliberately carries no approval,
 * provenance, evidence, dispatch or admission flag: the actor text is a claim,
 * not authenticated truth, and every trusted-evidence, DE38, instruction
 * binding and business-admission decision remains a later layer.</p>
 *
 * @param actor claimed producing decision actor
 * @param stage claimed verified dispatch stage
 * @param de39 physical DE39 response code supplied by the caller
 * @param result claimed business result
 * @param reason claimed result reason
 */
public record Iso8583P4MatrixDecision(
        String actor, String stage, String de39, String result, String reason) {

    public Iso8583P4MatrixDecision {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(stage, "stage");
        Objects.requireNonNull(de39, "de39");
        Objects.requireNonNull(result, "result");
        Objects.requireNonNull(reason, "reason");
    }
}
