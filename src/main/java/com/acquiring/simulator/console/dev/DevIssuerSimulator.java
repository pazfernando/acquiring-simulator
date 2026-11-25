package com.acquiring.simulator.console.dev;

import com.acquiring.simulator.domain.model.DownstreamReply;
import com.acquiring.simulator.domain.model.IssuerDecision;
import com.acquiring.simulator.domain.model.SimulatedDecisionRequest;
import com.acquiring.simulator.domain.port.IssuerSimulator;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Deterministic downstream simulator for the local console harness.
 *
 * <p>It mirrors the repository's existing test double exactly: the already-resolved synthetic
 * {@code CredentialDecision} selects APPROVED, DECLINED or NO_RESPONSE, and availability is
 * derived from the supplied dispatch instant and timeout. It contacts no issuer, card network or
 * AWS service, reads no real credential, and invents no financial rule: the decision was already
 * made by the existing simulator configuration before this type is called.</p>
 */
public final class DevIssuerSimulator implements IssuerSimulator {

    private final AtomicInteger dispatches = new AtomicInteger();

    @Override
    public DownstreamReply decide(SimulatedDecisionRequest request) {
        dispatches.incrementAndGet();
        Instant at = request.dispatchedAt().plusMillis(25);
        return switch (request.credentialDecision()) {
            case APPROVE -> new DownstreamReply(
                    request.correlationId(), IssuerDecision.APPROVED, at);
            case DECLINE -> new DownstreamReply(
                    request.correlationId(), IssuerDecision.DECLINED, at);
            case TIMEOUT -> new DownstreamReply(
                    request.correlationId(), IssuerDecision.NO_RESPONSE,
                    request.dispatchedAt().plus(request.responseTimeout()).plusMillis(1));
        };
    }

    /** Number of synthetic downstream calls this process made. Observation only. */
    public int dispatchCount() {
        return dispatches.get();
    }
}
