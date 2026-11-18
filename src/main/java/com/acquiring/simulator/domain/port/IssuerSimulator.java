package com.acquiring.simulator.domain.port;

import com.acquiring.simulator.domain.model.DownstreamReply;
import com.acquiring.simulator.domain.model.SimulatedDecisionRequest;

/** Deterministic downstream port; production connectivity is out of scope. */
public interface IssuerSimulator {

    DownstreamReply decide(SimulatedDecisionRequest request);
}
