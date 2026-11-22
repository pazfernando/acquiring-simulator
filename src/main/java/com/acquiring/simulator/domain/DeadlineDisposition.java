package com.acquiring.simulator.domain;

import com.acquiring.simulator.domain.model.AuthorizationRecord;
import com.acquiring.simulator.domain.model.AuthorizationState;
import com.acquiring.simulator.domain.model.Outcome;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Pure, read-only deadline disposition for Operation A.
 *
 * <p>The approved channel contract defines the deadline as a <em>read disposition</em>, not as a
 * new committed business transition. Its C3 row reads: "readable POSSIBLE_SEND without confirmation
 * at/after normal deadline | 200 | NOT_CONFIRMED / REPLAY for duplicate/history or COMPLETED for
 * initial deadline response / null | original IDs/version; COMPLETED means this exchange ended, not
 * payment finality". The same contract's deadline paragraph keeps the earlier evidence rule: a
 * timeout "after possible dispatch" is "unknown absent previously confirmed evidence", with "no
 * automatic retries and no assumed harvesting of a timed-out invocation".
 *
 * <p>Observation contract O3 likewise requires the post-send invocation to return a "conservative
 * {@code NOT_CONFIRMED} unless prior committed result readable" and forbids any automatic resend,
 * rollback, reversal or inquiry. O8 says repeated evidence or timers "duplicate no new transition",
 * and async vector A16 keeps the operation's own critical reserve for the later verified result.
 * R07 requires confirmed state never to regress to uncertainty. The approved event vocabulary has no
 * timeout task or reason, so a synthetic critical {@code TIMEOUT} event would be invented evidence.
 *
 * <p>Consequence: a deadline evaluation never changes the persisted state, advances the committed
 * version, appends a critical event or HEAD/dedupe row, consumes a critical slot, declares financial
 * finality or permits a dispatch. A readable {@code POSSIBLE_SEND} stays readable at and after the
 * deadline; only the caller-visible outcome is the conservative {@code NOT_CONFIRMED}. The legacy
 * {@code NOT_CONFIRMED} state remains readable if an older local row already carries it, and is not
 * transitioned again.
 */
public record DeadlineDisposition(
        Phase phase,
        Outcome outcome,
        Instant deadline,
        AuthorizationState readableState) {

    /** The four observable deadline situations plus the settled/uncertain read-only states. */
    public enum Phase {
        /** A claim exists but no dispatch happened, so no deadline applies yet. */
        NOT_DISPATCHED,
        /** Dispatch is durable and the evaluation time is before the deadline. */
        IN_FLIGHT,
        /** Dispatch is durable and the evaluation time is at or after the deadline. */
        BOUNDARY_REACHED,
        /** A durable confirmed result is readable; the deadline never overrides it. */
        SETTLED,
        /** An older local row already persists uncertainty; it is read, not transitioned again. */
        ALREADY_UNCERTAIN
    }

    public DeadlineDisposition {
        Objects.requireNonNull(phase, "phase");
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(readableState, "readableState");
    }

    /**
     * Evaluate the deadline rule without any side effect. The evaluation time is supplied by the
     * caller's clock; this method never reads the wall clock.
     */
    public static DeadlineDisposition evaluate(
            AuthorizationRecord record, Instant evaluatedAt, Duration timeout) {
        Objects.requireNonNull(record, "record");
        Objects.requireNonNull(evaluatedAt, "evaluatedAt");
        Objects.requireNonNull(timeout, "timeout");
        if (timeout.isNegative()) {
            throw new IllegalArgumentException("timeout cannot be negative");
        }
        AuthorizationState state = record.state();
        if (state == null) {
            throw new IllegalArgumentException("record state is required");
        }
        Instant deadline = record.dispatchedAt() == null
                ? null
                : record.dispatchedAt().plus(timeout);

        return switch (state) {
            case APPROVED -> settled(record, Outcome.APPROVED, deadline);
            case DECLINED -> settled(record, Outcome.ISSUER_DECLINED, deadline);
            case NETWORK_REJECTED -> settled(record, Outcome.PROCESSOR_REJECTED, deadline);
            case NOT_CONFIRMED ->
                    new DeadlineDisposition(Phase.ALREADY_UNCERTAIN, Outcome.NOT_CONFIRMED,
                            deadline, state);
            case AWAITING -> {
                if (record.dispatchedAt() == null) {
                    yield new DeadlineDisposition(Phase.NOT_DISPATCHED, Outcome.NOT_CONFIRMED,
                            null, state);
                }
                boolean reached = !evaluatedAt.isBefore(deadline);
                yield new DeadlineDisposition(
                        reached ? Phase.BOUNDARY_REACHED : Phase.IN_FLIGHT,
                        Outcome.NOT_CONFIRMED, deadline, state);
            }
        };
    }

    private static DeadlineDisposition settled(
            AuthorizationRecord record, Outcome outcome, Instant deadline) {
        return new DeadlineDisposition(Phase.SETTLED, outcome, deadline, record.state());
    }

    /** True only when a readable possible send is at or past its deadline. */
    public boolean boundaryReached() {
        return phase == Phase.BOUNDARY_REACHED;
    }

    /** A deadline evaluation never writes a business transition. */
    public boolean persistsTransition() {
        return false;
    }

    /** A deadline evaluation never advances the committed journal version. */
    public boolean advancesCommittedVersion() {
        return false;
    }

    /** A deadline evaluation never appends a critical event. */
    public boolean appendsCriticalEvent() {
        return false;
    }

    /** A deadline evaluation never consumes a reserved critical slot. */
    public boolean consumesCriticalSlot() {
        return false;
    }

    /** The exchange ending is not payment finality. */
    public boolean createsFinancialFinality() {
        return false;
    }

    /** A deadline evaluation never authorizes a send or a resend. */
    public boolean permitsDispatch() {
        return false;
    }
}
