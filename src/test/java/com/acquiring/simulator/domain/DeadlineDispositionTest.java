package com.acquiring.simulator.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.acquiring.simulator.domain.model.AuthorizationRecord;
import com.acquiring.simulator.domain.model.AuthorizationState;
import com.acquiring.simulator.domain.model.CvmIndicator;
import com.acquiring.simulator.domain.model.DecisionSource;
import com.acquiring.simulator.domain.model.EntryMode;
import com.acquiring.simulator.domain.model.Outcome;
import com.acquiring.simulator.domain.model.RiskIndicator;
import com.acquiring.simulator.domain.model.Route;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * P1b6a deterministic deadline-disposition tests.
 *
 * <p>The approved channel contract C3 row for "readable POSSIBLE_SEND without confirmation
 * at/after normal deadline" returns {@code NOT_CONFIRMED} with "original IDs/version" and says
 * "COMPLETED means this exchange ended, not payment finality". It does not create a new state,
 * version, event, HEAD/dedupe row or slot. These tests therefore assert a pure boundary rule:
 * just-before/exact/after behaviour, confirmed precedence, repeated evaluation without a
 * transition or budget use, and no dispatch.
 */
class DeadlineDispositionTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(2);
    private static final Instant DISPATCHED_AT = Instant.parse("2026-11-18T12:00:03Z");
    private static final Instant FIXED_NOW = Instant.parse("2026-11-18T12:00:04Z");

    @Test
    void justBeforeTheBoundaryKeepsReadablePossibleSendInFlight() {
        DeadlineDisposition disposition = evaluate(
                awaitingDispatched(), TIMEOUT.minusMillis(1));

        assertThat(disposition.phase()).isEqualTo(DeadlineDisposition.Phase.IN_FLIGHT);
        assertThat(disposition.boundaryReached()).isFalse();
        assertThat(disposition.outcome()).isEqualTo(Outcome.NOT_CONFIRMED);
        assertThat(disposition.readableState()).isEqualTo(AuthorizationState.AWAITING);
        assertReadOnly(disposition);
    }

    @Test
    void exactBoundaryIsAReadDispositionNotFinancialFinality() {
        DeadlineDisposition disposition = evaluate(awaitingDispatched(), TIMEOUT);

        assertThat(disposition.phase()).isEqualTo(DeadlineDisposition.Phase.BOUNDARY_REACHED);
        assertThat(disposition.boundaryReached()).isTrue();
        assertThat(disposition.outcome()).isEqualTo(Outcome.NOT_CONFIRMED);
        // The readable row is still the durable possible send; no NOT_CONFIRMED row is written.
        assertThat(disposition.readableState()).isEqualTo(AuthorizationState.AWAITING);
        assertThat(disposition.deadline()).isEqualTo(DISPATCHED_AT.plus(TIMEOUT));
        assertReadOnly(disposition);
    }

    @Test
    void afterBoundaryStaysTheSameReadOnlyDisposition() {
        DeadlineDisposition exact = evaluate(awaitingDispatched(), TIMEOUT);
        DeadlineDisposition after = evaluate(awaitingDispatched(), TIMEOUT.plusSeconds(30));

        assertThat(after.phase()).isEqualTo(DeadlineDisposition.Phase.BOUNDARY_REACHED);
        // Equal values prove repeated evaluation produces no additional disposition state.
        assertThat(after).isEqualTo(exact);
        assertReadOnly(after);
    }

    @Test
    void confirmedApprovalTakesPrecedenceAtAndAfterTheDeadline() {
        DeadlineDisposition exactlyAt = evaluate(settled(AuthorizationState.APPROVED), TIMEOUT);
        DeadlineDisposition longAfter = evaluate(settled(AuthorizationState.APPROVED),
                TIMEOUT.plus(Duration.ofHours(4)));

        assertThat(exactlyAt.phase()).isEqualTo(DeadlineDisposition.Phase.SETTLED);
        assertThat(exactlyAt.outcome()).isEqualTo(Outcome.APPROVED);
        assertThat(exactlyAt.boundaryReached()).isFalse();
        assertThat(longAfter.outcome()).isEqualTo(Outcome.APPROVED);
        assertThat(longAfter.readableState()).isEqualTo(AuthorizationState.APPROVED);
        assertReadOnly(exactlyAt);
        assertReadOnly(longAfter);
    }

    @Test
    void confirmedDeclineAndNetworkRejectionAreNeverRegressedByTheDeadline() {
        DeadlineDisposition decline = evaluate(settled(AuthorizationState.DECLINED),
                TIMEOUT.plusSeconds(30));
        DeadlineDisposition network = evaluate(settled(AuthorizationState.NETWORK_REJECTED),
                TIMEOUT.plusSeconds(30));

        assertThat(decline.outcome()).isEqualTo(Outcome.ISSUER_DECLINED);
        assertThat(decline.readableState()).isEqualTo(AuthorizationState.DECLINED);
        assertThat(network.outcome()).isEqualTo(Outcome.PROCESSOR_REJECTED);
        assertThat(network.readableState()).isEqualTo(AuthorizationState.NETWORK_REJECTED);
        assertReadOnly(decline);
        assertReadOnly(network);
    }

    @Test
    void repeatedEvaluationCreatesNoTransitionNoSlotAndNoDispatch() {
        List<DeadlineDisposition> evaluations = List.of(
                evaluate(awaitingDispatched(), TIMEOUT),
                evaluate(awaitingDispatched(), TIMEOUT),
                evaluate(awaitingDispatched(), TIMEOUT.plusSeconds(1)),
                evaluate(awaitingDispatched(), TIMEOUT.plusSeconds(60)));

        assertThat(evaluations).allMatch(e -> e.equals(evaluations.get(0)));
        assertThat(evaluations).allMatch(e -> e.readableState() == AuthorizationState.AWAITING);
        assertThat(evaluations).allMatch(e -> !e.persistsTransition());
        assertThat(evaluations).allMatch(e -> !e.advancesCommittedVersion());
        assertThat(evaluations).allMatch(e -> !e.appendsCriticalEvent());
        assertThat(evaluations).allMatch(e -> !e.consumesCriticalSlot());
        assertThat(evaluations).allMatch(e -> !e.createsFinancialFinality());
        assertThat(evaluations).allMatch(e -> !e.permitsDispatch());
    }

    @Test
    void aClaimWithoutDispatchHasNoDeadline() {
        DeadlineDisposition disposition = DeadlineDisposition.evaluate(
                record(AuthorizationState.AWAITING, null), FIXED_NOW, TIMEOUT);

        assertThat(disposition.phase()).isEqualTo(DeadlineDisposition.Phase.NOT_DISPATCHED);
        assertThat(disposition.boundaryReached()).isFalse();
        assertThat(disposition.deadline()).isNull();
        assertThat(disposition.outcome()).isEqualTo(Outcome.NOT_CONFIRMED);
        assertReadOnly(disposition);
    }

    @Test
    void anOlderPersistedUncertaintyIsReadWithoutASecondTransition() {
        DeadlineDisposition disposition = evaluate(record(AuthorizationState.NOT_CONFIRMED,
                DISPATCHED_AT), TIMEOUT);

        assertThat(disposition.phase()).isEqualTo(DeadlineDisposition.Phase.ALREADY_UNCERTAIN);
        assertThat(disposition.boundaryReached()).isFalse();
        assertThat(disposition.outcome()).isEqualTo(Outcome.NOT_CONFIRMED);
        assertThat(disposition.readableState()).isEqualTo(AuthorizationState.NOT_CONFIRMED);
        assertReadOnly(disposition);
    }

    @Test
    void invalidInputsAreRejectedBeforeAnyEvaluation() {
        assertThatThrownBy(() -> DeadlineDisposition.evaluate(null, FIXED_NOW, TIMEOUT))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> DeadlineDisposition.evaluate(awaitingDispatched(), null, TIMEOUT))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> DeadlineDisposition.evaluate(awaitingDispatched(), FIXED_NOW, null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> DeadlineDisposition.evaluate(awaitingDispatched(), FIXED_NOW,
                Duration.ofSeconds(-1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("timeout");
    }

    private static DeadlineDisposition evaluate(
            AuthorizationRecord record, Duration offsetFromDispatch) {
        // Deterministic clock: the evaluation instant is dispatch + offset, never the wall clock.
        Clock clock = Clock.offset(
                Clock.fixed(DISPATCHED_AT, ZoneOffset.UTC), offsetFromDispatch);
        return DeadlineDisposition.evaluate(record, clock.instant(), TIMEOUT);
    }

    private static AuthorizationRecord awaitingDispatched() {
        return record(AuthorizationState.AWAITING, DISPATCHED_AT);
    }

    private static AuthorizationRecord settled(AuthorizationState state) {
        return record(state, DISPATCHED_AT);
    }

    private static AuthorizationRecord record(AuthorizationState state, Instant dispatchedAt) {
        return new AuthorizationRecord(
                "AUTH-1",
                "BUSINESS-1",
                "ACQ-SIM-001",
                "MERCH-SIM-001",
                "CHANNEL-1",
                1_000L,
                "USD",
                "SIM-APP-001",
                "TERM-SIM-001",
                EntryMode.SIMULATED_CONTACT,
                CvmIndicator.SIMULATED_VERIFIED,
                false,
                RiskIndicator.PASS,
                Route.DOMESTIC,
                "fingerprint-1",
                1,
                state,
                DecisionSource.NONE,
                null,
                dispatchedAt == null ? null : "CORR-1",
                DISPATCHED_AT.minusSeconds(1),
                dispatchedAt,
                null,
                DISPATCHED_AT.plus(Duration.ofHours(24)));
    }

    private static void assertReadOnly(DeadlineDisposition disposition) {
        assertThat(disposition.persistsTransition()).isFalse();
        assertThat(disposition.advancesCommittedVersion()).isFalse();
        assertThat(disposition.appendsCriticalEvent()).isFalse();
        assertThat(disposition.consumesCriticalSlot()).isFalse();
        assertThat(disposition.createsFinancialFinality()).isFalse();
        assertThat(disposition.permitsDispatch()).isFalse();
    }
}
