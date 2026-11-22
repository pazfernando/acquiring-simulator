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
import com.acquiring.simulator.domain.port.DispatchIntentGateway.Status;
import com.acquiring.simulator.domain.port.IssuerSimulator;
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
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * P1b3c service-level dispatch-intent slice: the atomic claim port and a narrow dispatch-intent
 * port are consumed through one optional atomic service path.
 *
 * <p>The fake downstream records calls, so the tests prove the send gate directly: a newly
 * committed intent sends exactly once and before/after ordering is observable; a cancelled or
 * unknown intent never reaches the downstream; a replay or recovered intent is knowledge only and
 * never causes a second financial call. The legacy {@code markDispatched} path is counted and must
 * stay unused on every atomic-intent outcome. No runtime is activated and no external call is made.
 */
class DispatchIntentServiceWiringTest {

    private static final Instant NOW = Instant.parse("2026-11-18T12:00:01Z");
    private static final String TRANSACTION_ID = "PURCHASE-1";

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
    private final List<String> order = new ArrayList<>();
    private final CountingDownstream downstream = new CountingDownstream(order);

    @Test
    void newIntentCommitsBeforeDownstreamAndSendsExactlyOnce() {
        AuthorizationService service = service(intent(Status.COMMITTED,
                new CommittedVersionEvidence(1L, 2L)));

        AuthorizationResult result = service.authorize(CALLER, request());

        assertThat(result.outcome()).isEqualTo(Outcome.APPROVED);
        assertThat(downstream.calls).isEqualTo(1);
        assertThat(order).containsExactly("intent", "send");
        assertThat(repository.markDispatchedCalls).isZero();
    }

    @Test
    void cancelledIntentPreventsDownstreamAndDoesNotFallBackToLegacyDispatch() {
        AuthorizationService service = service(intent(Status.NOT_COMMITTED, null));

        AuthorizationResult result = service.authorize(CALLER, request());

        assertThat(result.outcome()).isEqualTo(Outcome.NOT_CONFIRMED);
        assertThat(downstream.calls).isZero();
        assertThat(order).containsExactly("intent");
        assertThat(repository.markDispatchedCalls).isZero();
        assertThat(repository.eventsFor(result.reference())).isEmpty();
    }

    @Test
    void unknownIntentRemainsUncertainAndPreventsDownstream() {
        AuthorizationService service = service(intent(Status.UNKNOWN, null));

        AuthorizationResult result = service.authorize(CALLER, request());

        assertThat(result.outcome()).isEqualTo(Outcome.NOT_CONFIRMED);
        assertThat(downstream.calls).isZero();
        assertThat(repository.markDispatchedCalls).isZero();
        // Uncertain is not a no-send assertion: no confirmed outcome or event is fabricated.
        assertThat(repository.eventsFor(result.reference())).isEmpty();
    }

    @Test
    void replayedIntentDoesNotSendTwice() {
        AuthorizationService service = service(intent(Status.REPLAY,
                new CommittedVersionEvidence(1L, 2L)));

        AuthorizationResult result = service.authorize(CALLER, request());

        assertThat(result.outcome()).isEqualTo(Outcome.NOT_CONFIRMED);
        assertThat(downstream.calls).isZero();
        assertThat(repository.markDispatchedCalls).isZero();
    }

    @Test
    void recoveredIntentIsKnowledgeOnlyAndNotPermissionToResend() {
        AuthorizationService service = service(intent(Status.RECOVERED,
                new CommittedVersionEvidence(1L, 2L)));

        AuthorizationResult result = service.authorize(CALLER, request());

        assertThat(result.outcome()).isEqualTo(Outcome.NOT_CONFIRMED);
        assertThat(downstream.calls).isZero();
        assertThat(repository.markDispatchedCalls).isZero();
    }

