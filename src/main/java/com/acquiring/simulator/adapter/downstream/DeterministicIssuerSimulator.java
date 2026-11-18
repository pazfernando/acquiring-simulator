package com.acquiring.simulator.adapter.downstream;

import com.acquiring.simulator.domain.model.DownstreamReply;
import com.acquiring.simulator.domain.model.IssuerDecision;
import com.acquiring.simulator.domain.model.SimulatedDecisionRequest;
import com.acquiring.simulator.domain.port.IssuerSimulator;
import java.time.Instant;
import org.springframework.stereotype.Component;

/** Deterministic, in-process simulated issuer/scheme response; no network calls. */
@Component
public class DeterministicIssuerSimulator implements IssuerSimulator {

    @Override
    public DownstreamReply decide(SimulatedDecisionRequest request) {
        Instant availableAt = request.dispatchedAt().plusMillis(25);
        return switch (request.credentialDecision()) {
            case APPROVE -> new DownstreamReply(
                    request.correlationId(), IssuerDecision.APPROVED, availableAt);
            case DECLINE -> new DownstreamReply(
                    request.correlationId(), IssuerDecision.DECLINED, availableAt);
            case TIMEOUT -> new DownstreamReply(
                    request.correlationId(),
                    IssuerDecision.NO_RESPONSE,
                    request.dispatchedAt().plus(request.responseTimeout()).plusMillis(1));
        };
    }
}
