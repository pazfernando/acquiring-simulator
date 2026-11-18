package com.acquiring.simulator;

import com.acquiring.simulator.domain.model.DownstreamReply;
import com.acquiring.simulator.domain.model.IssuerDecision;
import com.acquiring.simulator.domain.model.SimulatedDecisionRequest;
import com.acquiring.simulator.domain.port.IssuerSimulator;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

/** Test double for the deterministic downstream simulator. */
public final class StubIssuerSimulator implements IssuerSimulator {

    private final AtomicInteger dispatches = new AtomicInteger();
    private volatile Function<SimulatedDecisionRequest, DownstreamReply> behavior;
    private volatile CountDownLatch entered;
    private volatile CountDownLatch release;

    public void reset() {
        dispatches.set(0);
        behavior = null;
        entered = null;
        release = null;
    }

    public int dispatchCount() {
        return dispatches.get();
    }

    public void behaveAs(Function<SimulatedDecisionRequest, DownstreamReply> behavior) {
        this.behavior = behavior;
    }

    public void gate(CountDownLatch entered, CountDownLatch release) {
        this.entered = entered;
        this.release = release;
    }

    @Override
    public DownstreamReply decide(SimulatedDecisionRequest request) {
        dispatches.incrementAndGet();
        CountDownLatch enteredLatch = entered;
        if (enteredLatch != null) {
            enteredLatch.countDown();
        }
        CountDownLatch releaseLatch = release;
        if (releaseLatch != null) {
            try {
                releaseLatch.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        }
        Function<SimulatedDecisionRequest, DownstreamReply> current = behavior;
        if (current != null) {
            return current.apply(request);
        }
        Instant at = request.dispatchedAt().plusMillis(25);
        return switch (request.credentialDecision()) {
            case APPROVE -> new DownstreamReply(request.correlationId(), IssuerDecision.APPROVED, at);
            case DECLINE -> new DownstreamReply(request.correlationId(), IssuerDecision.DECLINED, at);
            case TIMEOUT -> new DownstreamReply(request.correlationId(), IssuerDecision.NO_RESPONSE,
                    request.dispatchedAt().plus(request.responseTimeout()).plusMillis(1));
        };
    }
}
