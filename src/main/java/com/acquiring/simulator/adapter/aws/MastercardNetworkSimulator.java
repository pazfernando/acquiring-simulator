package com.acquiring.simulator.adapter.aws;

import com.acquiring.simulator.domain.model.*;
import com.acquiring.simulator.domain.port.IssuerSimulator;

/** Synthetic fault profile, NOT a Mastercard wire protocol or certification claim. */
public final class MastercardNetworkSimulator implements IssuerSimulator {
    public enum Fault { NONE, REJECT_BEFORE_ISSUER, UNAVAILABLE, DROP_RESPONSE, MALFORMED_RESPONSE, MISMATCHED_RESPONSE, LATE_RESPONSE }
    private final IssuerSimulator issuer;
    private final Fault fault;
    public MastercardNetworkSimulator(IssuerSimulator issuer, Fault fault) { this.issuer = issuer; this.fault = fault; }
    @Override public DownstreamReply decide(SimulatedDecisionRequest r) {
        if (fault == Fault.REJECT_BEFORE_ISSUER) {
            return new DownstreamReply(r.correlationId(), IssuerDecision.NETWORK_REJECTED, r.dispatchedAt());
        }
        if (fault == Fault.UNAVAILABLE) return LambdaDownstream.unknown(r);
        DownstreamReply truth = issuer.decide(r); // independent issuer executes even when network drops its reply
        return switch (fault) {
            case DROP_RESPONSE -> LambdaDownstream.unknown(r);
            case MALFORMED_RESPONSE -> new DownstreamReply(r.correlationId(), IssuerDecision.MALFORMED, r.dispatchedAt());
            case MISMATCHED_RESPONSE -> new DownstreamReply("UNRELATED", truth.decision(), truth.availableAt());
            case LATE_RESPONSE -> new DownstreamReply(truth.correlationId(), truth.decision(),
                    r.dispatchedAt().plus(r.responseTimeout()).plusMillis(1));
            default -> truth;
        };
    }
}
