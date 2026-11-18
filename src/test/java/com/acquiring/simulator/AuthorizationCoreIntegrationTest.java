package com.acquiring.simulator;

import static org.assertj.core.api.Assertions.assertThat;

import com.acquiring.simulator.domain.AuthorizationService;
import com.acquiring.simulator.domain.model.AuthorizationRecord;
import com.acquiring.simulator.domain.model.AuthorizationResult;
import com.acquiring.simulator.domain.model.AuthorizationState;
import com.acquiring.simulator.domain.model.AuthorizedContext;
import com.acquiring.simulator.domain.model.CvmIndicator;
import com.acquiring.simulator.domain.model.DecisionSource;
import com.acquiring.simulator.domain.model.DownstreamReply;
import com.acquiring.simulator.domain.model.EntryMode;
import com.acquiring.simulator.domain.model.EvidenceEvent;
import com.acquiring.simulator.domain.model.IssuerDecision;
import com.acquiring.simulator.domain.model.Outcome;
import com.acquiring.simulator.domain.model.PurchaseRequest;
import com.acquiring.simulator.domain.model.RejectionReason;
import com.acquiring.simulator.domain.model.RiskIndicator;
import com.acquiring.simulator.domain.model.Route;
import com.acquiring.simulator.domain.port.AuthorizationRepository;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest
@ActiveProfiles("local")
@Import(TestDoublesConfiguration.class)
class AuthorizationCoreIntegrationTest {

    private static final String ACQUIRER = "ACQ-SIM-001";
    private static final String MERCHANT = "MERCH-SIM-001";
    private static final String TERMINAL = "TERM-SIM-001";
    private static final AuthorizedContext CONTEXT = new AuthorizedContext(
            ACQUIRER, MERCHANT, "CALLER-SIM-001", Set.of(Route.DOMESTIC, Route.INTERNATIONAL));
    private static final AuthorizedContext CONTEXT_MERCHANT_003 = new AuthorizedContext(
            ACQUIRER, "MERCH-SIM-003", "CALLER-SIM-001", Set.of(Route.DOMESTIC));
    private static final AuthorizedContext CONTEXT_INTL = new AuthorizedContext(
            "ACQ-SIM-INTL", "MERCH-SIM-002", "CALLER-SIM-002", Set.of(Route.INTERNATIONAL));

    private static final java.nio.file.Path DATABASE = TestDoublesConfiguration.temporaryDatabase();
    private static final AtomicInteger CHANNEL_SEQUENCE = new AtomicInteger();

    @Autowired
    private AuthorizationService service;

    @Autowired
    private AuthorizationRepository repository;

    @Autowired
    private StubIssuerSimulator simulator;

    @Autowired
    private MutableClock clock;

    @Autowired
    private JdbcClient jdbc;

