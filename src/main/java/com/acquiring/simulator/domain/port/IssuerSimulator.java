package com.acquiring.simulator.domain.port;

import com.acquiring.simulator.domain.model.DownstreamReply;
import com.acquiring.simulator.domain.model.SimulatedDecisionRequest;

/** Synthetic downstream boundary: local issuer or separate simulated network/issuer Lambdas; no live network connectivity. */
public interface IssuerSimulator {

    DownstreamReply decide(SimulatedDecisionRequest request);
}
