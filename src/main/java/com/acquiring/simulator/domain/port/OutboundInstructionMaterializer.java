package com.acquiring.simulator.domain.port;

import com.acquiring.simulator.domain.model.AuthorizationRecord;
import java.time.Instant;

/**
 * Local seam that materializes the exact outbound synthetic {@code 0100} instruction for one
 * durable dispatch attempt.
 *
 * <p>The dispatch-intent adapter invokes this <em>inside</em> the POSSIBLE_SEND transaction, so the
 * exact original bytes are persisted atomically with the intent and therefore before any physical
 * send. A materialization failure must abort the intent: a send is never permitted without the
 * original instruction that a later signed response can be bound to.</p>
 *
 * <p>This is a simulator-local allocation seam, not the approved durable alias/STAN/RRN allocation
 * (T2). Implementations must derive their synthetic aliases deterministically from stable persisted
 * values so a repeated materialization for the same attempt produces identical bytes; they must not
 * choose an issuer outcome, authenticate, authorize, claim, dispatch, persist or send by themselves.
 * The production composition root remains inactive and does not construct an implementation.</p>
 */
@FunctionalInterface
public interface OutboundInstructionMaterializer {

    /**
     * @param claim                 the durable, undispatched claim that owns this send attempt
     * @param dispatchCorrelationId the processor-owned correlation of this single attempt
     * @param dispatchedAt          the same instant the dispatch-intent transition records
     * @return the exact non-empty {@code 0100} ASCII octets
     * @throws RuntimeException when the instruction cannot be materialized; the caller must then
     *                          commit no intent and send nothing
     */
    byte[] materialize(AuthorizationRecord claim, String dispatchCorrelationId, Instant dispatchedAt);
}
