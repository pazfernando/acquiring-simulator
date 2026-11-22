package com.acquiring.simulator.aws;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.acquiring.simulator.config.SimulatorProperties;
import com.acquiring.simulator.domain.AuthorizationService;
import com.acquiring.simulator.domain.IdentifierSource;
import com.acquiring.simulator.domain.event.CommittedVersionEvidence;
import com.acquiring.simulator.domain.model.AuthorizationRecord;
import com.acquiring.simulator.domain.model.AuthorizationResult;
import com.acquiring.simulator.domain.model.AuthorizationState;
import com.acquiring.simulator.domain.model.AuthorizedContext;
import com.acquiring.simulator.domain.model.CredentialDecision;
import com.acquiring.simulator.domain.model.CvmIndicator;
import com.acquiring.simulator.domain.model.DecisionSource;
import com.acquiring.simulator.domain.model.DownstreamReply;
import com.acquiring.simulator.domain.model.EntryMode;
import com.acquiring.simulator.domain.model.EvidenceEvent;
import com.acquiring.simulator.domain.model.IssuerDecision;
import com.acquiring.simulator.domain.model.Outcome;
import com.acquiring.simulator.domain.model.PurchaseRequest;
import com.acquiring.simulator.domain.model.RejectionAudit;
import com.acquiring.simulator.domain.model.Route;
import com.acquiring.simulator.domain.model.SimulatedDecisionRequest;
import com.acquiring.simulator.domain.port.AuthorizationRepository;
import com.acquiring.simulator.domain.port.ClaimGateway;
import com.acquiring.simulator.domain.port.DispatchIntentGateway;
import com.acquiring.simulator.domain.port.IssuerSimulator;
import com.acquiring.simulator.domain.port.ResultGateway;
import java.math.BigDecimal;
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
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * P1b4c service-level result slice: the atomic claim, dispatch-intent and result ports are consumed
 * through one optional atomic service path. No runtime is activated and no external call is made.
 *
 * <p>The fake downstream counts sends and the fake repository counts legacy writes, so each
 * assertion is direct: a confirmed result is committed exactly once through the port, the reported
 * outcome comes from persisted state, and no atomic-mode branch writes {@code updateOutcome},
 * {@code markDispatched} or a legacy event. {@code UNKNOWN}/{@code NOT_COMMITTED} fabricate no
 * financial result, and a competing terminal outcome is reported from storage instead of being
 * overwritten by the caller's reply.
 */
class ResultServiceWiringTest {

    private static final Instant NOW = Instant.parse("2026-11-18T12:00:01Z");
    private static final String TRANSACTION_ID = "PURCHASE-1";
    private static final CommittedVersionEvidence RESULT_EVIDENCE =
            new CommittedVersionEvidence(2L, 3L);

    private static final AuthorizedContext CALLER = new AuthorizedContext(
            "ACQ-SIM-001", "MERCH-SIM-001", "TEST", Set.of(Route.DOMESTIC));
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
    void approvedResultCommitsOnceBeforeTheResponse() {
        CountingDownstream downstream = new CountingDownstream(IssuerDecision.APPROVED);
        CountingResultGateway result = result(ResultGateway.Status.COMMITTED,
                AuthorizationState.APPROVED, RESULT_EVIDENCE);

        AuthorizationResult response = service(downstream, result).authorize(CALLER, request());

        assertThat(response.outcome()).isEqualTo(Outcome.APPROVED);
        assertThat(response.reference()).isNotBlank();
        assertThat(downstream.calls).isEqualTo(1);
        assertThat(result.calls).isEqualTo(1);
        assertThat(repository.updateOutcomeCalls).isZero();
        assertThat(repository.appendEventCalls).isZero();
        assertThat(repository.markDispatchedCalls).isZero();
    }

    @Test
    void declinedResultReportsThePersistedDecline() {
        CountingDownstream downstream = new CountingDownstream(IssuerDecision.DECLINED);
        CountingResultGateway result = result(ResultGateway.Status.COMMITTED,
                AuthorizationState.DECLINED, RESULT_EVIDENCE);

        AuthorizationResult response = service(downstream, result).authorize(CALLER, request());

        assertThat(response.outcome()).isEqualTo(Outcome.ISSUER_DECLINED);
        assertThat(downstream.calls).isEqualTo(1);
        assertThat(repository.updateOutcomeCalls).isZero();
        assertThat(repository.appendEventCalls).isZero();
    }

    @Test
    void recoveredPersistedResultAfterLostResponseKeepsOneDownstreamCall() {
        CountingDownstream downstream = new CountingDownstream(IssuerDecision.APPROVED);
        CountingResultGateway result = result(ResultGateway.Status.RECOVERED,
                AuthorizationState.APPROVED, RESULT_EVIDENCE);

        AuthorizationResult response = service(downstream, result).authorize(CALLER, request());

        assertThat(response.outcome()).isEqualTo(Outcome.APPROVED);
        assertThat(downstream.calls).isEqualTo(1);
        assertThat(result.calls).isEqualTo(1);
        assertThat(repository.updateOutcomeCalls).isZero();
        assertThat(repository.appendEventCalls).isZero();
    }

