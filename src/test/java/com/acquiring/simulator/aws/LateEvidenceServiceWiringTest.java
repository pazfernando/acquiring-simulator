package com.acquiring.simulator.aws;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.acquiring.simulator.config.SimulatorProperties;
import com.acquiring.simulator.domain.AuthorizationService;
import com.acquiring.simulator.domain.IdentifierSource;
import com.acquiring.simulator.domain.event.CommittedVersionEvidence;
import com.acquiring.simulator.domain.model.AuthorizationRecord;
import com.acquiring.simulator.domain.model.AuthorizationState;
import com.acquiring.simulator.domain.model.CredentialDecision;
import com.acquiring.simulator.domain.model.CvmIndicator;
import com.acquiring.simulator.domain.model.DecisionSource;
import com.acquiring.simulator.domain.model.DownstreamReply;
import com.acquiring.simulator.domain.model.EntryMode;
import com.acquiring.simulator.domain.model.EvidenceEvent;
import com.acquiring.simulator.domain.model.IssuerDecision;
import com.acquiring.simulator.domain.model.LateEvidenceAdmission;
import com.acquiring.simulator.domain.model.LateEvidenceOutcome;
import com.acquiring.simulator.domain.model.Outcome;
import com.acquiring.simulator.domain.model.RejectionAudit;
import com.acquiring.simulator.domain.model.RiskIndicator;
import com.acquiring.simulator.domain.model.Route;
import com.acquiring.simulator.domain.port.AuthorizationRepository;
import com.acquiring.simulator.domain.port.ClaimGateway;
import com.acquiring.simulator.domain.port.DispatchIntentGateway;
import com.acquiring.simulator.domain.port.IssuerSimulator;
import com.acquiring.simulator.domain.port.LateEvidenceGateway;
import com.acquiring.simulator.domain.port.ResultGateway;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * P1b6d service-level late-admission branch. The port is a fake and the result gateway counts calls,
 * so each assertion is direct: one admission attempt per service call, recorded outcomes carry
 * persisted evidence, unknown/conflict/refusal write nothing, and only on-time evidence with the
 * same durable context is routed to the verified-result path.
 *
 * <p>The {@link DownstreamReply} is an internal seam. A green test is not a C4/P6 authentication
 * claim and no public endpoint or runtime is exercised.
 */
class LateEvidenceServiceWiringTest {

    private static final Instant NOW = Instant.parse("2026-11-18T12:00:20Z");
    private static final Instant DISPATCHED_AT = Instant.parse("2026-11-18T12:00:01Z");
    private static final String AUTHORIZATION_ID = "AUTH-001";
    private static final String CORRELATION = "CORR-SEND-001";
    private static final CommittedVersionEvidence LATE_EVIDENCE =
            new CommittedVersionEvidence(2L, 3L);
    private static final CommittedVersionEvidence NORMAL_EVIDENCE =
            new CommittedVersionEvidence(2L, 3L);

    private static final SimulatorProperties PROPERTIES = new SimulatorProperties(
            new SimulatorProperties.Authorization(Duration.ofHours(24), Duration.ofSeconds(2)),
            new SimulatorProperties.Profile(
                    List.of(new SimulatorProperties.AcquirerFixture("ACQ-SIM-001",
                            List.of(Route.DOMESTIC))),
                    List.of(new SimulatorProperties.MerchantFixture("MERCH-SIM-001", "ACQ-SIM-001")),
                    List.of(new SimulatorProperties.TerminalFixture("TERM-SIM-001", "MERCH-SIM-001")),
                    List.of(new SimulatorProperties.CredentialRange("SIM-APP-",
                            CredentialDecision.APPROVE, Route.DOMESTIC))));

    private final RecordingRepository repository = new RecordingRepository();

    @Test
    void recordedLateAdmissionReturnsPersistedOutcomeAndVersion() {
        repository.put(dispatched());
        CountingLateGateway late = lateGate(LateEvidenceAdmission.Status.COMMITTED,
                AuthorizationState.APPROVED, LATE_EVIDENCE);
        CountingResultGateway result = result(ResultGateway.Status.COMMITTED,
                AuthorizationState.APPROVED, NORMAL_EVIDENCE);

        LateEvidenceOutcome outcome = service(late, result).submitVerifiedEvidence(
                AUTHORIZATION_ID, approved());

        assertThat(outcome.outcome()).isEqualTo(Outcome.APPROVED);
        assertThat(outcome.status()).isEqualTo(LateEvidenceAdmission.Status.COMMITTED);
        assertThat(outcome.recorded()).isTrue();
        assertThat(outcome.evidence().agreesWith(2L, 3L)).isTrue();
        assertThat(late.calls).isEqualTo(1);
        assertThat(result.calls).isZero();
        assertThat(repository.updateOutcomeCalls).isZero();
        assertThat(repository.appendEventCalls).isZero();
        assertThat(repository.markDispatchedCalls).isZero();
    }

