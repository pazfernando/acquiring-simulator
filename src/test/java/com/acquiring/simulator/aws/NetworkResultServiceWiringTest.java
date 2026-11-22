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
import com.acquiring.simulator.domain.port.NetworkResultGateway;
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
 * P1b5c service-level network-result slice: the optional {@link NetworkResultGateway} branch is
 * consumed exactly once for a valid, correlated, explicit pre-issuer {@code NETWORK_REJECTED} reply.
 *
 * <p>Every assertion is direct. The fake downstream counts sends, the fake network port counts
 * commits and records the evidence it received, the fake result port counts issuer-result commits,
 * and the fake repository counts legacy writes. A network result is reported as
 * {@code PROCESSOR_REJECTED} only from persisted {@code NETWORK_REJECTED} state plus committed
 * version evidence; uncertain, failed, absent or conflicting evidence stays {@code NOT_CONFIRMED}.
 * No branch writes {@code updateOutcome}, {@code markDispatched} or a legacy event, fabricates an
 * issuer task or decline, resends or retries. The legacy constructors stay on the unchanged legacy
 * path. No runtime is activated and no external call is made.
 */
class NetworkResultServiceWiringTest {

    private static final Instant NOW = Instant.parse("2026-11-18T12:00:01Z");
    private static final String TRANSACTION_ID = "PURCHASE-1";
    private static final CommittedVersionEvidence NETWORK_EVIDENCE =
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
    void committedNetworkRejectionReportsProcessorRejectedOnce() {
        CountingDownstream downstream = new CountingDownstream(IssuerDecision.NETWORK_REJECTED);
        CountingResultGateway result = result(ResultGateway.Status.COMMITTED,
                AuthorizationState.APPROVED, NETWORK_EVIDENCE);
        CountingNetworkResultGateway network = network(NetworkResultGateway.Status.COMMITTED,
                AuthorizationState.NETWORK_REJECTED, NETWORK_EVIDENCE);

        AuthorizationResult response = service(downstream, result, network)
                .authorize(CALLER, request());

        assertThat(response.outcome()).isEqualTo(Outcome.PROCESSOR_REJECTED);
        assertThat(response.reference()).isNotBlank();
        assertThat(downstream.calls).isEqualTo(1);
        assertThat(network.calls).isEqualTo(1);
        assertThat(result.calls).isZero();
        assertThat(network.lastEvidence.decision()).isEqualTo(IssuerDecision.NETWORK_REJECTED);
        assertThat(repository.updateOutcomeCalls).isZero();
        assertThat(repository.appendEventCalls).isZero();
        assertThat(repository.markDispatchedCalls).isZero();
        assertThat(repository.eventsFor(response.reference())).isEmpty();
    }

    @Test
    void recoveredNetworkRejectionReportsProcessorRejectedWithoutResend() {
        CountingDownstream downstream = new CountingDownstream(IssuerDecision.NETWORK_REJECTED);
        CountingNetworkResultGateway network = network(NetworkResultGateway.Status.RECOVERED,
                AuthorizationState.NETWORK_REJECTED, NETWORK_EVIDENCE);

        AuthorizationResult response = service(downstream, result(), network)
                .authorize(CALLER, request());

        assertThat(response.outcome()).isEqualTo(Outcome.PROCESSOR_REJECTED);
        assertThat(downstream.calls).isEqualTo(1);
        assertThat(network.calls).isEqualTo(1);
        assertThat(repository.updateOutcomeCalls).isZero();
        assertThat(repository.appendEventCalls).isZero();
        assertThat(repository.eventsFor(response.reference())).isEmpty();
    }

    @Test
    void replayedNetworkRejectionReportsProcessorRejectedWithoutResend() {
        CountingDownstream downstream = new CountingDownstream(IssuerDecision.NETWORK_REJECTED);
        CountingNetworkResultGateway network = network(NetworkResultGateway.Status.REPLAY,
                AuthorizationState.NETWORK_REJECTED, NETWORK_EVIDENCE);

        AuthorizationResult response = service(downstream, result(), network)
                .authorize(CALLER, request());

        assertThat(response.outcome()).isEqualTo(Outcome.PROCESSOR_REJECTED);
        assertThat(downstream.calls).isEqualTo(1);
        assertThat(network.calls).isEqualTo(1);
        assertThat(repository.updateOutcomeCalls).isZero();
        assertThat(repository.appendEventCalls).isZero();
    }