    @Test
    void duplicateResultDoesNotResend() {
        CountingDownstream downstream = new CountingDownstream(IssuerDecision.APPROVED);
        CountingResultGateway result = result(ResultGateway.Status.REPLAY,
                AuthorizationState.APPROVED, RESULT_EVIDENCE);

        AuthorizationResult response = service(downstream, result).authorize(CALLER, request());

        assertThat(response.outcome()).isEqualTo(Outcome.APPROVED);
        assertThat(downstream.calls).isEqualTo(1);
        assertThat(repository.updateOutcomeCalls).isZero();
        assertThat(repository.appendEventCalls).isZero();
    }

    @Test
    void unknownResultFabricatesNoFinancialOutcomeAndNoLegacyWrite() {
        CountingDownstream downstream = new CountingDownstream(IssuerDecision.APPROVED);
        CountingResultGateway result = result(ResultGateway.Status.UNKNOWN, null, null);

        AuthorizationResult response = service(downstream, result).authorize(CALLER, request());

        assertThat(response.outcome()).isEqualTo(Outcome.NOT_CONFIRMED);
        // Uncertain is not a no-send assertion and not a confirmed result: nothing is written.
        assertThat(repository.updateOutcomeCalls).isZero();
        assertThat(repository.appendEventCalls).isZero();
        assertThat(repository.markDispatchedCalls).isZero();
    }

    @Test
    void notCommittedResultFabricatesNoFinancialOutcome() {
        CountingDownstream downstream = new CountingDownstream(IssuerDecision.APPROVED);
        CountingResultGateway result = result(ResultGateway.Status.NOT_COMMITTED, null, null);

        AuthorizationResult response = service(downstream, result).authorize(CALLER, request());

        assertThat(response.outcome()).isEqualTo(Outcome.NOT_CONFIRMED);
        assertThat(repository.updateOutcomeCalls).isZero();
        assertThat(repository.appendEventCalls).isZero();
    }

    @Test
    void recordedStatusWithoutPersistedEvidenceIsNotSuccess() {
        CountingDownstream downstream = new CountingDownstream(IssuerDecision.APPROVED);
        CountingResultGateway result = result(ResultGateway.Status.COMMITTED,
                AuthorizationState.APPROVED, null);

        AuthorizationResult response = service(downstream, result).authorize(CALLER, request());

        assertThat(response.outcome()).isEqualTo(Outcome.NOT_CONFIRMED);
        assertThat(repository.updateOutcomeCalls).isZero();
        assertThat(repository.appendEventCalls).isZero();
    }

    @Test
    void competingResultKeepsTheAuthoritativeStoredOutcome() {
        CountingDownstream downstream = new CountingDownstream(IssuerDecision.APPROVED);
        CountingResultGateway result = result(ResultGateway.Status.CONTENT_CONFLICT, null, null);
        repository.confirmCompetingOutcome = AuthorizationState.DECLINED;

        AuthorizationResult response = service(downstream, result).authorize(CALLER, request());

        // The caller's APPROVED reply never overwrites the durable DECLINED, and no legacy row is
        // written; the authoritative stored outcome is what the caller sees.
        assertThat(response.outcome()).isEqualTo(Outcome.ISSUER_DECLINED);
        assertThat(downstream.calls).isEqualTo(1);
        assertThat(repository.updateOutcomeCalls).isZero();
        assertThat(repository.appendEventCalls).isZero();
    }

    @Test
    void lateConfirmedReplyFailsClosedWithoutGatewayCallOrLegacyWrite() {
        CountingDownstream downstream =
                new CountingDownstream(IssuerDecision.APPROVED, Duration.ofSeconds(3));
        CountingResultGateway result = result(ResultGateway.Status.COMMITTED,
                AuthorizationState.APPROVED, RESULT_EVIDENCE);

        AuthorizationResult response = service(downstream, result).authorize(CALLER, request());

        assertThat(response.outcome()).isEqualTo(Outcome.NOT_CONFIRMED);
        assertThat(result.calls).isZero();
        assertThat(repository.updateOutcomeCalls).isZero();
        assertThat(repository.appendEventCalls).isZero();
    }

    @Test
    void networkRejectionFailsClosedInsteadOfWritingLegacyRows() {
        CountingDownstream downstream = new CountingDownstream(IssuerDecision.NETWORK_REJECTED);
        CountingResultGateway result = result(ResultGateway.Status.COMMITTED,
                AuthorizationState.APPROVED, RESULT_EVIDENCE);

        AuthorizationResult response = service(downstream, result).authorize(CALLER, request());

        assertThat(response.outcome()).isEqualTo(Outcome.NOT_CONFIRMED);
        assertThat(result.calls).isZero();
        assertThat(repository.updateOutcomeCalls).isZero();
        assertThat(repository.appendEventCalls).isZero();
    }