    @Test
    void repeatedServiceCallsReplayWithoutASecondWriteOrSend() {
        repository.put(dispatched());
        CountingLateGateway late = lateGate(LateEvidenceAdmission.Status.REPLAY,
                AuthorizationState.APPROVED, LATE_EVIDENCE);
        CountingResultGateway result = result(ResultGateway.Status.COMMITTED,
                AuthorizationState.APPROVED, NORMAL_EVIDENCE);
        AuthorizationService service = service(late, result);

        LateEvidenceOutcome first = service.submitVerifiedEvidence(AUTHORIZATION_ID, approved());
        LateEvidenceOutcome second = service.submitVerifiedEvidence(AUTHORIZATION_ID, approved());

        assertThat(first.outcome()).isEqualTo(Outcome.APPROVED);
        assertThat(second.outcome()).isEqualTo(Outcome.APPROVED);
        assertThat(first.status()).isEqualTo(LateEvidenceAdmission.Status.REPLAY);
        assertThat(second.status()).isEqualTo(LateEvidenceAdmission.Status.REPLAY);
        assertThat(first.evidence().agreesWith(2L, 3L)).isTrue();
        assertThat(second.evidence().agreesWith(2L, 3L)).isTrue();
        // Exactly one admission attempt per service call; no forced resend and no result write.
        assertThat(late.calls).isEqualTo(2);
        assertThat(result.calls).isZero();
        assertThat(repository.updateOutcomeCalls).isZero();
        assertThat(repository.appendEventCalls).isZero();
    }

    @Test
    void unknownLateAdmissionStaysUncertainAndWritesNothing() {
        repository.put(dispatched());
        CountingLateGateway late = lateGate(LateEvidenceAdmission.Status.UNKNOWN, null, null);
        CountingResultGateway result = result(ResultGateway.Status.COMMITTED,
                AuthorizationState.APPROVED, NORMAL_EVIDENCE);

        LateEvidenceOutcome outcome = service(late, result).submitVerifiedEvidence(
                AUTHORIZATION_ID, approved());

        assertThat(outcome.outcome()).isEqualTo(Outcome.NOT_CONFIRMED);
        assertThat(outcome.status()).isEqualTo(LateEvidenceAdmission.Status.UNKNOWN);
        assertThat(outcome.recorded()).isFalse();
        assertThat(outcome.evidence()).isNull();
        assertThat(late.calls).isEqualTo(1);
        assertThat(result.calls).isZero();
        assertThat(repository.updateOutcomeCalls).isZero();
        assertThat(repository.appendEventCalls).isZero();
    }

    @Test
    void contentConflictPreservesTheAuthoritativeStoredOutcome() {
        repository.put(dispatched().withOutcome(AuthorizationState.DECLINED,
                DecisionSource.ISSUER, null, NOW.minusSeconds(10)));
        CountingLateGateway late = lateGate(LateEvidenceAdmission.Status.CONTENT_CONFLICT,
                null, null);
        CountingResultGateway result = result(ResultGateway.Status.COMMITTED,
                AuthorizationState.APPROVED, NORMAL_EVIDENCE);

        LateEvidenceOutcome outcome = service(late, result).submitVerifiedEvidence(
                AUTHORIZATION_ID, approved());

        // The caller's APPROVED candidate never overwrites the durable DECLINED.
        assertThat(outcome.outcome()).isEqualTo(Outcome.ISSUER_DECLINED);
        assertThat(outcome.status()).isEqualTo(LateEvidenceAdmission.Status.CONTENT_CONFLICT);
        assertThat(outcome.confirmedState()).isNull();
        assertThat(late.calls).isEqualTo(1);
        assertThat(result.calls).isZero();
        assertThat(repository.updateOutcomeCalls).isZero();
        assertThat(repository.appendEventCalls).isZero();
    }