    @Test
    void unknownNetworkOutcomeStaysNotConfirmedWithoutLegacyWrite() {
        CountingDownstream downstream = new CountingDownstream(IssuerDecision.NETWORK_REJECTED);
        CountingNetworkResultGateway network = network(NetworkResultGateway.Status.UNKNOWN,
                null, null);

        AuthorizationResult response = service(downstream, result(), network)
                .authorize(CALLER, request());

        // Uncertain is not proof of no rejection and not a confirmed processor rejection.
        assertThat(response.outcome()).isEqualTo(Outcome.NOT_CONFIRMED);
        assertThat(network.calls).isEqualTo(1);
        assertThat(repository.updateOutcomeCalls).isZero();
        assertThat(repository.appendEventCalls).isZero();
        assertThat(repository.markDispatchedCalls).isZero();
        assertThat(repository.eventsFor(response.reference())).isEmpty();
    }

    @Test
    void networkEvidenceAbsentIsNotProcessorRejected() {
        CountingDownstream downstream = new CountingDownstream(IssuerDecision.NETWORK_REJECTED);
        CountingNetworkResultGateway network = network(NetworkResultGateway.Status.COMMITTED,
                AuthorizationState.NETWORK_REJECTED, null);

        AuthorizationResult response = service(downstream, result(), network)
                .authorize(CALLER, request());

        assertThat(response.outcome()).isEqualTo(Outcome.NOT_CONFIRMED);
        assertThat(network.calls).isEqualTo(1);
        assertThat(repository.updateOutcomeCalls).isZero();
        assertThat(repository.appendEventCalls).isZero();
        assertThat(repository.eventsFor(response.reference())).isEmpty();
    }

    @Test
    void notCommittedNetworkOutcomeStaysNotConfirmed() {
        CountingDownstream downstream = new CountingDownstream(IssuerDecision.NETWORK_REJECTED);
        CountingNetworkResultGateway network = network(NetworkResultGateway.Status.NOT_COMMITTED,
                null, null);

        AuthorizationResult response = service(downstream, result(), network)
                .authorize(CALLER, request());

        assertThat(response.outcome()).isEqualTo(Outcome.NOT_CONFIRMED);
        assertThat(network.calls).isEqualTo(1);
        assertThat(repository.updateOutcomeCalls).isZero();
        assertThat(repository.appendEventCalls).isZero();
    }

    @Test
    void competingIssuerOutcomeIsRetainedAndNotMislabelled() {
        CountingDownstream downstream = new CountingDownstream(IssuerDecision.NETWORK_REJECTED);
        CountingNetworkResultGateway network = network(NetworkResultGateway.Status.CONTENT_CONFLICT,
                null, null);
        repository.confirmCompetingOutcome = AuthorizationState.DECLINED;

        AuthorizationResult response = service(downstream, result(), network)
                .authorize(CALLER, request());

        // The durable issuer decline is authoritative precedence; the network reply neither
        // overwrites it nor turns it into a processor rejection.
        assertThat(response.outcome()).isEqualTo(Outcome.ISSUER_DECLINED);
        assertThat(network.calls).isEqualTo(1);
        assertThat(repository.updateOutcomeCalls).isZero();
        assertThat(repository.appendEventCalls).isZero();
        assertThat(repository.eventsFor(response.reference())).isEmpty();
    }

    @Test
    void uncorrelatedNetworkRejectionNeverReachesTheGatewayOrLegacyWrites() {
        IssuerSimulator mismatched = request -> new DownstreamReply("CORR-OTHER",
                IssuerDecision.NETWORK_REJECTED, request.dispatchedAt().plusMillis(10));
        CountingNetworkResultGateway network = network(NetworkResultGateway.Status.COMMITTED,
                AuthorizationState.NETWORK_REJECTED, NETWORK_EVIDENCE);

        AuthorizationResult response = service(mismatched, result(), network)
                .authorize(CALLER, request());

        assertThat(response.outcome()).isEqualTo(Outcome.NOT_CONFIRMED);
        assertThat(network.calls).isZero();
        assertThat(repository.updateOutcomeCalls).isZero();
        assertThat(repository.appendEventCalls).isZero();
        assertThat(repository.markDispatchedCalls).isZero();
    }

    @Test
    void networkRejectionPredatingDispatchIsRefusedBeforeAnyWrite() {
        CountingDownstream downstream = new CountingDownstream(IssuerDecision.NETWORK_REJECTED,
                Duration.ofMillis(-10));
        CountingNetworkResultGateway network = network(NetworkResultGateway.Status.COMMITTED,
                AuthorizationState.NETWORK_REJECTED, NETWORK_EVIDENCE);

        AuthorizationResult response = service(downstream, result(), network)
                .authorize(CALLER, request());

        assertThat(response.outcome()).isEqualTo(Outcome.NOT_CONFIRMED);
        assertThat(network.calls).isZero();
        assertThat(repository.updateOutcomeCalls).isZero();
        assertThat(repository.appendEventCalls).isZero();
    }

