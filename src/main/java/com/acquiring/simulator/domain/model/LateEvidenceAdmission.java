package com.acquiring.simulator.domain.model;

import com.acquiring.simulator.domain.event.CommittedVersionEvidence;
import java.util.Objects;

/**
 * Persisted result of exactly one internal late-evidence admission attempt (observation contract
 * O3/P5; R07/R20; OBS-08/OBS-10).
 *
 * <p>This is knowledge read back from persistence, never a caller-supplied verdict. It carries no
 * delivery receipt, no evidence payload and no timing classification: those are adapter-side
 * admission facts, and only the confirmed state plus its committed-version evidence cross into the
 * domain. A green attempt is <strong>not</strong> proof that the evidence was cryptographically
 * authenticated; the upstream C4/P6 verification is still missing.
 *
 * <p>{@link Status#COMMITTED}, {@link Status#REPLAY} and {@link Status#RECOVERED} are meaningful
 * only together with {@link #recorded()} returning {@code true}. {@link Status#UNKNOWN} is
 * genuinely uncertain: it is neither proof that no result exists nor evidence of one.
 */
public record LateEvidenceAdmission(Status status, AuthorizationState confirmedState,
        CommittedVersionEvidence evidence, String detail) {

    /** Outcome of exactly one admission attempt. No new business state is introduced. */
    public enum Status {
        /** This attempt committed one late confirmed result. */
        COMMITTED,
        /** Fresh terminal read and receipt lookup show the identical evidence is already retained. */
        REPLAY,
        /** An ambiguous write was resolved by lookup as already committed; knowledge only. */
        RECOVERED,
        /**
         * Evidence was available at or before the deadline. It belongs to the normal verified-result
         * admission; the late path wrote nothing and lost nothing.
         */
        NORMAL_RESULT_ADMISSION,
        /** A different confirmed outcome or retained content exists; the known outcome is preserved. */
        CONTENT_CONFLICT,
        /** The candidate is not authorized for this operation; nothing was read for dedupe or written. */
        REFUSED,
        /** The transaction was definitively rejected and no concurrent identical commit was proven. */
        NOT_COMMITTED,
        /** State could not be established; never proof that no result exists. */
        UNKNOWN
    }

    public LateEvidenceAdmission {
        Objects.requireNonNull(status, "status");
    }

    /**
     * True only for a confirmed result read back from persistence with recorded committed-version
     * evidence. Replay and recovery confirm existence; they grant no downstream action.
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