    private ExecutorService executor;

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",
                () -> "jdbc:sqlite:" + DATABASE.toAbsolutePath() + "?busy_timeout=5000");
        registry.add("spring.datasource.driver-class-name", () -> "org.sqlite.JDBC");
        registry.add("spring.datasource.username", () -> "");
        registry.add("spring.datasource.password", () -> "");
    }

    @BeforeEach
    void setUp() {
        clock.setInstant(TestDoublesConfiguration.BASE);
        simulator.reset();
        executor = Executors.newFixedThreadPool(4);
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    // A01
    @Test
    void approvesValidPurchaseWithIssuerProvenance() {
        AuthorizationResult result = service.authorize(CONTEXT, request("SIM-APP-001", "10.00"));

        assertThat(result.outcome()).isEqualTo(Outcome.APPROVED);
        assertThat(result.reference()).isNotBlank();
        AuthorizationRecord record = repository.findById(result.reference()).orElseThrow();
        assertThat(record.state()).isEqualTo(AuthorizationState.APPROVED);
        assertThat(record.decisionSource()).isEqualTo(DecisionSource.ISSUER);
        assertThat(record.route()).isEqualTo(Route.DOMESTIC);
        assertThat(events(result.reference())).extracting(EvidenceEvent::eventType)
                .contains("CONFIRMATION");
    }

    // A02
    @Test
    void distinguishesIssuerDeclineFromProcessorRejection() {
        AuthorizationResult result = service.authorize(CONTEXT, request("SIM-DEC-001", "10.00"));

        assertThat(result.outcome()).isEqualTo(Outcome.ISSUER_DECLINED);
        AuthorizationRecord record = repository.findById(result.reference()).orElseThrow();
        assertThat(record.state()).isEqualTo(AuthorizationState.DECLINED);
        assertThat(record.decisionSource()).isEqualTo(DecisionSource.ISSUER);
    }

    // A03
    @Test
    void rejectsCallerWithoutAuthorityOverTheMerchant() {
        PurchaseRequest request = request("SIM-APP-001", "10.00");

        AuthorizationResult result = service.authorize(CONTEXT_MERCHANT_003, request);

        assertThat(result.outcome()).isEqualTo(Outcome.PROCESSOR_REJECTED);
        assertThat(result.reference()).isNull();
        assertThat(simulator.dispatchCount()).isZero();
    }

    // A04
    @Test
    void rejectsEachMalformedOrOutOfProfileInputBeforeDispatch() {
        List<PurchaseRequest> invalid = List.of(
                requestWithCredential(null),
                requestWithCurrency("EUR"),
                requestWithAmount("0.99"),
                requestWithAmount("10000.00"),
                requestWithAmount("10.001"),
                requestWithCvm(null),
                requestWithCryptogram(null));

        for (PurchaseRequest request : invalid) {
            AuthorizationResult result = service.authorize(CONTEXT, request);
            assertThat(result.outcome()).isEqualTo(Outcome.PROCESSOR_REJECTED);
            assertThat(result.reference()).isNull();
            assertThat(reasonFor(request.channelPaymentReference())).isNotNull();
        }
        assertThat(simulator.dispatchCount()).isZero();
    }

    // A05
    @Test
    void rejectsUnknownMerchantAndUnknownTerminal() {
        AuthorizedContext unknownMerchantContext = new AuthorizedContext(
                ACQUIRER, "MERCH-NOPE", "CALLER-SIM-001", Set.of(Route.DOMESTIC));
        PurchaseRequest unknownMerchant = new PurchaseRequest(
                "MERCH-NOPE", TERMINAL, nextChannel(), new BigDecimal("10.00"), "USD",
                "SIM-APP-001", EntryMode.SIMULATED_CONTACT, CvmIndicator.SIMULATED_VERIFIED,
                true, null, null);
        assertThat(service.authorize(unknownMerchantContext, unknownMerchant).outcome())
                .isEqualTo(Outcome.PROCESSOR_REJECTED);
        assertThat(reasonFor(unknownMerchant.channelPaymentReference()))
                .isEqualTo(RejectionReason.UNKNOWN_MERCHANT.name());

        PurchaseRequest unknownTerminal = new PurchaseRequest(
                MERCHANT, "TERM-NOPE", nextChannel(), new BigDecimal("10.00"), "USD",
                "SIM-APP-001", EntryMode.SIMULATED_CONTACT, CvmIndicator.SIMULATED_VERIFIED,
                true, null, null);
        assertThat(service.authorize(CONTEXT, unknownTerminal).outcome())
                .isEqualTo(Outcome.PROCESSOR_REJECTED);
        assertThat(reasonFor(unknownTerminal.channelPaymentReference()))
                .isEqualTo(RejectionReason.UNKNOWN_TERMINAL.name());
        assertThat(simulator.dispatchCount()).isZero();
    }

    // A06
    @Test
    void rejectsUnknownAcquirerWithoutUsingAnotherMandate() {
        AuthorizedContext wrongAcquirer = new AuthorizedContext(
                "ACQ-NOPE", MERCHANT, "CALLER-SIM-001", Set.of(Route.DOMESTIC));

        AuthorizationResult result = service.authorize(wrongAcquirer, request("SIM-APP-001", "10.00"));

        assertThat(result.outcome()).isEqualTo(Outcome.PROCESSOR_REJECTED);
        assertThat(simulator.dispatchCount()).isZero();
    }

    // A07 / A23
    @Test
    void timeoutAndAmbiguousReplyRemainNotConfirmed() {
        AuthorizationResult timeout = service.authorize(CONTEXT, request("SIM-TMO-001", "10.00"));
        assertThat(timeout.outcome()).isEqualTo(Outcome.NOT_CONFIRMED);
        assertThat(timeout.reference()).isNotBlank();
        assertThat(repository.findById(timeout.reference()).orElseThrow().state())
                .isEqualTo(AuthorizationState.NOT_CONFIRMED);

        simulator.behaveAs(request -> new DownstreamReply(
                request.correlationId(), IssuerDecision.MALFORMED, clock.instant()));
        AuthorizationResult malformed = service.authorize(CONTEXT, request("SIM-APP-002", "10.00"));
        assertThat(malformed.outcome()).isEqualTo(Outcome.NOT_CONFIRMED);

        simulator.behaveAs(request -> new DownstreamReply(
                "CORR-WRONG", IssuerDecision.APPROVED, clock.instant()));
        AuthorizationResult wrongCorrelation = service.authorize(CONTEXT, request("SIM-APP-003", "10.00"));
        assertThat(wrongCorrelation.outcome()).isEqualTo(Outcome.NOT_CONFIRMED);
        assertThat(repository.findById(wrongCorrelation.reference()).orElseThrow().state())
                .isNotEqualTo(AuthorizationState.APPROVED);
    }

    // A08
    @Test
    void identicalReplayReturnsOriginalReferenceWithoutRedispatch() {
        PurchaseRequest request = request("SIM-APP-010", "10.00");
        AuthorizationResult first = service.authorize(CONTEXT, request);
        AuthorizationResult replay = service.authorize(CONTEXT, request);

        assertThat(first.outcome()).isEqualTo(Outcome.APPROVED);
        assertThat(replay).isEqualTo(first);
        assertThat(simulator.dispatchCount()).isEqualTo(1);
    }

    // A09 / A14 / A25
    @Test
    void lateConfirmationUpdatesLatestResultAndPreservesUncertainty() {
        PurchaseRequest request = request("SIM-TMO-002", "10.00");
        AuthorizationResult unknown = service.authorize(CONTEXT, request);
        assertThat(unknown.outcome()).isEqualTo(Outcome.NOT_CONFIRMED);
        AuthorizationRecord pending = repository.findById(unknown.reference()).orElseThrow();

        clock.advance(Duration.ofSeconds(31));
        service.submitEvidence(unknown.reference(), new DownstreamReply(
                pending.dispatchCorrelationId(), IssuerDecision.APPROVED, clock.instant()));

        assertThat(repository.findById(unknown.reference()).orElseThrow().state())
                .isEqualTo(AuthorizationState.APPROVED);
        assertThat(events(unknown.reference())).extracting(EvidenceEvent::eventType)
                .contains("TIMEOUT", "LATE_CONFIRMATION");
        assertThat(events(unknown.reference())).extracting(EvidenceEvent::eventType)
                .doesNotContain("ISSUER_DECLINED");

        PurchaseRequest linked = linkedRequest(request, unknown.reference());
        AuthorizationResult retry = service.authorize(CONTEXT, linked);
        assertThat(retry.outcome()).isEqualTo(Outcome.APPROVED);
        assertThat(retry.reference()).isEqualTo(unknown.reference());
        assertThat(simulator.dispatchCount()).isEqualTo(1);
    }

    // A12 / A18
    @Test
    void invalidOrForeignPriorReferenceRejectsBeforeReplayDisclosure() {
        PurchaseRequest original = request("SIM-APP-011", "10.00");
        AuthorizationResult approved = service.authorize(CONTEXT, original);

        AuthorizationResult unknownLink = service.authorize(CONTEXT,
                linkedRequest(original, "AUTH-DOES-NOT-EXIST"));
        assertThat(unknownLink.outcome()).isEqualTo(Outcome.PROCESSOR_REJECTED);
        assertThat(unknownLink.reference()).isNull();
        assertThat(reasonFor(original.channelPaymentReference()))
                .isEqualTo(RejectionReason.INVALID_PRIOR_REFERENCE.name());

        PurchaseRequest foreignRequest = new PurchaseRequest(
                "MERCH-SIM-003", "TERM-SIM-003", original.channelPaymentReference(),
                original.amount(), "USD", original.credentialRef(), original.entryMode(),
                original.cvmIndicator(), original.cryptogramPresent(), null, approved.reference());
        AuthorizationResult foreign = service.authorize(CONTEXT_MERCHANT_003, foreignRequest);
        assertThat(foreign.outcome()).isEqualTo(Outcome.PROCESSOR_REJECTED);
        assertThat(foreign.reference()).isNull();
        assertThat(reasonFor(original.channelPaymentReference()))
                .isEqualTo(RejectionReason.FOREIGN_PRIOR_REFERENCE.name());
        assertThat(simulator.dispatchCount()).isEqualTo(1);
        assertThat(repository.findById(approved.reference()).orElseThrow().state())
                .isEqualTo(AuthorizationState.APPROVED);
    }

    // A13
    @Test
    void conflictingDuplicateRejectsWithoutMutatingOriginal() {
        PurchaseRequest original = request("SIM-APP-012", "10.00");
        AuthorizationResult approved = service.authorize(CONTEXT, original);

        PurchaseRequest conflict = new PurchaseRequest(
                MERCHANT, TERMINAL, original.channelPaymentReference(), new BigDecimal("11.00"),
                "USD", "SIM-APP-001", EntryMode.SIMULATED_CONTACT,
                CvmIndicator.SIMULATED_VERIFIED, true, null, null);
        AuthorizationResult result = service.authorize(CONTEXT, conflict);

        assertThat(result.outcome()).isEqualTo(Outcome.PROCESSOR_REJECTED);
        assertThat(result.reference()).isNull();
        assertThat(reasonFor(original.channelPaymentReference()))
                .isEqualTo(RejectionReason.PAYMENT_DATA_CONFLICT.name());
        AuthorizationRecord unchanged = repository.findById(approved.reference()).orElseThrow();
        assertThat(unchanged.amountMinor()).isEqualTo(1000L);
        assertThat(unchanged.state()).isEqualTo(AuthorizationState.APPROVED);
        assertThat(simulator.dispatchCount()).isEqualTo(1);
    }

    // A15
    @Test
    void noPermittedRouteRejectsBeforeDispatch() {
        AuthorizedContext noRoutes = new AuthorizedContext(
                ACQUIRER, MERCHANT, "CALLER-SIM-001", Set.of());
        assertThat(service.authorize(noRoutes, request("SIM-APP-013", "10.00")).outcome())
                .isEqualTo(Outcome.PROCESSOR_REJECTED);

        PurchaseRequest intlRequest = new PurchaseRequest(
                "MERCH-SIM-002", "TERM-SIM-002", nextChannel(), new BigDecimal("10.00"), "USD",
                "SIM-APP-001", EntryMode.SIMULATED_CONTACT, CvmIndicator.SIMULATED_VERIFIED,
                true, null, null);
        assertThat(service.authorize(CONTEXT_INTL, intlRequest).outcome())
                .isEqualTo(Outcome.PROCESSOR_REJECTED);
        assertThat(simulator.dispatchCount()).isZero();
    }

    // A16
    @Test
    void concurrentIdenticalArrivalsProduceOneAuthorizationAndOneDispatch() throws Exception {
        PurchaseRequest request = request("SIM-APP-014", "10.00");
        CyclicBarrier barrier = new CyclicBarrier(2);
        Future<AuthorizationResult> first = executor.submit(() -> {
            barrier.await(5, TimeUnit.SECONDS);
            return service.authorize(CONTEXT, request);
        });
        Future<AuthorizationResult> second = executor.submit(() -> {
            barrier.await(5, TimeUnit.SECONDS);
            return service.authorize(CONTEXT, request);
        });

        AuthorizationResult a = first.get(10, TimeUnit.SECONDS);
        AuthorizationResult b = second.get(10, TimeUnit.SECONDS);

        assertThat(a.reference()).isEqualTo(b.reference());
        assertThat(a.outcome()).isIn(Outcome.APPROVED, Outcome.NOT_CONFIRMED);
        assertThat(b.outcome()).isIn(Outcome.APPROVED, Outcome.NOT_CONFIRMED);
        assertThat(simulator.dispatchCount()).isEqualTo(1);
    }

    // A17
    @Test
    void inFlightReplayReturnsPendingWithoutWaitingOrSecondDispatch() throws Exception {
        PurchaseRequest request = request("SIM-APP-015", "10.00");
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        simulator.gate(entered, release);
        Future<AuthorizationResult> first = executor.submit(() -> service.authorize(CONTEXT, request));
        assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();

        AuthorizationResult replay = service.authorize(CONTEXT, request);
        assertThat(replay.outcome()).isEqualTo(Outcome.NOT_CONFIRMED);
        assertThat(replay.reference()).isNotBlank();
        assertThat(simulator.dispatchCount()).isEqualTo(1);

        release.countDown();
        AuthorizationResult original = first.get(10, TimeUnit.SECONDS);
        assertThat(original.outcome()).isEqualTo(Outcome.APPROVED);
        assertThat(original.reference()).isEqualTo(replay.reference());
        assertThat(service.authorize(CONTEXT, request).outcome()).isEqualTo(Outcome.APPROVED);
    }

    // A19
    @Test
    void linkedChangedDataIsConflictButMandatoryValidationComesFirst() {
        PurchaseRequest original = request("SIM-APP-016", "10.00");
        AuthorizationResult approved = service.authorize(CONTEXT, original);

        clock.advance(Duration.ofSeconds(1));
        PurchaseRequest changedAmount = new PurchaseRequest(
                MERCHANT, TERMINAL, original.channelPaymentReference(), new BigDecimal("11.00"),
                "USD", "SIM-APP-001", EntryMode.SIMULATED_CONTACT,
                CvmIndicator.SIMULATED_VERIFIED, true, null, approved.reference());
        assertThat(service.authorize(CONTEXT, changedAmount).outcome())
                .isEqualTo(Outcome.PROCESSOR_REJECTED);
        assertThat(reasonFor(original.channelPaymentReference()))
                .isEqualTo(RejectionReason.PAYMENT_DATA_CONFLICT.name());

        clock.advance(Duration.ofSeconds(1));
        PurchaseRequest invalidAmount = new PurchaseRequest(
                MERCHANT, TERMINAL, original.channelPaymentReference(), new BigDecimal("0.99"),
                "USD", "SIM-APP-001", EntryMode.SIMULATED_CONTACT,
                CvmIndicator.SIMULATED_VERIFIED, true, null, approved.reference());
        assertThat(service.authorize(CONTEXT, invalidAmount).outcome())
                .isEqualTo(Outcome.PROCESSOR_REJECTED);
        assertThat(reasonFor(original.channelPaymentReference()))
                .isEqualTo(RejectionReason.MISSING_OR_INVALID_INPUT.name());
        assertThat(simulator.dispatchCount()).isEqualTo(1);
        assertThat(repository.findById(approved.reference()).orElseThrow().amountMinor())
                .isEqualTo(1000L);
    }

    // A20
    @Test
    void linkedRetryWithChangedBusinessKeyRejects() {
        PurchaseRequest original = request("SIM-APP-017", "10.00");
        AuthorizationResult approved = service.authorize(CONTEXT, original);

        PurchaseRequest changedKey = new PurchaseRequest(
                MERCHANT, TERMINAL, nextChannel(), original.amount(), "USD",
                original.credentialRef(), original.entryMode(), original.cvmIndicator(),
                original.cryptogramPresent(), null, approved.reference());
        AuthorizationResult result = service.authorize(CONTEXT, changedKey);

        assertThat(result.outcome()).isEqualTo(Outcome.PROCESSOR_REJECTED);
        assertThat(reasonFor(changedKey.channelPaymentReference()))
                .isEqualTo(RejectionReason.INCOMPATIBLE_LINKED_RETRY.name());
        assertThat(simulator.dispatchCount()).isEqualTo(1);
    }

    // A21
    @Test
    void linkedRetrySurvivesWindowExpiryAtEveryBoundary() {
        PurchaseRequest original = request("SIM-APP-018", "10.00");
        AuthorizationResult approved = service.authorize(CONTEXT, original);
        PurchaseRequest linked = linkedRequest(original, approved.reference());

        clock.advance(Duration.ofHours(24).minusMillis(1));
        assertThat(service.authorize(CONTEXT, linked).reference()).isEqualTo(approved.reference());
        clock.advance(Duration.ofMillis(1));
        assertThat(service.authorize(CONTEXT, linked).outcome()).isEqualTo(Outcome.APPROVED);
        assertThat(service.authorize(CONTEXT, linked).reference()).isEqualTo(approved.reference());
        clock.advance(Duration.ofHours(2));
        assertThat(service.authorize(CONTEXT, linked).reference()).isEqualTo(approved.reference());
        assertThat(simulator.dispatchCount()).isEqualTo(1);
    }

    // A22
    @Test
    void unlinkedBoundaryCreatesNewPaymentAtExactWindowEnd() {
        PurchaseRequest original = request("SIM-APP-019", "10.00");
        AuthorizationResult first = service.authorize(CONTEXT, original);

        clock.advance(Duration.ofHours(24).minusMillis(1));
        assertThat(service.authorize(CONTEXT, original).reference()).isEqualTo(first.reference());
        assertThat(simulator.dispatchCount()).isEqualTo(1);

        clock.advance(Duration.ofMillis(1));
        AuthorizationResult renewed = service.authorize(CONTEXT, original);
        assertThat(renewed.reference()).isNotEqualTo(first.reference());
        assertThat(repository.findById(renewed.reference()).orElseThrow().generation()).isEqualTo(2);
        assertThat(simulator.dispatchCount()).isEqualTo(2);

        PurchaseRequest later = request("SIM-APP-020", "10.00");
        AuthorizationResult laterFirst = service.authorize(CONTEXT, later);
        clock.advance(Duration.ofHours(25));
        AuthorizationResult laterRenewed = service.authorize(CONTEXT, later);
        assertThat(laterRenewed.reference()).isNotEqualTo(laterFirst.reference());
        assertThat(repository.findById(laterFirst.reference()).orElseThrow().state())
                .isEqualTo(AuthorizationState.APPROVED);
    }

    // A23 contradictory evidence
    @Test
    void contradictoryLaterConfirmationIsRetainedWithoutReplacingDecision() {
        AuthorizationResult approved = service.authorize(CONTEXT, request("SIM-APP-021", "10.00"));
        AuthorizationRecord record = repository.findById(approved.reference()).orElseThrow();

        service.submitEvidence(approved.reference(), new DownstreamReply(
                record.dispatchCorrelationId(), IssuerDecision.DECLINED, clock.instant()));

        assertThat(repository.findById(approved.reference()).orElseThrow().state())
                .isEqualTo(AuthorizationState.APPROVED);
        assertThat(events(approved.reference())).extracting(EvidenceEvent::eventType)
                .contains("CONTRADICTORY_CONFIRMATION");
    }

    // A24
    @Test
    void confirmationExactlyAtDeadlineWinsOverTimeout() {
        simulator.behaveAs(request -> new DownstreamReply(
                request.correlationId(), IssuerDecision.APPROVED,
                request.dispatchedAt().plus(request.responseTimeout())));
        AuthorizationResult result = service.authorize(CONTEXT, request("SIM-APP-022", "10.00"));

        assertThat(result.outcome()).isEqualTo(Outcome.APPROVED);
        AuthorizationRecord record = repository.findById(result.reference()).orElseThrow();
        AuthorizationRecord afterBoundary = service.evaluateDeadline(
                result.reference(), record.dispatchedAt().plus(Duration.ofSeconds(30))).orElseThrow();
        assertThat(afterBoundary.state()).isEqualTo(AuthorizationState.APPROVED);
        assertThat(events(result.reference())).extracting(EvidenceEvent::eventType)
                .doesNotContain("TIMEOUT");
    }

    // A25 combined retry after late confirmation
    @Test
    void combinedRetryAfterLateConfirmationReturnsLatestButRejectsConflict() {
        PurchaseRequest original = request("SIM-TMO-003", "10.00");
        AuthorizationResult unknown = service.authorize(CONTEXT, original);
        AuthorizationRecord pending = repository.findById(unknown.reference()).orElseThrow();
        clock.advance(Duration.ofSeconds(31));
        service.submitEvidence(unknown.reference(), new DownstreamReply(
                pending.dispatchCorrelationId(), IssuerDecision.DECLINED, clock.instant()));

        assertThat(service.authorize(CONTEXT, linkedRequest(original, unknown.reference())).outcome())
                .isEqualTo(Outcome.ISSUER_DECLINED);

        PurchaseRequest changed = new PurchaseRequest(
                MERCHANT, TERMINAL, original.channelPaymentReference(), new BigDecimal("12.00"),
                "USD", "SIM-APP-001", EntryMode.SIMULATED_CONTACT,
                CvmIndicator.SIMULATED_VERIFIED, true, null, unknown.reference());
        assertThat(service.authorize(CONTEXT, changed).outcome())
                .isEqualTo(Outcome.PROCESSOR_REJECTED);
        assertThat(simulator.dispatchCount()).isEqualTo(1);
    }

    // A26
    @Test
    void profileEdgesAndDefaultsAreEnforced() {
        assertThat(service.authorize(CONTEXT, request("SIM-APP-023", "1.00")).outcome())
                .isEqualTo(Outcome.APPROVED);
        assertThat(service.authorize(CONTEXT, request("SIM-APP-024", "9999.99")).outcome())
                .isEqualTo(Outcome.APPROVED);

        PurchaseRequest falseCryptogram = new PurchaseRequest(
                MERCHANT, TERMINAL, nextChannel(), new BigDecimal("10.00"), "USD", "SIM-APP-025",
                EntryMode.SIMULATED_CONTACTLESS, CvmIndicator.SIMULATED_NOT_REQUIRED,
                false, null, null);
        assertThat(service.authorize(CONTEXT, falseCryptogram).outcome())
                .isEqualTo(Outcome.APPROVED);

        PurchaseRequest riskReject = new PurchaseRequest(
                MERCHANT, TERMINAL, nextChannel(), new BigDecimal("10.00"), "USD", "SIM-APP-026",
                EntryMode.SIMULATED_CONTACT, CvmIndicator.SIMULATED_VERIFIED,
                true, RiskIndicator.REJECT, null);
        assertThat(service.authorize(CONTEXT, riskReject).outcome())
                .isEqualTo(Outcome.PROCESSOR_REJECTED);

        assertThat(service.authorize(CONTEXT, requestWithCredential("SIM-XXX-001")).outcome())
                .isEqualTo(Outcome.PROCESSOR_REJECTED);
        assertThat(service.authorize(CONTEXT, requestWithAmount("1.001")).outcome())
                .isEqualTo(Outcome.PROCESSOR_REJECTED);
        assertThat(service.authorize(CONTEXT, requestWithEntryMode(null)).outcome())
                .isEqualTo(Outcome.PROCESSOR_REJECTED);
    }

    @Test
    void retainedClaimWithoutDispatchIsNeverRedispatchedAfterRestart() {
        String channel = nextChannel();
        Instant now = clock.instant();
        String businessKey = ACQUIRER + "|" + MERCHANT + "|" + channel;
        AuthorizationRecord orphan = new AuthorizationRecord(
                "AUTH-ORPHAN", businessKey, ACQUIRER, MERCHANT, channel, 1000L, "USD",
                "SIM-APP-001", TERMINAL, EntryMode.SIMULATED_CONTACT,
                CvmIndicator.SIMULATED_VERIFIED, true, RiskIndicator.PASS, Route.DOMESTIC,
                fingerprint(1000L), 1, AuthorizationState.AWAITING, DecisionSource.NONE,
                null, null, now, null, null, now.plus(Duration.ofHours(24)));
        repository.claim(orphan);

        AuthorizationResult result = service.authorize(CONTEXT,
                new PurchaseRequest(MERCHANT, TERMINAL, channel, new BigDecimal("10.00"), "USD",
                        "SIM-APP-001", EntryMode.SIMULATED_CONTACT,
                        CvmIndicator.SIMULATED_VERIFIED, true, null, null));

        assertThat(result.outcome()).isEqualTo(Outcome.NOT_CONFIRMED);
        assertThat(result.reference()).isEqualTo("AUTH-ORPHAN");
        assertThat(simulator.dispatchCount()).isZero();
    }

    private PurchaseRequest request(String credential, String amount) {
        return new PurchaseRequest(MERCHANT, TERMINAL, nextChannel(), new BigDecimal(amount),
                "USD", credential, EntryMode.SIMULATED_CONTACT,
                CvmIndicator.SIMULATED_VERIFIED, true, null, null);
    }

    private PurchaseRequest linkedRequest(PurchaseRequest base, String reference) {
        return new PurchaseRequest(base.merchantRef(), base.terminalRef(),
                base.channelPaymentReference(), base.amount(), base.currency(),
                base.credentialRef(), base.entryMode(), base.cvmIndicator(),
                base.cryptogramPresent(), base.riskIndicator(), reference);
    }

    private PurchaseRequest requestWithCredential(String credential) {
        return new PurchaseRequest(MERCHANT, TERMINAL, nextChannel(), new BigDecimal("10.00"),
                "USD", credential, EntryMode.SIMULATED_CONTACT,
                CvmIndicator.SIMULATED_VERIFIED, true, null, null);
    }

    private PurchaseRequest requestWithCurrency(String currency) {
        return new PurchaseRequest(MERCHANT, TERMINAL, nextChannel(), new BigDecimal("10.00"),
                currency, "SIM-APP-001", EntryMode.SIMULATED_CONTACT,
                CvmIndicator.SIMULATED_VERIFIED, true, null, null);
    }

    private PurchaseRequest requestWithAmount(String amount) {
        return new PurchaseRequest(MERCHANT, TERMINAL, nextChannel(), new BigDecimal(amount),
                "USD", "SIM-APP-001", EntryMode.SIMULATED_CONTACT,
                CvmIndicator.SIMULATED_VERIFIED, true, null, null);
    }

    private PurchaseRequest requestWithCvm(CvmIndicator cvm) {
        return new PurchaseRequest(MERCHANT, TERMINAL, nextChannel(), new BigDecimal("10.00"),
                "USD", "SIM-APP-001", EntryMode.SIMULATED_CONTACT, cvm, true, null, null);
    }

    private PurchaseRequest requestWithCryptogram(Boolean present) {
        return new PurchaseRequest(MERCHANT, TERMINAL, nextChannel(), new BigDecimal("10.00"),
                "USD", "SIM-APP-001", EntryMode.SIMULATED_CONTACT,
                CvmIndicator.SIMULATED_VERIFIED, present, null, null);
    }

    private PurchaseRequest requestWithEntryMode(EntryMode entryMode) {
        return new PurchaseRequest(MERCHANT, TERMINAL, nextChannel(), new BigDecimal("10.00"),
                "USD", "SIM-APP-001", entryMode, CvmIndicator.SIMULATED_VERIFIED, true, null, null);
    }

    private String reasonFor(String channel) {
        return jdbc.sql("""
                SELECT reason_category FROM rejection_audit
                 WHERE channel_payment_ref = :channel
                 ORDER BY recorded_at DESC, id DESC
                 LIMIT 1
                """)
                .param("channel", channel)
                .query(String.class)
                .optional()
                .orElse(null);
    }

    private List<EvidenceEvent> events(String authorizationId) {
        return repository.eventsFor(authorizationId);
    }

    private static String nextChannel() {
        return "CH-" + CHANNEL_SEQUENCE.incrementAndGet() + "-"
                + UUID.randomUUID().toString().substring(0, 8);
    }

    private static String fingerprint(long amountMinor) {
        String canonical = String.join("|", Long.toString(amountMinor), "USD", "SIM-APP-001",
                TERMINAL, EntryMode.SIMULATED_CONTACT.name(), CvmIndicator.SIMULATED_VERIFIED.name(),
                "true", RiskIndicator.PASS.name());
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
