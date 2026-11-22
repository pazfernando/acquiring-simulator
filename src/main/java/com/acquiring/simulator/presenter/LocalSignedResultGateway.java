package com.acquiring.simulator.presenter;

import com.acquiring.simulator.adapter.aws.SignedEvidenceReceiptGateway;
import com.acquiring.simulator.domain.model.AuthorizationRecord;
import com.acquiring.simulator.domain.model.DownstreamReply;
import com.acquiring.simulator.domain.model.IssuerDecision;
import com.acquiring.simulator.domain.port.ResultGateway;
import java.util.Objects;

/**
 * Local-demo bridge from the domain result port to the real signed-evidence gateway.
 *
 * <p>The production {@link ResultGateway} contract carries only a {@link DownstreamReply}; the
 * signed carrier does not yet travel on that port. This adapter is the explicit local composition
 * seam that carries the simulated bank's already-produced carrier to
 * {@link SignedEvidenceReceiptGateway#admit}. It refuses to submit when the bank has no chain or
 * when the chain does not describe the same durable intent/correlation, and it never falls back to
 * an unsigned result, a bypass or a fabricated outcome.</p>
 */
public final class LocalSignedResultGateway implements ResultGateway {

    private final LocalSignedIssuerSimulator bank;
    private final SignedEvidenceReceiptGateway gateway;

    public LocalSignedResultGateway(LocalSignedIssuerSimulator bank,
            SignedEvidenceReceiptGateway gateway) {
        this.bank = Objects.requireNonNull(bank, "bank");
        this.gateway = Objects.requireNonNull(gateway, "gateway");
    }

    @Override
    public Decision recordResult(AuthorizationRecord intent, DownstreamReply evidence) {
        LocalSignedIssuerSimulator.Chain chain = bank.chain();
        if (chain == null
                || evidence == null
                || evidence.decision() != IssuerDecision.APPROVED
                || !chain.signed().authorizationId().equals(intent.id())
                || !chain.signed().correlationId().equals(evidence.correlationId())) {
            return new Decision(Status.NOT_COMMITTED, null, null,
                    "signed evidence does not correlate to the durable intent");
        }
        SignedEvidenceReceiptGateway.Decision admitted = gateway.admit(chain.candidate(),
                chain.signed(), chain.context(), chain.registry(), chain.response());
        return new Decision(switch (admitted.status()) {
            case COMMITTED -> Status.COMMITTED;
            case REPLAY -> Status.REPLAY;
            case RECOVERED -> Status.RECOVERED;
            case CONTENT_CONFLICT -> Status.CONTENT_CONFLICT;
            case REFUSED, NOT_COMMITTED -> Status.NOT_COMMITTED;
            case UNKNOWN -> Status.UNKNOWN;
        }, admitted.confirmedState(), admitted.evidence(), admitted.detail());
    }
}
