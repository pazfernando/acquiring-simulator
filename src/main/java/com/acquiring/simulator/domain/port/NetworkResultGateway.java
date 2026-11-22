package com.acquiring.simulator.domain.port;

import com.acquiring.simulator.domain.event.CommittedVersionEvidence;
import com.acquiring.simulator.domain.model.AuthorizationRecord;
import com.acquiring.simulator.domain.model.AuthorizationState;
import com.acquiring.simulator.domain.model.DownstreamReply;
import com.acquiring.simulator.domain.model.Outcome;
import java.util.Objects;

/**
 * Narrow domain port for an explicit pre-issuer network rejection (observation contract O3,
 * transition {@code NETWORK_REJECTED} with {@code NETWORK} provenance).
 *
 * <p>This is the network counterpart of {@link ResultGateway}, deliberately narrower: it admits
 * only an authoritative, correlated {@code NETWORK_REJECTED} reply available after a durable
 * dispatch intent. Issuer {@code APPROVED}/{@code DECLINED} decisions stay with
 * {@link ResultGateway}; {@code NO_RESPONSE}/{@code MALFORMED} stay uncertainty. The implementation
 * must commit the conditioned business transition, the explicitly advanced committed journal
 * version, the immutable critical business-persist event, the EVENT head, dedupe metadata and any
 * slot reservation in ONE atomic transaction, then read the stored record back before reporting
 * success.
 *
 * <p><strong>Network provenance is not an issuer decision.</strong> A recorded rejection keeps
 * {@code NETWORK_REJECTED} with {@link Outcome#PROCESSOR_REJECTED} as the caller outcome (the
 * existing fixed channel mapping in {@code AuthorizationService}) and never fabricates an issuer
 * task, an issuer decision or an issuer decline. The port exposes that mapping only as derived,
 * record-backed knowledge; it does not define a new caller code or reason vocabulary.
 *
 * <p><strong>Recovered knowledge is not a send permission.</strong> {@link Status#RECOVERED} shows
 * that an ambiguous commit can be proven to have happened; it authorizes no resend, no reversal and
 * no legacy fallback. {@link Status#UNKNOWN} is genuinely uncertain: it is neither proof that no
 * rejection exists nor evidence of one. No non-success outcome may trigger {@code updateOutcome}, a
 * legacy event append, a retry, a resend or a reversal.
 */
public interface NetworkResultGateway {

    /** Outcome classification of exactly one explicit-network-rejection commit attempt. */
    enum Status {
        /** This invocation committed a new network rejection. */
        COMMITTED,
        /** The identical rejection was already stored; nothing was written a second time. */
        REPLAY,
        /** An ambiguous write was resolved by lookup as already committed; knowledge only. */
        RECOVERED,
        /** The stable identity exists with different content, or a competing outcome is retained. */
        CONTENT_CONFLICT,
        /** The transaction was rejected; nothing was committed by this attempt. */
        NOT_COMMITTED,
        /** State could not be established; UNAVAILABLE semantics, never proof of no-send. */
        UNKNOWN
    }

    /**
     * @param status          outcome classification
     * @param confirmedState  {@code NETWORK_REJECTED} read back from the stored record, or
     *                        {@code null} when no rejection was established
     * @param evidence        persisted committed-version evidence, or {@code null} when not read
     * @param detail          safe, non-payload diagnostic; never contains event content
     */
    record Decision(Status status, AuthorizationState confirmedState,
            CommittedVersionEvidence evidence, String detail) {

        public Decision {
            Objects.requireNonNull(status, "status");
        }

        /**
         * True only when an explicit network rejection was read back from persistence with its
         * recorded committed version. Replay and recovery confirm existence; they grant no
         * downstream action.
         */
        public boolean recorded() {
            return (status == Status.COMMITTED || status == Status.REPLAY
                            || status == Status.RECOVERED)
                    && confirmedState == AuthorizationState.NETWORK_REJECTED
                    && evidence != null
                    && evidence.committedVersionRecorded();
        }

        /**
         * Caller-visible outcome derived from recorded evidence only: the existing domain mapping
         * renders a committed network rejection as {@link Outcome#PROCESSOR_REJECTED}, never as an
         * issuer decline. Without recorded evidence the conservative answer is
         * {@link Outcome#NOT_CONFIRMED}.
         */
        public Outcome callerOutcome() {
            return recorded() ? Outcome.PROCESSOR_REJECTED : Outcome.NOT_CONFIRMED;
        }
    }

    /**
     * Perform exactly one atomic network-rejection commit for the durable dispatch {@code intent}.
     * The evidence must be the authenticated, correlated {@code NETWORK_REJECTED} reply and its
     * availability time governs the committed rejection time.
     */
    Decision recordNetworkRejection(AuthorizationRecord intent, DownstreamReply evidence);
}