    @Test
    void refusedLateAdmissionWritesNothing() {
        repository.put(dispatched());
        CountingLateGateway late = lateGate(LateEvidenceAdmission.Status.REFUSED,
                null, null);
        CountingResultGateway result = result(ResultGateway.Status.COMMITTED,
                AuthorizationState.APPROVED, NORMAL_EVIDENCE);

        LateEvidenceOutcome outcome = service(late, result).submitVerifiedEvidence(
                AUTHORIZATION_ID, approved());

        assertThat(outcome.outcome()).isEqualTo(Outcome.NOT_CONFIRMED);
        assertThat(outcome.status()).isEqualTo(LateEvidenceAdmission.Status.REFUSED);
        assertThat(outcome.recorded()).isFalse();
        assertThat(late.calls).isEqualTo(1);
        assertThat(result.calls).isZero();
        assertThat(repository.updateOutcomeCalls).isZero();
        assertThat(repository.appendEventCalls).isZero();
    }

    @Test
    void normalResultAdmissionRoutesToTheVerifiedResultPortOnce() {
        repository.put(dispatched());
        CountingLateGateway late = lateGate(LateEvidenceAdmission.Status.NORMAL_RESULT_ADMISSION,
                null, null);
        CountingResultGateway result = result(ResultGateway.Status.COMMITTED,
                AuthorizationState.APPROVED, NORMAL_EVIDENCE);

        LateEvidenceOutcome outcome = service(late, result).submitVerifiedEvidence(
                AUTHORIZATION_ID, approved());

        assertThat(outcome.outcome()).isEqualTo(Outcome.APPROVED);
        assertThat(outcome.status()).isEqualTo(LateEvidenceAdmission.Status.COMMITTED);
        assertThat(outcome.recorded()).isTrue();
        assertThat(outcome.evidence().agreesWith(2L, 3L)).isTrue();
        assertThat(late.calls).isEqualTo(1);
        assertThat(result.calls).isEqualTo(1);
        // The normal verified-result port owns the write; no legacy row is used.
        assertThat(repository.updateOutcomeCalls).isZero();
        assertThat(repository.appendEventCalls).isZero();
        assertThat(repository.markDispatchedCalls).isZero();
    }

    @Test
    void normalResultAdmissionWithoutTheSameDurableContextStaysPending() {
        // AWAITING but no durable dispatch correlation: the verified-result port must not be called.
        repository.put(awaitingWithoutCorrelation());
        CountingLateGateway late = lateGate(LateEvidenceAdmission.Status.NORMAL_RESULT_ADMISSION,
                null, null);
        CountingResultGateway result = result(ResultGateway.Status.COMMITTED,
                AuthorizationState.APPROVED, NORMAL_EVIDENCE);

        LateEvidenceOutcome outcome = service(late, result).submitVerifiedEvidence(
                AUTHORIZATION_ID, approved());

        assertThat(outcome.outcome()).isEqualTo(Outcome.NOT_CONFIRMED);
        assertThat(outcome.status()).isEqualTo(LateEvidenceAdmission.Status.NORMAL_RESULT_ADMISSION);
        assertThat(result.calls).isZero();
        assertThat(repository.updateOutcomeCalls).isZero();
        assertThat(repository.appendEventCalls).isZero();
    }

    @Test
    void legacyUncertaintyStaysUnknownWithoutMigration() {
        repository.put(dispatched().withOutcome(AuthorizationState.NOT_CONFIRMED,
                DecisionSource.NONE, "NO_CONFIRMATION", DISPATCHED_AT.plusSeconds(2)));
        CountingLateGateway late = lateGate(LateEvidenceAdmission.Status.NORMAL_RESULT_ADMISSION,
                null, null);
        CountingResultGateway result = result(ResultGateway.Status.COMMITTED,
                AuthorizationState.APPROVED, NORMAL_EVIDENCE);

        LateEvidenceOutcome outcome = service(late, result).submitVerifiedEvidence(
                AUTHORIZATION_ID, approved());

        assertThat(outcome.outcome()).isEqualTo(Outcome.NOT_CONFIRMED);
        assertThat(outcome.status()).isEqualTo(LateEvidenceAdmission.Status.UNKNOWN);
        assertThat(outcome.recorded()).isFalse();
        assertThat(result.calls).isZero();
        assertThat(repository.updateOutcomeCalls).isZero();
        assertThat(repository.appendEventCalls).isZero();
    }

