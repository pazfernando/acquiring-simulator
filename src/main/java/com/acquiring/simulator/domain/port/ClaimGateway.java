package com.acquiring.simulator.domain.port;

import com.acquiring.simulator.domain.event.CommittedVersionEvidence;
import com.acquiring.simulator.domain.model.AuthorizationRecord;
import java.time.Instant;
import java.util.Objects;

/**
 * Narrow domain port for the atomic initial claim (observation contract O3).
 *
 * <p>A caller invokes {@link #claim} <em>instead of</em> {@link AuthorizationRepository#claim}
 * followed by a separate event append. Implementations must commit the business record, business
 * head, critical claim event, EVENT head, dedupe metadata and any slot reservation in ONE atomic
 * transaction, and must never dispatch payment, resend a request or retry the write.
 *
 * <p>The returned {@link Decision} is the claim response the service acts on. {@code evidence} is
 * read back from persistence (never derived from an in-memory counter), so a caller can only report
 * a committed journal version that the stored claim record actually carries. {@code null} evidence
 * for a claimed/replayed outcome means the persisted evidence could not be established; the caller
 * must not fabricate a value.
 *
 * <p>When no implementation is injected, the legacy {@code claim()} path stays in force unchanged.
 * This port does not authorize a runtime fallback: {@link Status#UNKNOWN}, {@link
 * Status#CONTENT_CONFLICT} and {@link Status#NOT_COMMITTED} are terminal for that attempt and must
 * not trigger a legacy claim, a retry or a resend.
 */
public interface ClaimGateway {

    /** Outcome classification of exactly one claim commit attempt. */
    enum Status {
        /** The atomic transaction committed this claim (including ambiguous-after-commit recovery). */
        CLAIMED,
        /** The identical claim was already stored; nothing was written a second time. */
        REPLAY,
        /** The stable identity exists with different content; never overwritten, never replayed. */
        CONTENT_CONFLICT,
        /** The transaction was rejected; nothing was committed and nothing may be sent. */
        NOT_COMMITTED,
        /** State could not be established; UNAVAILABLE semantics, never proof of no-send. */
        UNKNOWN
    }

    /**
     * @param status   outcome classification
     * @param evidence persisted committed-version evidence, or {@code null} when not established
     * @param detail   safe, non-payload diagnostic; never contains event content
     */
    record Decision(Status status, CommittedVersionEvidence evidence, String detail) {

        public Decision {
            Objects.requireNonNull(status, "status");
        }

        /** True only when this attempt established a durably committed claim. */
        public boolean committed() {
            return status == Status.CLAIMED || status == Status.REPLAY;
        }
    }

    /**
     * Perform exactly one atomic claim commit for {@code record}. {@code correlationId} is the
     * processor-owned correlation allocated for this attempt and is carried by the claim event; the
     * record itself stays undispatched until the caller explicitly marks it dispatched.
     */
    Decision claim(AuthorizationRecord record, String correlationId, Instant now);
}