    @Test
    void lateEvidenceAdmissionIsExplicitlyPendingOnTheAtomicPath() {
        AuthorizationService service = service(new CountingDownstream(IssuerDecision.APPROVED),
                result(ResultGateway.Status.COMMITTED, AuthorizationState.APPROVED, RESULT_EVIDENCE));

        assertThatThrownBy(() -> service.submitEvidence("AUTH-NOT-READ",
                new DownstreamReply("CORR-X", IssuerDecision.APPROVED, NOW.plusSeconds(3))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("late evidence");
        assertThat(repository.updateOutcomeCalls).isZero();
        assertThat(repository.appendEventCalls).isZero();
    }

    @Test
    void resultGatewayWithoutAtomicPredecessorsIsRejected() {
        assertThatThrownBy(() -> new AuthorizationService(repository,
                new CountingDownstream(IssuerDecision.APPROVED), PROPERTIES,
                Clock.fixed(NOW, ZoneOffset.UTC), claimingGateway(), null,
                result(ResultGateway.Status.COMMITTED, AuthorizationState.APPROVED, RESULT_EVIDENCE),
                IdentifierSource.randomHex()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("atomic claim and dispatch-intent");

        assertThatThrownBy(() -> new AuthorizationService(repository,
                new CountingDownstream(IssuerDecision.APPROVED), PROPERTIES,
                Clock.fixed(NOW, ZoneOffset.UTC), null, null,
                result(ResultGateway.Status.COMMITTED, AuthorizationState.APPROVED, RESULT_EVIDENCE),
                IdentifierSource.randomHex()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("atomic claim and dispatch-intent");
    }

    private AuthorizationService service(IssuerSimulator downstream, ResultGateway result) {
        return new AuthorizationService(repository, downstream, PROPERTIES,
                Clock.fixed(NOW, ZoneOffset.UTC), claimingGateway(), intentGateway(), result,
                IdentifierSource.randomHex());
    }

    private ClaimGateway claimingGateway() {
        return (record, correlationId, now) -> {
            repository.claim(record);
            if (repository.confirmCompetingOutcome != null) {
                repository.storeCompetingOutcome(record.id(),
                        repository.confirmCompetingOutcome);
            }
            return new ClaimGateway.Decision(ClaimGateway.Status.CLAIMED,
                    new CommittedVersionEvidence(0L, 1L), null);
        };
    }

    private DispatchIntentGateway intentGateway() {
        return (claim, correlationId, now) -> new DispatchIntentGateway.Decision(
                DispatchIntentGateway.Status.COMMITTED,
                new CommittedVersionEvidence(1L, 2L), null);
    }

    private static CountingResultGateway result(ResultGateway.Status status,
            AuthorizationState confirmedState, CommittedVersionEvidence evidence) {
        return new CountingResultGateway(status, confirmedState, evidence);
    }

    /** Port double that records how many atomic result commits the service attempted. */
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

    private static PurchaseRequest request() {
        return new PurchaseRequest("MERCH-SIM-001", "TERM-SIM-001", TRANSACTION_ID,
                new BigDecimal("10.00"), "USD", "SIM-APP-001", EntryMode.SIMULATED_CONTACT,
                CvmIndicator.SIMULATED_VERIFIED, true, null, null);
    }

    /** Fake downstream that records how many financial calls the service made. */
    private static final class CountingDownstream implements IssuerSimulator {
        private final IssuerDecision decision;
        private final Duration offset;
        int calls;

        CountingDownstream(IssuerDecision decision) {
            this(decision, Duration.ofMillis(10));
        }

        CountingDownstream(IssuerDecision decision, Duration offset) {
            this.decision = decision;
            this.offset = offset;
        }

        @Override public DownstreamReply decide(SimulatedDecisionRequest request) {
            calls++;
            return new DownstreamReply(request.correlationId(), decision,
                    request.dispatchedAt().plus(offset));
        }
    }

    /** Port double that counts every legacy write so atomic-mode branches cannot use one. */
    private static final class RecordingRepository implements AuthorizationRepository {

        private final Map<String, AuthorizationRecord> records = new LinkedHashMap<>();
        private final List<EvidenceEvent> events = new ArrayList<>();
        int markDispatchedCalls;
        int updateOutcomeCalls;
        int appendEventCalls;
        AuthorizationState confirmCompetingOutcome;

        @Override public void claim(AuthorizationRecord record) {
            records.put(record.id(), record);
        }

        @Override public void markDispatched(String authorizationId, String correlationId,
                Instant dispatchedAt) {
            markDispatchedCalls++;
            AuthorizationRecord record = records.get(authorizationId);
            records.put(authorizationId, record.withDispatch(correlationId, dispatchedAt));
        }

        @Override public void updateOutcome(String authorizationId, String state,
                String decisionSource, String reasonCategory, Instant outcomeDeterminedAt) {
            updateOutcomeCalls++;
            AuthorizationRecord record = records.get(authorizationId);
            records.put(authorizationId, record.withOutcome(AuthorizationState.valueOf(state),
                    DecisionSource.valueOf(decisionSource), reasonCategory, outcomeDeterminedAt));
        }

        /** Test bookkeeping for a competing confirmed result; not a service write. */
        void storeCompetingOutcome(String authorizationId, AuthorizationState state) {
            AuthorizationRecord record = records.get(authorizationId);
            records.put(authorizationId, record.withOutcome(state, DecisionSource.ISSUER, null, NOW));
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