    @Test
    void issuerReplyNeverReachesTheNetworkGateway() {
        CountingDownstream downstream = new CountingDownstream(IssuerDecision.APPROVED);
        CountingResultGateway result = result(ResultGateway.Status.COMMITTED,
                AuthorizationState.APPROVED, NETWORK_EVIDENCE);
        CountingNetworkResultGateway network = network(NetworkResultGateway.Status.COMMITTED,
                AuthorizationState.NETWORK_REJECTED, NETWORK_EVIDENCE);

        AuthorizationResult response = service(downstream, result, network)
                .authorize(CALLER, request());

        assertThat(response.outcome()).isEqualTo(Outcome.APPROVED);
        assertThat(result.calls).isEqualTo(1);
        assertThat(network.calls).isZero();
        assertThat(repository.updateOutcomeCalls).isZero();
        assertThat(repository.appendEventCalls).isZero();
    }

    @Test
    void networkGatewayWithoutAtomicPredecessorsIsRejected() {
        CountingNetworkResultGateway network = network(NetworkResultGateway.Status.COMMITTED,
                AuthorizationState.NETWORK_REJECTED, NETWORK_EVIDENCE);

        assertThatThrownBy(() -> new AuthorizationService(repository,
                new CountingDownstream(IssuerDecision.NETWORK_REJECTED), PROPERTIES,
                Clock.fixed(NOW, ZoneOffset.UTC), claimingGateway(), intentGateway(), null,
                network, IdentifierSource.randomHex()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("network-result gateway requires");

        assertThatThrownBy(() -> new AuthorizationService(repository,
                new CountingDownstream(IssuerDecision.NETWORK_REJECTED), PROPERTIES,
                Clock.fixed(NOW, ZoneOffset.UTC), null, null, result(), network,
                IdentifierSource.randomHex()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("atomic claim");
    }

    @Test
    void legacyHarnessConstructorKeepsTheLegacyNetworkPath() {
        CountingDownstream downstream = new CountingDownstream(IssuerDecision.NETWORK_REJECTED);
        AuthorizationService legacy = new AuthorizationService(repository, downstream, PROPERTIES,
                Clock.fixed(NOW, ZoneOffset.UTC));

        AuthorizationResult response = legacy.authorize(CALLER, request());

        // The unchanged legacy path still records its own outcomes and events; the atomic network
        // branch is not involved because no atomic gateway was injected.
        assertThat(response.outcome()).isEqualTo(Outcome.PROCESSOR_REJECTED);
        assertThat(downstream.calls).isEqualTo(1);
        assertThat(repository.updateOutcomeCalls).isEqualTo(1);
        assertThat(repository.appendEventCalls).isEqualTo(1);
        assertThat(repository.markDispatchedCalls).isEqualTo(1);
    }

    private AuthorizationService service(IssuerSimulator downstream, ResultGateway result,
            NetworkResultGateway network) {
        return new AuthorizationService(repository, downstream, PROPERTIES,
                Clock.fixed(NOW, ZoneOffset.UTC), claimingGateway(), intentGateway(), result,
                network, IdentifierSource.randomHex());
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

    private static DispatchIntentGateway intentGateway() {
        return (claim, correlationId, now) -> new DispatchIntentGateway.Decision(
                DispatchIntentGateway.Status.COMMITTED,
                new CommittedVersionEvidence(1L, 2L), null);
    }

    private static CountingResultGateway result() {
        return result(ResultGateway.Status.COMMITTED, AuthorizationState.APPROVED, NETWORK_EVIDENCE);
    }

    private static CountingResultGateway result(ResultGateway.Status status,
            AuthorizationState confirmedState, CommittedVersionEvidence evidence) {
        return new CountingResultGateway(status, confirmedState, evidence);
    }

    private static CountingNetworkResultGateway network(NetworkResultGateway.Status status,
            AuthorizationState confirmedState, CommittedVersionEvidence evidence) {
        return new CountingNetworkResultGateway(status, confirmedState, evidence);
    }

    private static PurchaseRequest request() {
        return new PurchaseRequest("MERCH-SIM-001", "TERM-SIM-001", TRANSACTION_ID,
                new BigDecimal("10.00"), "USD", "SIM-APP-001", EntryMode.SIMULATED_CONTACT,
                CvmIndicator.SIMULATED_VERIFIED, true, null, null);
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

    /** Port double that records the single network commit and the evidence it received. */
    private static final class CountingNetworkResultGateway implements NetworkResultGateway {
        private final Status status;
        private final AuthorizationState confirmedState;
        private final CommittedVersionEvidence evidence;
        int calls;
        DownstreamReply lastEvidence;

        CountingNetworkResultGateway(Status status, AuthorizationState confirmedState,
                CommittedVersionEvidence evidence) {
            this.status = status;
            this.confirmedState = confirmedState;
            this.evidence = evidence;
        }

        @Override public Decision recordNetworkRejection(AuthorizationRecord intent,
                DownstreamReply evidence) {
            calls++;
            lastEvidence = evidence;
            return new Decision(status, confirmedState, this.evidence, "test");
        }
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
