package com.acquiring.simulator.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.acquiring.simulator.StubIssuerSimulator;
import com.acquiring.simulator.config.SimulatorProperties;
import com.acquiring.simulator.domain.event.EventSanitizer;
import com.acquiring.simulator.domain.event.EventValidationException;
import com.acquiring.simulator.domain.model.AuthorizationRecord;
import com.acquiring.simulator.domain.model.AuthorizationResult;
import com.acquiring.simulator.domain.model.AuthorizationState;
import com.acquiring.simulator.domain.model.AuthorizedContext;
import com.acquiring.simulator.domain.model.CredentialDecision;
import com.acquiring.simulator.domain.model.CvmIndicator;
import com.acquiring.simulator.domain.model.DecisionSource;
import com.acquiring.simulator.domain.model.EntryMode;
import com.acquiring.simulator.domain.model.EvidenceEvent;
import com.acquiring.simulator.domain.model.Outcome;
import com.acquiring.simulator.domain.model.PurchaseRequest;
import com.acquiring.simulator.domain.model.RejectionAudit;
import com.acquiring.simulator.domain.model.Route;
import com.acquiring.simulator.domain.port.AuthorizationRepository;
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
 * Deterministic regression for the observed failure where an internally generated identifier
 * happened to contain a 13-19 digit run and the event sanitizer rejected it as a PAN shape.
 *
 * <p>A controllable {@link IdentifierSource} is injected, so the dangerous input is fixed rather
 * than left to chance: the generated authorization, correlation and evidence identifiers are
 * asserted against the real {@link EventSanitizer} (PAN, PIN-block and token heuristics), the
 * 24-hex-character contract and the 64-character event bound, while the repair is shown to keep
 * every character except the one that would extend the digit run.
 */
class GeneratedIdentifierSanitizerTest {

    private static final Instant NOW = Instant.parse("2026-11-18T12:00:01Z");
    private static final String DANGEROUS = "ab1234567890123cdef01234";
    private static final String SAFE = "fedcba9876543210fedcba98";

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

    @Test
    void generatedIdentifiersAreSanitizerSafeWithBoundedSchemaOnFixedSample() {
        // Authorization id gets the dangerous run, correlation id a safe one, the evidence id
        // another dangerous run: the seam makes the previously random condition deterministic.
        List<String> supplied = List.of(DANGEROUS, SAFE, DANGEROUS, DANGEROUS);
        AtomicInteger calls = new AtomicInteger();
        IdentifierSource generator = () -> supplied.get(calls.getAndIncrement() % supplied.size());
        InMemoryRepository repository = new InMemoryRepository();
        AuthorizationService service = new AuthorizationService(repository, new StubIssuerSimulator(),
                PROPERTIES, Clock.fixed(NOW, ZoneOffset.UTC), null, generator);

        AuthorizationResult result = service.authorize(CALLER, request());
        assertThat(result.outcome()).isEqualTo(Outcome.APPROVED);
        String authorizationId = result.reference();
        AuthorizationRecord record = repository.findById(authorizationId).orElseThrow();

        List<String> identifiers = new ArrayList<>();
        identifiers.add(authorizationId);
        identifiers.add(record.dispatchCorrelationId());
        repository.eventsFor(authorizationId).stream().map(EvidenceEvent::id).forEach(identifiers::add);

        assertThat(identifiers).hasSizeGreaterThanOrEqualTo(3);
        assertThat(identifiers).doesNotHaveDuplicates();
        assertThat(identifiers).allSatisfy(id -> {
            assertThat(id).matches("(AUTH|CORR|EVT)-[0-9a-f]{24}");
            assertThat(id.length()).isLessThanOrEqualTo(64);
            EventSanitizer.requireSafe("generatedIdentifier", id);
        });
        assertThat(authorizationId).startsWith("AUTH-");
        assertThat(record.dispatchCorrelationId()).startsWith("CORR-");
        assertThat(repository.eventsFor(authorizationId)).allSatisfy(event ->
                assertThat(event.id()).startsWith("EVT-"));

        // Fixed-sample assertions only: the safe candidate is used unchanged, and each dangerous
        // candidate loses exactly the single character that would have extended its digit run.
        // This does not claim global uniqueness or preserved 128-bit output.
        assertThat(payload(authorizationId)).isNotEqualTo(DANGEROUS);
        assertThat(differences(DANGEROUS, payload(authorizationId))).isEqualTo(1);
        assertThat(payload(record.dispatchCorrelationId())).isEqualTo(SAFE);
        assertThat(longestDigitRun(payload(authorizationId)))
                .isLessThanOrEqualTo(12);
        assertThat(longestDigitRun(payload(record.dispatchCorrelationId())))
                .isLessThanOrEqualTo(12);

        // The fix does not come from weakening the sanitizer, so the other heuristics stay live.
        assertThatThrownBy(() -> EventSanitizer.requireSafe("detail", "PIN BLOCK"))
                .isInstanceOf(EventValidationException.class);
        assertThatThrownBy(() -> EventSanitizer.requireSafe("detail",
                "eyJhbGciOiJIUzI1NiJ9.payload.signature"))
                .isInstanceOf(EventValidationException.class);
    }

    private static String payload(String identifier) {
        return identifier.substring(identifier.indexOf('-') + 1);
    }

    private static int longestDigitRun(String value) {
        int longest = 0;
        int run = 0;
        for (int i = 0; i < value.length(); i++) {
            if (Character.isDigit(value.charAt(i))) {
                run++;
                longest = Math.max(longest, run);
            } else {
                run = 0;
            }
        }
        return longest;
    }

    private static int differences(String left, String right) {
        int count = 0;
        for (int i = 0; i < left.length(); i++) {
            if (left.charAt(i) != right.charAt(i)) {
                count++;
            }
        }
        return count;
    }

    private static PurchaseRequest request() {
        return new PurchaseRequest("MERCH-SIM-001", "TERM-SIM-001", "PURCHASE-1",
                new BigDecimal("10.00"), "USD", "SIM-APP-001", EntryMode.SIMULATED_CONTACT,
                CvmIndicator.SIMULATED_VERIFIED, true, null, null);
    }

    /** Minimal in-memory port double: the test asserts generated values, not storage behavior. */
    private static final class InMemoryRepository implements AuthorizationRepository {

        private final Map<String, AuthorizationRecord> records = new LinkedHashMap<>();
        private final List<EvidenceEvent> events = new ArrayList<>();
        private final List<RejectionAudit> rejections = new ArrayList<>();

        @Override public void claim(AuthorizationRecord record) {
            records.put(record.id(), record);
        }

        @Override public void markDispatched(String authorizationId, String correlationId,
                Instant dispatchedAt) {
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
            rejections.add(audit);
        }
    }
}