    @Test
    void normalResultConflictPreservesTheStoredOutcome() {
        repository.put(dispatched());
        CountingLateGateway late = lateGate(LateEvidenceAdmission.Status.NORMAL_RESULT_ADMISSION,
                null, null);
        CountingResultGateway result = result(ResultGateway.Status.CONTENT_CONFLICT, null, null);

        LateEvidenceOutcome outcome = service(late, result).submitVerifiedEvidence(
                AUTHORIZATION_ID, approved());

        assertThat(outcome.outcome()).isEqualTo(Outcome.NOT_CONFIRMED);
        assertThat(outcome.status()).isEqualTo(LateEvidenceAdmission.Status.CONTENT_CONFLICT);
        assertThat(result.calls).isEqualTo(1);
        assertThat(repository.updateOutcomeCalls).isZero();
        assertThat(repository.appendEventCalls).isZero();
    }

    @Test
    void lateGatewayWithoutAtomicPredecessorsIsRejected() {
        assertThatThrownBy(() -> new AuthorizationService(repository,
                new NoDownstream(), PROPERTIES, Clock.fixed(NOW, ZoneOffset.UTC),
                null, null, null, null, lateGate(LateEvidenceAdmission.Status.UNKNOWN, null, null),
                IdentifierSource.randomHex()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("late-evidence gateway requires");

        assertThatThrownBy(() -> new AuthorizationService(repository,
                new NoDownstream(), PROPERTIES, Clock.fixed(NOW, ZoneOffset.UTC),
                claimingGateway(), null, null, null,
                lateGate(LateEvidenceAdmission.Status.UNKNOWN, null, null),
                IdentifierSource.randomHex()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("late-evidence gateway requires");
    }

    @Test
    void submitVerifiedEvidenceWithoutThePortIsExplicitlyPending() {
        AuthorizationService legacy = new AuthorizationService(repository, new NoDownstream(),
                PROPERTIES, Clock.fixed(NOW, ZoneOffset.UTC));

        assertThatThrownBy(() -> legacy.submitVerifiedEvidence(AUTHORIZATION_ID, approved()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not configured");
        assertThat(repository.updateOutcomeCalls).isZero();
        assertThat(repository.appendEventCalls).isZero();
    }

    private AuthorizationService service(LateEvidenceGateway late, ResultGateway result) {
        return new AuthorizationService(repository, new NoDownstream(), PROPERTIES,
                Clock.fixed(NOW, ZoneOffset.UTC), claimingGateway(), intentGateway(), result, null,
                late, IdentifierSource.randomHex());
    }

    private ClaimGateway claimingGateway() {
        return (record, correlationId, now) -> new ClaimGateway.Decision(
                ClaimGateway.Status.CLAIMED, new CommittedVersionEvidence(0L, 1L), null);
    }

    private DispatchIntentGateway intentGateway() {
        return (claim, correlationId, now) -> new DispatchIntentGateway.Decision(
                DispatchIntentGateway.Status.COMMITTED,
                new CommittedVersionEvidence(1L, 2L), null);
    }

    private static CountingLateGateway lateGate(LateEvidenceAdmission.Status status,
            AuthorizationState confirmedState, CommittedVersionEvidence evidence) {
        return new CountingLateGateway(status, confirmedState, evidence);
    }

    private static CountingResultGateway result(ResultGateway.Status status,
            AuthorizationState confirmedState, CommittedVersionEvidence evidence) {
        return new CountingResultGateway(status, confirmedState, evidence);
    }

    private static DownstreamReply approved() {
        return new DownstreamReply(CORRELATION, IssuerDecision.APPROVED, NOW.minusSeconds(15));
    }

    private static AuthorizationRecord dispatched() {
        return new AuthorizationRecord(
                AUTHORIZATION_ID,
                "ACQ-SIM-001|MERCH-SIM-001|TX-001",
                "ACQ-SIM-001",
                "MERCH-SIM-001",
                "TX-001",
                1000L,
                "USD",
                "SIM-APP-001",
                "TERM-SIM-001",
                EntryMode.SIMULATED_CONTACT,
                CvmIndicator.SIMULATED_VERIFIED,
                true,
                RiskIndicator.PASS,
                Route.DOMESTIC,
                "fingerprint-001",
                1,
                AuthorizationState.AWAITING,
                DecisionSource.NONE,
                null,
                CORRELATION,
                DISPATCHED_AT.minusSeconds(1),
                DISPATCHED_AT,
                null,
                DISPATCHED_AT.plusSeconds(86_400));
    }

    private static AuthorizationRecord awaitingWithoutCorrelation() {
        return new AuthorizationRecord(
                AUTHORIZATION_ID,
                "ACQ-SIM-001|MERCH-SIM-001|TX-001",
                "ACQ-SIM-001",
                "MERCH-SIM-001",
                "TX-001",
                1000L,
                "USD",
                "SIM-APP-001",
                "TERM-SIM-001",
                EntryMode.SIMULATED_CONTACT,
                CvmIndicator.SIMULATED_VERIFIED,
                true,
                RiskIndicator.PASS,
                Route.DOMESTIC,
                "fingerprint-001",
                1,
                AuthorizationState.AWAITING,
                DecisionSource.NONE,
                null,
                null,
                DISPATCHED_AT.minusSeconds(1),
                null,
                null,
                DISPATCHED_AT.plusSeconds(86_400));
    }

    private static final class CountingLateGateway implements LateEvidenceGateway {
        private final LateEvidenceAdmission.Status status;
        private final AuthorizationState confirmedState;
        private final CommittedVersionEvidence evidence;
        int calls;

        CountingLateGateway(LateEvidenceAdmission.Status status,
                AuthorizationState confirmedState, CommittedVersionEvidence evidence) {
            this.status = status;
            this.confirmedState = confirmedState;
            this.evidence = evidence;
        }

        @Override public LateEvidenceAdmission submit(String authorizationId,
                DownstreamReply reply, Instant deadline, Instant deliveredAt,
                String deliveryReceiptId) {
            calls++;
            return new LateEvidenceAdmission(status, confirmedState, evidence, "test");
        }
    }

    private static final class CountingResultGateway implements ResultGateway {
        private final Status status;
        private final AuthorizationState confirmedState;
        private final CommittedVersionEvidence evidence;
        int calls;

        CountingResultGateway(Status status, AuthorizationState confirmedState,
                CommittedVersionEvidence evidence) {
            this.status = status;
            this.confirmedState = confirmedState;
            this.evidence = evidence;
        }

        @Override public Decision recordResult(AuthorizationRecord intent, DownstreamReply reply) {
            calls++;
            return new Decision(status, confirmedState, evidence, "test");
        }
    }

    private static final class NoDownstream implements IssuerSimulator {
        @Override public DownstreamReply decide(
                com.acquiring.simulator.domain.model.SimulatedDecisionRequest request) {
            throw new AssertionError("late evidence must never call the downstream");
        }
    }

    /** Port double that counts every legacy write so the atomic branch cannot use one. */
    private static final class RecordingRepository implements AuthorizationRepository {

        private final Map<String, AuthorizationRecord> records = new LinkedHashMap<>();
        private final List<EvidenceEvent> events = new ArrayList<>();
        int markDispatchedCalls;
        int updateOutcomeCalls;
        int appendEventCalls;

        void put(AuthorizationRecord record) {
            records.put(record.id(), record);
        }

        @Override public void claim(AuthorizationRecord record) {
            records.put(record.id(), record);
        }

        @Override public void markDispatched(String authorizationId, String correlationId,
                Instant dispatchedAt) {
            markDispatchedCalls++;
        }

        @Override public void updateOutcome(String authorizationId, String state,
                String decisionSource, String reasonCategory, Instant outcomeDeterminedAt) {
            updateOutcomeCalls++;
        }

        @Override public Optional<AuthorizationRecord> findById(String id) {
            return Optional.ofNullable(records.get(id));
        }

        @Override public Optional<AuthorizationRecord> findLatestByBusinessKey(String businessKey) {
            return records.values().stream()
                    .filter(record -> record.businessKey().equals(businessKey))
                    .max(Comparator.comparingInt(AuthorizationRecord::generation));
        }

        @Override public List<EvidenceEvent> eventsFor(String authorizationId) {
            return events.stream()
                    .filter(event -> event.authorizationId().equals(authorizationId))
                    .toList();
        }

        @Override public void appendEvent(EvidenceEvent event) {
            appendEventCalls++;
            events.add(event);
        }

        @Override public void appendRejection(RejectionAudit audit) {
        }
    }
}