    @Test
    void contentConflictPreventsDownstream() {
        AuthorizationService service = service(intent(Status.CONTENT_CONFLICT, null));

        AuthorizationResult result = service.authorize(CALLER, request());

        assertThat(result.outcome()).isEqualTo(Outcome.NOT_CONFIRMED);
        assertThat(downstream.calls).isZero();
        assertThat(repository.markDispatchedCalls).isZero();
    }

    @Test
    void committedIntentWithoutPersistedEvidenceDoesNotSend() {
        AuthorizationService service = service(intent(Status.COMMITTED, null));

        AuthorizationResult result = service.authorize(CALLER, request());

        assertThat(result.outcome()).isEqualTo(Outcome.NOT_CONFIRMED);
        assertThat(downstream.calls).isZero();
        assertThat(repository.markDispatchedCalls).isZero();
    }

    @Test
    void serviceWithoutIntentGatewayKeepsTheLegacyDispatchPath() {
        AuthorizationService service = new AuthorizationService(repository, downstream, PROPERTIES,
                Clock.fixed(NOW, ZoneOffset.UTC), claimingGateway());

        AuthorizationResult result = service.authorize(CALLER, request());

        assertThat(result.outcome()).isEqualTo(Outcome.APPROVED);
        assertThat(downstream.calls).isEqualTo(1);
        assertThat(repository.markDispatchedCalls).isEqualTo(1);
    }

    @Test
    void dispatchIntentGatewayWithoutClaimGatewayIsRejected() {
        assertThatThrownBy(() -> new AuthorizationService(repository, downstream, PROPERTIES,
                Clock.fixed(NOW, ZoneOffset.UTC), null,
                intent(Status.COMMITTED, new CommittedVersionEvidence(1L, 2L)),
                IdentifierSource.randomHex()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("claim gateway");
    }

    private AuthorizationService service(DispatchIntentGateway intentGateway) {
        return new AuthorizationService(repository, downstream, PROPERTIES,
                Clock.fixed(NOW, ZoneOffset.UTC), claimingGateway(), intentGateway,
                IdentifierSource.randomHex());
    }

    private ClaimGateway claimingGateway() {
        return (record, correlationId, now) -> {
            repository.claim(record);
            return new ClaimGateway.Decision(ClaimGateway.Status.CLAIMED,
                    new CommittedVersionEvidence(0L, 1L), null);
        };
    }

    private DispatchIntentGateway intent(DispatchIntentGateway.Status status,
            CommittedVersionEvidence evidence) {
        return (claim, correlationId, now) -> {
            order.add("intent");
            return new DispatchIntentGateway.Decision(status, evidence, null);
        };
    }

    private static PurchaseRequest request() {
        return new PurchaseRequest("MERCH-SIM-001", "TERM-SIM-001", TRANSACTION_ID,
                new BigDecimal("10.00"), "USD", "SIM-APP-001", EntryMode.SIMULATED_CONTACT,
                CvmIndicator.SIMULATED_VERIFIED, true, null, null);
    }

    private static final class CountingDownstream implements IssuerSimulator {
        private final List<String> order;
        int calls;

        CountingDownstream(List<String> order) {
            this.order = order;
        }

        @Override public DownstreamReply decide(SimulatedDecisionRequest request) {
            calls++;
            order.add("send");
            return new DownstreamReply(request.correlationId(), IssuerDecision.APPROVED,
                    request.dispatchedAt().plusMillis(10));
        }
    }

    /** Minimal port double; counts the legacy dispatch write so the atomic path cannot use it. */
    private static final class RecordingRepository implements AuthorizationRepository {

        private final Map<String, AuthorizationRecord> records = new LinkedHashMap<>();
        private final List<EvidenceEvent> events = new ArrayList<>();
        int markDispatchedCalls;

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
            AuthorizationRecord record = records.get(authorizationId);
            records.put(authorizationId, record.withOutcome(AuthorizationState.valueOf(state),
                    DecisionSource.valueOf(decisionSource), reasonCategory, outcomeDeterminedAt));
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
            events.add(event);
        }

        @Override public void appendRejection(RejectionAudit audit) {
        }
    }
}
