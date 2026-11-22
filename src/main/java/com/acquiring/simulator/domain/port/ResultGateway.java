package com.acquiring.simulator.domain.port;

import com.acquiring.simulator.domain.event.CommittedVersionEvidence;
import com.acquiring.simulator.domain.model.AuthorizationRecord;
import com.acquiring.simulator.domain.model.AuthorizationState;
import com.acquiring.simulator.domain.model.DownstreamReply;
import java.util.Objects;

/**
 * Narrow domain port for the verified result (observation contract O3/P5, transitions
 * {@code APPROVED}/{@code DECLINED}).
 *
 * <p>A caller invokes {@link #recordResult} <em>after</em> a durable dispatch intent exists and an
 * authenticated, correlated issuer reply is available. Implementations must commit the conditioned
 * business transition, the explicitly advanced committed journal version, the immutable critical
 * result event, the EVENT head, dedupe metadata and any slot reservation in ONE atomic transaction.
 * The call itself never invokes the downstream, never resends and never reverses.
 *
 * <p>The returned {@link Decision} is only a success when it is grounded in persistence: the
 * implementation must read the stored record back and confirm the matching committed version, the
 * matching confirmed state and issuer provenance. A success is never synthesised from a computed
 * value or from the caller's input. A competing terminal outcome is retained, never overwritten.
 *
 * <p><strong>Recovered knowledge is not a send permission.</strong> {@link Status#RECOVERED} proves
 * that an ambiguous commit can be shown to have happened; it does not authorize a resend, a reversal
 * or a legacy fallback. {@link Status#UNKNOWN} is genuinely uncertain: it is neither proof that no
 * result exists nor evidence of one.
 *
 * <p>When no implementation is injected, the legacy result path stays in force unchanged. This
 * port does not authorize a runtime fallback: a non-success outcome must not trigger
 * {@code updateOutcome}, a legacy event append, a retry, a resend or a reversal.
 */
public interface ResultGateway {

    /** Outcome classification of exactly one verified-result commit attempt. */
    enum Status {
        /** This invocation committed a new confirmed result. */
        COMMITTED,
        /** The identical result was already stored; nothing was written a second time. */
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
     * @param confirmedState  {@code APPROVED}/{@code DECLINED} read back from the stored record, or
     *                        {@code null} when no confirmed result was established
     * @param evidence        persisted committed-version evidence, or {@code null} when not read
     * @param detail          safe, non-payload diagnostic; never contains event content
     */
    record Decision(Status status, AuthorizationState confirmedState,
            CommittedVersionEvidence evidence, String detail) {

        public Decision {
            Objects.requireNonNull(status, "status");
        }

        /**
         * True only when a confirmed result was read back from persistence with recorded version
         * evidence. Replay and recovery confirm existence; they do not grant any downstream action.
         */
        public boolean recorded() {
            return (status == Status.COMMITTED || status == Status.REPLAY
                            || status == Status.RECOVERED)
                    && (confirmedState == AuthorizationState.APPROVED
                            || confirmedState == AuthorizationState.DECLINED)
                    && evidence != null
                    && evidence.committedVersionRecorded();
        }
    }

    /**
     * Perform exactly one atomic verified-result commit for the durable dispatch {@code intent}.
     * The evidence must be the authenticated, correlated issuer decision and its availability time
     * governs the committed result time. Invalid evidence is refused before any write.
     */
    Decision recordResult(AuthorizationRecord intent, DownstreamReply evidence);
}
