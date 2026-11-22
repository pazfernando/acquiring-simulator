package com.acquiring.simulator.domain.port;

import com.acquiring.simulator.domain.event.CommittedVersionEvidence;
import com.acquiring.simulator.domain.model.AuthorizationRecord;
import java.time.Instant;
import java.util.Objects;

/**
 * Narrow domain port for the durable dispatch intent (observation contract O3, transition
 * {@code POSSIBLE_SEND}).
 *
 * <p>A caller invokes {@link #dispatchIntent} <em>before</em> any physical send. Implementations
 * must commit the conditioned business transition, the explicitly advanced committed journal
 * version, the immutable critical event, the EVENT head and dedupe metadata, and any slot
 * reservation in ONE atomic transaction. The call itself never invokes the downstream and never
 * sends a financial request.
 *
 * <p><strong>Send permission is not the same as recovered knowledge.</strong> Only {@link
 * Status#COMMITTED} means <em>this invocation</em> freshly committed the intent, and therefore only
 * that outcome permits exactly one downstream send. {@link Status#REPLAY} and {@link
 * Status#RECOVERED} prove the intent already existed; a strong lookup may recover that knowledge,
 * but it is never permission to resend. {@link Status#UNKNOWN} is genuinely uncertain: it is neither
 * proof that nothing was sent nor permission to send.
 *
 * <p>When no implementation is injected, the legacy dispatch path stays in force unchanged. This
 * port does not authorize a fallback: a non-{@code COMMITTED} outcome must not trigger {@code
 * markDispatched}, a legacy event append, a retry or a resend.
 */
public interface DispatchIntentGateway {

    /** Outcome classification of exactly one dispatch-intent commit attempt. */
    enum Status {
        /** This invocation committed a new intent; exactly one physical send is permitted. */
        COMMITTED,
        /** The identical intent was already committed; nothing was written and nothing may be sent. */
        REPLAY,
        /** An ambiguous write was resolved by lookup as already committed; knowledge only, no resend. */
        RECOVERED,
        /** The stable identity exists with different content; never overwritten, never sent. */
        CONTENT_CONFLICT,
        /** The transaction was rejected; nothing was committed by this attempt and nothing may be sent. */
        NOT_COMMITTED,
        /** State could not be established; UNAVAILABLE semantics, never proof of no-send. */
        UNKNOWN
    }

    /**
     * @param status   outcome classification
     * @param evidence persisted committed-version evidence when established, otherwise {@code null}
     * @param detail   safe, non-payload diagnostic; never contains event content
     */
    record Decision(Status status, CommittedVersionEvidence evidence, String detail) {

        public Decision {
            Objects.requireNonNull(status, "status");
        }

        /**
         * True only for a freshly committed intent. This is the single send gate: replay,
         * recovery, conflict, rejection and unknown states deliberately return {@code false}.
         */
        public boolean permitsSend() {
            return status == Status.COMMITTED
                    && evidence != null
                    && evidence.committedVersionRecorded();
        }
    }

    /**
     * Perform exactly one atomic dispatch-intent commit for {@code claim}. The claim must be the
     * undispatched result of the atomic initial claim; {@code dispatchCorrelationId} identifies the
     * single send attempt this intent authorizes.
     */
    Decision dispatchIntent(AuthorizationRecord claim, String dispatchCorrelationId, Instant now);
}
