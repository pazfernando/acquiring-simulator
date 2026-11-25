package com.acquiring.simulator.domain;

import com.acquiring.simulator.config.SimulatorProperties;
import com.acquiring.simulator.domain.event.CommittedVersionEvidence;
import com.acquiring.simulator.domain.event.SafeIdentifier;
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
import com.acquiring.simulator.domain.model.LateEvidenceAdmission;
import com.acquiring.simulator.domain.model.LateEvidenceOutcome;
import com.acquiring.simulator.domain.model.Outcome;
import com.acquiring.simulator.domain.model.PurchaseRequest;
import com.acquiring.simulator.domain.model.RejectionAudit;
import com.acquiring.simulator.domain.model.RejectionReason;
import com.acquiring.simulator.domain.model.RiskIndicator;
import com.acquiring.simulator.domain.model.Route;
import com.acquiring.simulator.domain.model.SimulatedDecisionRequest;
import com.acquiring.simulator.domain.port.AuthorizationRepository;
import com.acquiring.simulator.domain.port.ClaimGateway;
import com.acquiring.simulator.domain.port.DispatchIntentGateway;
import com.acquiring.simulator.domain.port.DuplicateClaimException;
import com.acquiring.simulator.domain.port.IssuerSimulator;
import com.acquiring.simulator.domain.port.LateEvidenceGateway;
import com.acquiring.simulator.domain.port.NetworkResultGateway;
import com.acquiring.simulator.domain.port.ResultGateway;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Operation A authorization core. Financial effects are simulated only; reversal, inquiry and automatic redispatch remain out of scope.
 */
@Service
public class AuthorizationService {

    private static final String CURRENCY = "USD";
    private static final long MIN_AMOUNT_MINOR = 100L;
    private static final long MAX_AMOUNT_MINOR = 999_999L;

    /**
     * Rejection audits share the caller clock and can therefore land in the same millisecond.
     * Leading with the timestamp and a process-monotonic ordinal keeps append order
     * deterministic under that tie, while the random suffix preserves identifier uniqueness.
     */
    private static final AtomicLong REJECTION_SEQUENCE = new AtomicLong();

    private final AuthorizationRepository repository;
    private final IssuerSimulator issuerSimulator;
    private final SimulatorProperties properties;
    private final Clock clock;
    /**
     * Optional atomic claim port. When absent the legacy {@code repository.claim()} path is used
     * unchanged (local SQL harness and the current AWS runtime). When present the service performs
     * exactly one {@link ClaimGateway#claim} call and never falls back to the legacy claim.
     */
    private final ClaimGateway claimGateway;
    /**
     * Optional atomic dispatch-intent port. It may only be present together with {@link
     * #claimGateway}: the intent composer conditions on the committed claim version, which the
     * legacy {@code repository.claim()} path does not persist. When absent, the legacy
     * {@code markDispatched}-then-send path is used unchanged.
     */
    private final DispatchIntentGateway dispatchIntentGateway;
    /**
     * Optional atomic verified-result port. It may only be present together with {@link
     * #claimGateway} and {@link #dispatchIntentGateway}: the result composer conditions on the
     * durable dispatch intent. When present, one confirmed correlated issuer reply is committed
     * exactly once through {@link ResultGateway#recordResult} and the reported outcome is derived
     * from the persisted confirmed state and committed-version evidence. No branch falls back to
     * {@code updateOutcome}, a legacy event append, a resend or a reversal. When absent, the legacy
     * result path is unchanged.
     */
    private final ResultGateway resultGateway;
    /**
     * Optional atomic explicit-network-rejection port. It may only be present together with the
     * atomic claim, dispatch-intent and result gateways, because the network branch runs on the
     * atomic result path after a durable intent. When present, one valid, correlated, explicit
     * pre-issuer {@code NETWORK_REJECTED} reply is committed exactly once through
     * {@link NetworkResultGateway#recordNetworkRejection}, and only persisted
     * {@code NETWORK_REJECTED} state with committed-version evidence may be reported as
     * {@code PROCESSOR_REJECTED}. Unknown, failed, absent or conflicting evidence stays
     * {@code NOT_CONFIRMED}; no branch falls back to {@code updateOutcome}, a legacy event append,
     * a retry, a resend or a reversal, and no issuer task or issuer decline is fabricated.
     */
    private final NetworkResultGateway networkResultGateway;
    /**
     * Optional internal verified late-evidence port. It may only be present together with the atomic
     * claim, dispatch-intent and result gateways, because the late composer conditions on the durable
     * intent and the normal-admission branch reuses the verified-result port. When present, one
     * {@link LateEvidenceGateway#submit} call is made per service invocation and the reported outcome
     * comes from persisted admission knowledge. There is no public endpoint, no automatic financial
     * recovery, no legacy fallback and no {@code AwsRuntime} activation.
     */
    private final LateEvidenceGateway lateEvidenceGateway;
    /**
     * Seam for the service's own identifiers. Production uses random hexadecimal; tests supply a
     * controllable source. Every generated identifier is normalized by {@link SafeIdentifier} so it
     * cannot be mistaken for payment data by the event sanitizer.
     */
    private final IdentifierSource identifiers;

    /** Spring/local harness wiring: no atomic claim port, legacy claim path retained. */
    @Autowired
    public AuthorizationService(
            AuthorizationRepository repository,
            IssuerSimulator issuerSimulator,
            SimulatorProperties properties,
            Clock clock) {
        this(repository, issuerSimulator, properties, clock, null);
    }

    public AuthorizationService(
            AuthorizationRepository repository,
            IssuerSimulator issuerSimulator,
            SimulatorProperties properties,
            Clock clock,
            ClaimGateway claimGateway) {
        this(repository, issuerSimulator, properties, clock, claimGateway, null,
                IdentifierSource.randomHex());
    }

    public AuthorizationService(
            AuthorizationRepository repository,
            IssuerSimulator issuerSimulator,
            SimulatorProperties properties,
            Clock clock,
            ClaimGateway claimGateway,
            IdentifierSource identifiers) {
        this(repository, issuerSimulator, properties, clock, claimGateway, null, identifiers);
    }

    public AuthorizationService(
            AuthorizationRepository repository,
            IssuerSimulator issuerSimulator,
            SimulatorProperties properties,
            Clock clock,
            ClaimGateway claimGateway,
            DispatchIntentGateway dispatchIntentGateway,
            IdentifierSource identifiers) {
        this(repository, issuerSimulator, properties, clock, claimGateway, dispatchIntentGateway,
                null, identifiers);
    }

    public AuthorizationService(
            AuthorizationRepository repository,
            IssuerSimulator issuerSimulator,
            SimulatorProperties properties,
            Clock clock,
            ClaimGateway claimGateway,
            DispatchIntentGateway dispatchIntentGateway,
            ResultGateway resultGateway,
            IdentifierSource identifiers) {
        this(repository, issuerSimulator, properties, clock, claimGateway, dispatchIntentGateway,
                resultGateway, null, identifiers);
    }

    public AuthorizationService(
            AuthorizationRepository repository,
            IssuerSimulator issuerSimulator,
            SimulatorProperties properties,
            Clock clock,
            ClaimGateway claimGateway,
            DispatchIntentGateway dispatchIntentGateway,
            ResultGateway resultGateway,
            NetworkResultGateway networkResultGateway,
            IdentifierSource identifiers) {
        this(repository, issuerSimulator, properties, clock, claimGateway, dispatchIntentGateway,
                resultGateway, networkResultGateway, null, identifiers);
    }

    /**
     * Optional internal late-evidence seam. The port may only be injected together with the atomic
     * claim, dispatch-intent and verified-result gateways; otherwise construction is rejected so a
     * late admission can never run against a legacy record or write a legacy row. This is the
     * constructor {@code AwsRuntime#atomicService} binds for the Lambda runtime, passing the
     * late-evidence gateway alongside the other atomic gateways; the {@code @Autowired} constructor
     * above remains only the Spring/JDBC local harness binding.
     */
    public AuthorizationService(
            AuthorizationRepository repository,
            IssuerSimulator issuerSimulator,
            SimulatorProperties properties,
            Clock clock,
            ClaimGateway claimGateway,
            DispatchIntentGateway dispatchIntentGateway,
            ResultGateway resultGateway,
            NetworkResultGateway networkResultGateway,
            LateEvidenceGateway lateEvidenceGateway,
            IdentifierSource identifiers) {
        if (dispatchIntentGateway != null && claimGateway == null) {
            throw new IllegalArgumentException(
                    "a dispatch-intent gateway requires the atomic claim gateway");
        }
        if (resultGateway != null && (claimGateway == null || dispatchIntentGateway == null)) {
            throw new IllegalArgumentException(
                    "a result gateway requires the atomic claim and dispatch-intent gateways");
        }
        if (networkResultGateway != null
                && (claimGateway == null || dispatchIntentGateway == null
                        || resultGateway == null)) {
            throw new IllegalArgumentException("a network-result gateway requires the atomic claim,"
                    + " dispatch-intent and result gateways");
        }
        if (lateEvidenceGateway != null
                && (claimGateway == null || dispatchIntentGateway == null
                        || resultGateway == null)) {
            throw new IllegalArgumentException("a late-evidence gateway requires the atomic claim,"
                    + " dispatch-intent and result gateways");
        }
        this.repository = repository;
        this.issuerSimulator = issuerSimulator;
        this.properties = properties;
        this.clock = clock;
        this.claimGateway = claimGateway;
        this.dispatchIntentGateway = dispatchIntentGateway;
        this.resultGateway = resultGateway;
        this.networkResultGateway = networkResultGateway;
        this.lateEvidenceGateway = lateEvidenceGateway;
        this.identifiers = identifiers;
    }

    public AuthorizationResult authorize(AuthorizedContext context, PurchaseRequest request) {
        Instant now = clock.instant();
        Validation validation = validate(context, request, now);
        if (validation.reason() != null) {
            return reject(context, request, validation.reason(), now);
        }
        Resolved resolved = validation.resolved();

        String priorReference = request.priorReference();
        if (priorReference != null && !priorReference.isBlank()) {
            return authorizeLinked(context, request, resolved, priorReference, now);
        }
        return authorizeUnlinked(context, request, resolved, now);
    }

    /** Internal operator path for correlated late evidence; never caller-accessible. */
    public void submitEvidence(String authorizationId, DownstreamReply reply) {
        if (lateEvidenceGateway != null) {
            submitVerifiedEvidence(authorizationId, reply);
            return;
        }
        if (resultGateway != null) {
            // Late-evidence admission has no atomic composer installed on this service instance.
            // Fail closed instead of writing legacy outcome/event rows on an atomic-mode service.
            throw new IllegalStateException(
                    "late evidence admission is not implemented on the atomic result path");
        }
        AuthorizationRecord record = repository.findById(authorizationId)
                .orElseThrow(() -> new IllegalArgumentException("unknown authorization reference"));
        Instant now = clock.instant();
        if (reply == null || reply.availableAt() == null || reply.correlationId() == null
                || !reply.correlationId().equals(record.dispatchCorrelationId())
                || reply.decision() == null || reply.decision() == IssuerDecision.MALFORMED) {
            appendEvent(record, "MALFORMED_RESPONSE", null, DecisionSource.NONE,
                    reply == null ? null : reply.correlationId(), "UNRECOGNISED_OR_UNCORRELATED", now);
            if (!isConfirmed(record)) {
                repository.updateOutcome(record.id(), AuthorizationState.NOT_CONFIRMED.name(),
                        DecisionSource.NONE.name(), "MALFORMED_RESPONSE", now);
            }
            return;
        }
        if (reply.decision() == IssuerDecision.NO_RESPONSE) {
            evaluateDeadline(authorizationId, now);
            return;
        }

        if (reply.decision() == IssuerDecision.NETWORK_REJECTED) {
            if (!isConfirmed(record)) {
                repository.updateOutcome(record.id(), AuthorizationState.NETWORK_REJECTED.name(),
                        DecisionSource.NETWORK.name(), "NETWORK_REJECTION", reply.availableAt());
                appendEvent(record, "NETWORK_REJECTION", null, DecisionSource.NETWORK,
                        reply.correlationId(), "NO_ISSUER_DISPATCH", now);
            }
            return;
        }
        AuthorizationState confirmedState = reply.decision() == IssuerDecision.APPROVED
                ? AuthorizationState.APPROVED
                : AuthorizationState.DECLINED;
        if (isConfirmed(record)) {
            String eventType = record.state() == confirmedState
                    ? "DUPLICATE_CONFIRMATION"
                    : "CONTRADICTORY_CONFIRMATION";
            appendEvent(record, eventType, reply.decision().name(), DecisionSource.ISSUER,
                    reply.correlationId(), "CONFIRMED_RESULT_RETAINED", now);
            return;
        }
        boolean late = record.dispatchedAt() != null
                && reply.availableAt().isAfter(record.dispatchedAt().plus(timeout()));
        repository.updateOutcome(record.id(), confirmedState.name(), DecisionSource.ISSUER.name(),
                null, reply.availableAt());
        appendEvent(record, late ? "LATE_CONFIRMATION" : "CONFIRMATION", reply.decision().name(),
                DecisionSource.ISSUER, reply.correlationId(), late ? "AFTER_TIMEOUT" : "ON_TIME",
                now);
    }

    /**
     * Explicit internal verified-evidence seam. One call performs exactly one
     * {@link LateEvidenceGateway#submit} attempt and reports persisted admission knowledge.
     *
     * <p>{@code COMMITTED}/{@code REPLAY}/{@code RECOVERED} return the confirmed outcome and its
     * committed-version evidence. {@code UNKNOWN} stays uncertain and writes nothing.
     * {@code CONTENT_CONFLICT} reads back the authoritative stored outcome instead of overwriting it.
     * A refusal writes nothing. Evidence that became available on time (or was delivered late but
     * was available on time) is routed to the normal verified-result port only when the durable
     * record still satisfies the same trust/context conditions; otherwise the result is explicitly
     * pending and no legacy row is written.
     *
     * <p>The {@link DownstreamReply} is an internal seam only: this method does not verify C4
     * envelopes, signatures, keys or P6 structure, and it does not treat a caller-supplied flag as
     * authentication.
     */
    public LateEvidenceOutcome submitVerifiedEvidence(String authorizationId, DownstreamReply reply) {
        if (lateEvidenceGateway == null) {
            throw new IllegalStateException("late evidence admission gateway is not configured");
        }
        if (isBlank(authorizationId)) {
            throw new IllegalArgumentException("authorizationId is required");
        }
        String id = authorizationId;

        AuthorizationRecord record = null;
        try {
            record = repository.findById(id).orElse(null);
        } catch (RuntimeException unreadable) {
            // The gateway re-reads and classifies an unreadable record as UNKNOWN; do not invent a
            // deadline from a record that could not be read.
        }
        Instant deadline = record == null || record.dispatchedAt() == null
                ? null
                : record.dispatchedAt().plus(timeout());

        LateEvidenceAdmission admission = lateEvidenceGateway.submit(id, reply, deadline,
                clock.instant(), "SVC-" + id);

        return switch (admission.status()) {
            case COMMITTED, REPLAY, RECOVERED -> recordedLateEvidence(admission);
            case NORMAL_RESULT_ADMISSION -> normalResultAdmission(id, record, reply, admission);
            case CONTENT_CONFLICT -> preservedOutcome(id, admission);
            case REFUSED, NOT_COMMITTED, UNKNOWN ->
                    new LateEvidenceOutcome(Outcome.NOT_CONFIRMED, admission);
        };
    }

    /** Report a recorded late confirmation from persisted state and committed-version evidence. */
    private static LateEvidenceOutcome recordedLateEvidence(LateEvidenceAdmission admission) {
        if (!admission.recorded()) {
            return new LateEvidenceOutcome(Outcome.NOT_CONFIRMED, admission);
        }
        return new LateEvidenceOutcome(outcomeOf(admission.confirmedState()), admission);
    }

    /**
     * Evidence that belongs to the normal verified-result path is admitted through the same result
     * port only when the durable record is still an undetermined dispatched intent. A record that is
     * already determined reports its authoritative stored outcome without a second write; a legacy
     * uncertainty record without readable committed evidence stays UNKNOWN with no migration. A
     * failed or incomplete result admission is explicitly pending, never a legacy fallback.
     */
    private LateEvidenceOutcome normalResultAdmission(String authorizationId,
            AuthorizationRecord record, DownstreamReply reply, LateEvidenceAdmission admission) {
        if (record == null) {
            return new LateEvidenceOutcome(Outcome.NOT_CONFIRMED, admission);
        }
        if (record.state() == AuthorizationState.APPROVED
                || record.state() == AuthorizationState.DECLINED
                || record.state() == AuthorizationState.NETWORK_REJECTED) {
            // A duplicate delivery of on-time evidence: the durable result is already retained.
            return new LateEvidenceOutcome(outcomeOf(record.state()), admission);
        }
        if (record.state() == AuthorizationState.NOT_CONFIRMED) {
            return new LateEvidenceOutcome(Outcome.NOT_CONFIRMED, new LateEvidenceAdmission(
                    LateEvidenceAdmission.Status.UNKNOWN, null, null,
                    "legacy uncertainty records are not transitioned; no migration is performed"));
        }
        if (record.dispatchedAt() == null || isBlank(record.dispatchCorrelationId())) {
            // The same trust/context conditions are not established: stay explicitly pending.
            return new LateEvidenceOutcome(Outcome.NOT_CONFIRMED, admission);
        }

        ResultGateway.Decision result;
        try {
            result = resultGateway.recordResult(record, reply);
        } catch (RuntimeException refused) {
            return new LateEvidenceOutcome(Outcome.NOT_CONFIRMED, new LateEvidenceAdmission(
                    LateEvidenceAdmission.Status.UNKNOWN, null, null,
                    "normal verified-result admission could not be completed"));
        }
        LateEvidenceAdmission.Status status =
                LateEvidenceAdmission.Status.valueOf(result.status().name());
        if (result.status() == ResultGateway.Status.CONTENT_CONFLICT) {
            return preservedOutcome(authorizationId, new LateEvidenceAdmission(status, null, null,
                    result.detail()));
        }
        if (!result.recorded()) {
            return new LateEvidenceOutcome(Outcome.NOT_CONFIRMED,
                    new LateEvidenceAdmission(status, null, null, result.detail()));
        }
        return new LateEvidenceOutcome(outcomeOf(result.confirmedState()),
                new LateEvidenceAdmission(status, result.confirmedState(), result.evidence(),
                        result.detail()));
    }

    /**
     * A conflict never overwrites the durable outcome. The authoritative stored state is read back
     * and reported; an unreadable or undetermined record stays uncertain.
     */
    private LateEvidenceOutcome preservedOutcome(String authorizationId,
            LateEvidenceAdmission admission) {
        Optional<AuthorizationRecord> stored;
        try {
            stored = repository.findById(authorizationId);
        } catch (RuntimeException unreadable) {
            return new LateEvidenceOutcome(Outcome.NOT_CONFIRMED, admission);
        }
        if (stored.isEmpty()) {
            return new LateEvidenceOutcome(Outcome.NOT_CONFIRMED, admission);
        }
        return new LateEvidenceOutcome(outcomeOf(stored.get().state()), admission);
    }

    private static Outcome outcomeOf(AuthorizationState state) {
        return switch (state) {
            case APPROVED -> Outcome.APPROVED;
            case DECLINED -> Outcome.ISSUER_DECLINED;
            case NETWORK_REJECTED -> Outcome.PROCESSOR_REJECTED;
            case AWAITING, NOT_CONFIRMED -> Outcome.NOT_CONFIRMED;
        };
    }

    /** Internal boundary evaluation; a confirmed result never regresses to unknown. */
    public Optional<AuthorizationRecord> evaluateDeadline(String authorizationId, Instant at) {
        if (resultGateway != null) {
            // The NOT_CONFIRMED boundary transition is not implemented atomically yet; keep the
            // durable intent untouched and write no legacy outcome/event row.
            return repository.findById(authorizationId);
        }
        Optional<AuthorizationRecord> maybe = repository.findById(authorizationId);
        if (maybe.isEmpty()) {
            return Optional.empty();
        }
        AuthorizationRecord record = maybe.get();
        if (record.state() == AuthorizationState.AWAITING && record.dispatchedAt() != null
                && !at.isBefore(record.dispatchedAt().plus(timeout()))) {
            repository.updateOutcome(record.id(), AuthorizationState.NOT_CONFIRMED.name(),
                    DecisionSource.NONE.name(), "NO_CONFIRMATION",
                    record.dispatchedAt().plus(timeout()));
            appendEvent(record, "TIMEOUT", null, DecisionSource.NONE,
                    record.dispatchCorrelationId(), "NO_CONFIRMED_EVIDENCE",
                    record.dispatchedAt().plus(timeout()));
            return repository.findById(authorizationId);
        }
        return maybe;
    }

    /** Internal operator inspection only (P08); not a caller response. */
    public Optional<AuthorizationRecord> inspect(String authorizationId) {
        return repository.findById(authorizationId);
    }

    /** Internal operator inspection only (P08); not a caller response. */
    public List<EvidenceEvent> history(String authorizationId) {
        return repository.eventsFor(authorizationId);
    }

    private AuthorizationResult authorizeLinked(
            AuthorizedContext context,
            PurchaseRequest request,
            Resolved resolved,
            String priorReference,
            Instant now) {
        Optional<AuthorizationRecord> maybe = repository.findById(priorReference);
        if (maybe.isEmpty()) {
            return reject(context, request, RejectionReason.INVALID_PRIOR_REFERENCE, now);
        }
        AuthorizationRecord original = maybe.get();
        if (!original.acquirerRef().equals(context.acquirerRef())
                || !original.merchantRef().equals(context.merchantRef())) {
            return reject(context, request, RejectionReason.FOREIGN_PRIOR_REFERENCE, now);
        }
        if (!original.businessKey().equals(resolved.businessKey())) {
            return reject(context, request, RejectionReason.INCOMPATIBLE_LINKED_RETRY, now);
        }
        if (!original.paymentFingerprint().equals(resolved.fingerprint())) {
            return reject(context, request, RejectionReason.PAYMENT_DATA_CONFLICT, now);
        }
        return replay(original);
    }

    private AuthorizationResult authorizeUnlinked(
            AuthorizedContext context, PurchaseRequest request, Resolved resolved, Instant now) {
        Optional<AuthorizationRecord> latest = repository.findLatestByBusinessKey(resolved.businessKey());
        int generation = 1;
        if (latest.isPresent()) {
            AuthorizationRecord prior = latest.get();
            if (now.isBefore(prior.duplicateWindowEnd())) {
                if (!prior.paymentFingerprint().equals(resolved.fingerprint())) {
                    return reject(context, request, RejectionReason.PAYMENT_DATA_CONFLICT, now);
                }
                return replay(prior);
            }
            generation = prior.generation() + 1;
        }

        String authorizationId = "AUTH-" + randomId();
        AuthorizationRecord claimed = new AuthorizationRecord(
                authorizationId,
                resolved.businessKey(),
                context.acquirerRef(),
                request.merchantRef(),
                request.channelPaymentReference().trim(),
                resolved.amountMinor(),
                CURRENCY,
                request.credentialRef(),
                request.terminalRef(),
                request.entryMode(),
                request.cvmIndicator(),
                request.cryptogramPresent(),
                resolved.risk(),
                resolved.route(),
                resolved.fingerprint(),
                generation,
                AuthorizationState.AWAITING,
                DecisionSource.NONE,
                null,
                null,
                now,
                null,
                null,
                now.plus(window()));
        if (claimGateway != null) {
            return authorizeAtomic(context, request, resolved, claimed, now);
        }
        try {
            repository.claim(claimed);
        } catch (DuplicateClaimException e) {
            return resolveClaimRace(context, request, resolved, generation, now, e);
        }

        String correlationId = "CORR-" + randomId();
        repository.markDispatched(authorizationId, correlationId, now);
        DownstreamReply reply = issuerSimulator.decide(new SimulatedDecisionRequest(
                authorizationId, correlationId, resolved.credentialDecision(),
                resolved.route(), now, timeout()));
        return evaluateDispatchReply(claimed.withDispatch(correlationId, now), reply, now);
    }

    /**
     * Claim path used when an atomic claim port is installed. Exactly one gateway call happens:
     * the business record, business head, critical claim event, EVENT head and dedupe row (plus an
     * optional slot reservation) commit together, or nothing does. No branch of this method calls
     * {@code repository.claim()}, retries the gateway or resends a request.
     */
    private AuthorizationResult authorizeAtomic(
            AuthorizedContext context,
            PurchaseRequest request,
            Resolved resolved,
            AuthorizationRecord claimed,
            Instant now) {
        String correlationId = "CORR-" + randomId();
        ClaimGateway.Decision decision = claimGateway.claim(claimed, correlationId, now);
        return switch (decision.status()) {
            case CLAIMED -> dispatchCommitted(claimed, correlationId, decision, resolved, now);
            case REPLAY -> replayCommitted(claimed);
            case CONTENT_CONFLICT -> reject(context, request, RejectionReason.PAYMENT_DATA_CONFLICT, now);
            case NOT_COMMITTED -> resolveRejectedClaim(context, request, resolved, now);
            case UNKNOWN -> AuthorizationResult.of(Outcome.NOT_CONFIRMED, claimed.id());
        };
    }

    /**
     * Dispatch only when the gateway returned persisted committed-version evidence. The value is
     * read from the stored claim row; it is never assumed from the internal CAS counter, so a
     * committed claim with absent evidence stays undispatched instead of fabricating version 1.
     *
     * <p>When a dispatch-intent gateway is installed, the durable {@code POSSIBLE_SEND} transition
     * is committed BEFORE any downstream call. Only a freshly committed intent permits the send;
     * replay, recovery, conflict, rejection and unknown outcomes stop here without calling the
     * downstream and without falling back to {@code markDispatched} or a legacy event append.
     */
    private AuthorizationResult dispatchCommitted(
            AuthorizationRecord claimed,
            String correlationId,
            ClaimGateway.Decision decision,
            Resolved resolved,
            Instant now) {
        CommittedVersionEvidence evidence = decision.evidence();
        if (evidence == null || !evidence.committedVersionRecorded()) {
            return AuthorizationResult.of(Outcome.NOT_CONFIRMED, claimed.id());
        }
        if (dispatchIntentGateway == null) {
            repository.markDispatched(claimed.id(), correlationId, now);
            return sendDownstream(claimed.withDispatch(correlationId, now), resolved, now);
        }
        DispatchIntentGateway.Decision intent =
                dispatchIntentGateway.dispatchIntent(claimed, correlationId, now);
        return switch (intent.status()) {
            case COMMITTED -> {
                if (!intent.permitsSend()) {
                    // No persisted intent evidence: the send is not authorized.
                    yield AuthorizationResult.of(Outcome.NOT_CONFIRMED, claimed.id());
                }
                yield sendDownstream(claimed.withDispatch(correlationId, now), resolved, now);
            }
            // Already committed by an earlier attempt: knowledge, never a resend permission.
            case REPLAY, RECOVERED -> replayCommitted(claimed);
            // Integrity conflict, definite rejection or unestablished state: never send.
            case CONTENT_CONFLICT, NOT_COMMITTED, UNKNOWN ->
                    AuthorizationResult.of(Outcome.NOT_CONFIRMED, claimed.id());
        };
    }

    /** Exactly one simulated downstream call for an already-durable dispatch intent. */
    private AuthorizationResult sendDownstream(
            AuthorizationRecord record, Resolved resolved, Instant now) {
        DownstreamReply reply = issuerSimulator.decide(new SimulatedDecisionRequest(
                record.id(), record.dispatchCorrelationId(), resolved.credentialDecision(),
                resolved.route(), now, timeout()));
        if (resultGateway != null) {
            return evaluateAtomicResult(record, reply, now);
        }
        return evaluateDispatchReply(record, reply, now);
    }

    /**
     * Atomic result branch: the durable intent already exists and the simulated downstream has
     * returned exactly once. Only an authenticated, correlated, on-time confirmed issuer decision
     * reaches {@link ResultGateway#recordResult}; the reported outcome is derived from the persisted
     * confirmed state and committed-version evidence, never from the caller's reply. An explicit,
     * correlated, on-time {@code NETWORK_REJECTED} reply takes the separate
     * {@link NetworkResultGateway} branch instead of the issuer-result port.
     *
     * <p>Every other reply shape (no response, malformed, uncorrelated, late) has no atomic composer
     * in this slice. Those paths fail closed to {@code NOT_CONFIRMED} and write nothing: they
     * deliberately do not fall back to {@code updateOutcome} or a legacy event append.
     * {@code NOT_COMMITTED}/{@code UNKNOWN} likewise fabricate no financial result, and
     * {@code CONTENT_CONFLICT} exposes the authoritative stored outcome instead of overwriting it.
     */
    private AuthorizationResult evaluateAtomicResult(
            AuthorizationRecord record, DownstreamReply reply, Instant now) {
        Instant deadline = record.dispatchedAt() == null
                ? now.plus(timeout())
                : record.dispatchedAt().plus(timeout());

        boolean correlated = reply != null
                && reply.decision() != null
                && reply.availableAt() != null
                && reply.correlationId() != null
                && reply.correlationId().equals(record.dispatchCorrelationId());
        if (!correlated) {
            return AuthorizationResult.of(Outcome.NOT_CONFIRMED, record.id());
        }
        if (reply.availableAt().isAfter(deadline)) {
            // Late-evidence variants and the boundary disposition are a later slice.
            return AuthorizationResult.of(Outcome.NOT_CONFIRMED, record.id());
        }
        if (reply.decision() == IssuerDecision.NETWORK_REJECTED) {
            return evaluateAtomicNetworkResult(record, reply);
        }
        if (reply.decision() != IssuerDecision.APPROVED
                && reply.decision() != IssuerDecision.DECLINED) {
            return AuthorizationResult.of(Outcome.NOT_CONFIRMED, record.id());
        }

        ResultGateway.Decision decision = resultGateway.recordResult(record, reply);
        return switch (decision.status()) {
            case COMMITTED, REPLAY, RECOVERED -> recordedResult(decision, record);
            case CONTENT_CONFLICT -> retainedResult(record);
            case NOT_COMMITTED, UNKNOWN ->
                    AuthorizationResult.of(Outcome.NOT_CONFIRMED, record.id());
        };
    }

    /**
     * Service branch for one explicit pre-issuer network rejection. Only a valid, correlated reply
     * available no earlier than the durable dispatch reaches the port, and the port is called at
     * most once. {@code PROCESSOR_REJECTED} is reported only from persisted
     * {@code NETWORK_REJECTED} state with committed-version evidence; unknown, failed, absent or
     * conflicting evidence stays {@code NOT_CONFIRMED}. A competing issuer-confirmed outcome is read
     * back and reported instead of being overwritten or mislabelled, and no legacy write, retry,
     * resend, reversal or issuer task occurs.
     */
    private AuthorizationResult evaluateAtomicNetworkResult(
            AuthorizationRecord record, DownstreamReply reply) {
        if (networkResultGateway == null) {
            // No atomic network composer installed: fail closed without a legacy write.
            return AuthorizationResult.of(Outcome.NOT_CONFIRMED, record.id());
        }
        if (record.dispatchedAt() == null || reply.availableAt().isBefore(record.dispatchedAt())) {
            // Evidence that predates the durable dispatch is uncorrelated; refuse before any write.
            return AuthorizationResult.of(Outcome.NOT_CONFIRMED, record.id());
        }

        NetworkResultGateway.Decision decision =
                networkResultGateway.recordNetworkRejection(record, reply);
        return switch (decision.status()) {
            case COMMITTED, REPLAY, RECOVERED -> recordedNetworkResult(decision, record);
            case CONTENT_CONFLICT -> retainedResult(record);
            case NOT_COMMITTED, UNKNOWN ->
                    AuthorizationResult.of(Outcome.NOT_CONFIRMED, record.id());
        };
    }

    /** Report PROCESSOR_REJECTED only from persisted network evidence; never an issuer decline. */
    private static AuthorizationResult recordedNetworkResult(
            NetworkResultGateway.Decision decision, AuthorizationRecord record) {
        if (!decision.recorded()) {
            return AuthorizationResult.of(Outcome.NOT_CONFIRMED, record.id());
        }
        return AuthorizationResult.of(Outcome.PROCESSOR_REJECTED, record.id());
    }

    /** Report the persisted confirmed state; an unrecorded result stays uncertain, never invented. */
    private static AuthorizationResult recordedResult(
            ResultGateway.Decision decision, AuthorizationRecord record) {
        if (!decision.recorded()) {
            return AuthorizationResult.of(Outcome.NOT_CONFIRMED, record.id());
        }
        return switch (decision.confirmedState()) {
            case APPROVED -> AuthorizationResult.of(Outcome.APPROVED, record.id());
            case DECLINED -> AuthorizationResult.of(Outcome.ISSUER_DECLINED, record.id());
            default -> AuthorizationResult.of(Outcome.NOT_CONFIRMED, record.id());
        };
    }

    /**
     * The gateway retained a competing terminal outcome. The authoritative stored state is read
     * back and reported; the contradictory reply never overwrites it and no legacy row is written.
     * An unreadable or undetermined stored record stays {@code NOT_CONFIRMED}.
     */
    private AuthorizationResult retainedResult(AuthorizationRecord record) {
        Optional<AuthorizationRecord> stored = repository.findById(record.id());
        if (stored.isEmpty()) {
            return AuthorizationResult.of(Outcome.NOT_CONFIRMED, record.id());
        }
        AuthorizationRecord authoritative = stored.get();
        if (authoritative.state() != AuthorizationState.APPROVED
                && authoritative.state() != AuthorizationState.DECLINED) {
            return AuthorizationResult.of(Outcome.NOT_CONFIRMED, record.id());
        }
        return replay(authoritative);
    }

    /** The claim event already existed; report the stored record and never redispatch. */
    private AuthorizationResult replayCommitted(AuthorizationRecord claimed) {
        Optional<AuthorizationRecord> stored = repository.findById(claimed.id());
        if (stored.isEmpty()) {
            return AuthorizationResult.of(Outcome.NOT_CONFIRMED, claimed.id());
        }
        AuthorizationRecord record = stored.get();
        if (!record.paymentFingerprint().equals(claimed.paymentFingerprint())) {
            return AuthorizationResult.of(Outcome.NOT_CONFIRMED, claimed.id());
        }
        return replay(record);
    }

    /**
     * The atomic transaction was rejected, so nothing was committed. A single read establishes
     * whether a competing claim won; nothing is claimed again, dispatched or resent.
     */
    private AuthorizationResult resolveRejectedClaim(
            AuthorizedContext context, PurchaseRequest request, Resolved resolved, Instant now) {
        Optional<AuthorizationRecord> latest = repository.findLatestByBusinessKey(resolved.businessKey());
        if (latest.isEmpty()) {
            return AuthorizationResult.of(Outcome.NOT_CONFIRMED, null);
        }
        AuthorizationRecord existing = latest.get();
        if (!existing.paymentFingerprint().equals(resolved.fingerprint())) {
            return reject(context, request, RejectionReason.PAYMENT_DATA_CONFLICT, now);
        }
        return replay(existing);
    }

    private AuthorizationResult resolveClaimRace(
            AuthorizedContext context,
            PurchaseRequest request,
            Resolved resolved,
            int generation,
            Instant now,
            DuplicateClaimException claimFailure) {
        for (int attempt = 0; attempt < 80; attempt++) {
            Optional<AuthorizationRecord> winner = repository.findLatestByBusinessKey(resolved.businessKey());
            if (winner.isPresent()) {
                AuthorizationRecord record = winner.get();
                if (record.generation() != generation
                        || !record.paymentFingerprint().equals(resolved.fingerprint())) {
                    return reject(context, request, RejectionReason.PAYMENT_DATA_CONFLICT, now);
                }
                return replay(record);
            }
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("interrupted while resolving claim race", e);
            }
        }
        throw new IllegalStateException("claim contention could not be resolved", claimFailure);
    }

    private AuthorizationResult evaluateDispatchReply(
            AuthorizationRecord record, DownstreamReply reply, Instant now) {
        Instant deadline = record.dispatchedAt() == null
                ? now.plus(timeout())
                : record.dispatchedAt().plus(timeout());

        if (reply == null || reply.decision() == null || reply.decision() == IssuerDecision.NO_RESPONSE) {
            return notConfirmed(record, "NO_CONFIRMATION", "TIMEOUT", deadline);
        }
        if (reply.correlationId() == null
                || !reply.correlationId().equals(record.dispatchCorrelationId())
                || reply.decision() == IssuerDecision.MALFORMED
                || reply.availableAt() == null) {
            return notConfirmed(record, "MALFORMED_RESPONSE", "MALFORMED_RESPONSE", now);
        }

        if (reply.decision() == IssuerDecision.NETWORK_REJECTED) {
            repository.updateOutcome(record.id(), AuthorizationState.NETWORK_REJECTED.name(),
                    DecisionSource.NETWORK.name(), "NETWORK_REJECTION", reply.availableAt());
            appendEvent(record, "NETWORK_REJECTION", null, DecisionSource.NETWORK,
                    reply.correlationId(), "NO_ISSUER_DISPATCH", now);
            return AuthorizationResult.of(Outcome.PROCESSOR_REJECTED, record.id());
        }

        AuthorizationState confirmedState = reply.decision() == IssuerDecision.APPROVED
                ? AuthorizationState.APPROVED
                : AuthorizationState.DECLINED;
        if (reply.availableAt().isAfter(deadline)) {
            // Boundary response is not-confirmed; late evidence is retained and becomes the
            // latest result for subsequent valid retries (R20/A14).
            repository.updateOutcome(record.id(), confirmedState.name(), DecisionSource.ISSUER.name(),
                    null, reply.availableAt());
            appendEvent(record, "LATE_CONFIRMATION", reply.decision().name(), DecisionSource.ISSUER,
                    reply.correlationId(), "AFTER_TIMEOUT", now);
            return AuthorizationResult.of(Outcome.NOT_CONFIRMED, record.id());
        }

        repository.updateOutcome(record.id(), confirmedState.name(), DecisionSource.ISSUER.name(),
                null, reply.availableAt());
        appendEvent(record, "CONFIRMATION", reply.decision().name(), DecisionSource.ISSUER,
                reply.correlationId(), "ON_TIME", now);
        return AuthorizationResult.of(
                confirmedState == AuthorizationState.APPROVED
                        ? Outcome.APPROVED
                        : Outcome.ISSUER_DECLINED,
                record.id());
    }

    private AuthorizationResult notConfirmed(
            AuthorizationRecord record, String reason, String eventType, Instant determinedAt) {
        repository.updateOutcome(record.id(), AuthorizationState.NOT_CONFIRMED.name(),
                DecisionSource.NONE.name(), reason, determinedAt);
        appendEvent(record, eventType, null, DecisionSource.NONE, record.dispatchCorrelationId(),
                reason, determinedAt);
        return AuthorizationResult.of(Outcome.NOT_CONFIRMED, record.id());
    }

    private AuthorizationResult replay(AuthorizationRecord record) {
        Outcome outcome = switch (record.state()) {
            case NETWORK_REJECTED -> Outcome.PROCESSOR_REJECTED;
            case APPROVED -> Outcome.APPROVED;
            case DECLINED -> Outcome.ISSUER_DECLINED;
            case AWAITING, NOT_CONFIRMED -> Outcome.NOT_CONFIRMED;
        };
        return AuthorizationResult.of(outcome, record.id());
    }

    private AuthorizationResult reject(
            AuthorizedContext context,
            PurchaseRequest request,
            RejectionReason reason,
            Instant now) {
        repository.appendRejection(new RejectionAudit(
                "REJ-" + String.format(Locale.ROOT, "%013d-%09d-%s", now.toEpochMilli(),
                        REJECTION_SEQUENCE.incrementAndGet(), randomId().substring(0, 8)),
                context == null ? null : context.acquirerRef(),
                request == null ? null : request.merchantRef(),
                context == null ? null : context.callerId(),
                request == null ? null : request.channelPaymentReference(),
                reason,
                now.toEpochMilli()));
        return AuthorizationResult.of(Outcome.PROCESSOR_REJECTED, null);
    }

    private Validation validate(AuthorizedContext context, PurchaseRequest request, Instant now) {
        if (context == null || isBlank(context.acquirerRef()) || isBlank(context.merchantRef())
                || isBlank(context.callerId()) || request == null) {
            return Validation.fail(RejectionReason.CALLER_NOT_AUTHORIZED);
        }
        Optional<SimulatorProperties.AcquirerFixture> acquirer =
                properties.profile().acquirer(context.acquirerRef());
        if (acquirer.isEmpty()) {
            return Validation.fail(RejectionReason.CALLER_NOT_AUTHORIZED);
        }
        if (isBlank(request.merchantRef()) || !request.merchantRef().equals(context.merchantRef())) {
            return Validation.fail(RejectionReason.CALLER_NOT_AUTHORIZED);
        }
        if (isBlank(request.channelPaymentReference())) {
            return Validation.fail(RejectionReason.MISSING_OR_INVALID_INPUT);
        }
        if (request.amount() == null || !validAmount(request.amount())) {
            return Validation.fail(RejectionReason.MISSING_OR_INVALID_INPUT);
        }
        if (!CURRENCY.equals(request.currency())) {
            return Validation.fail(RejectionReason.MISSING_OR_INVALID_INPUT);
        }
        if (request.entryMode() == null || request.cvmIndicator() == null
                || request.cryptogramPresent() == null) {
            return Validation.fail(RejectionReason.MISSING_OR_INVALID_INPUT);
        }
        if (isBlank(request.terminalRef())) {
            return Validation.fail(RejectionReason.MISSING_OR_INVALID_INPUT);
        }
        if (isBlank(request.credentialRef())) {
            return Validation.fail(RejectionReason.UNKNOWN_CREDENTIAL);
        }
        RiskIndicator risk = request.riskIndicator() == null ? RiskIndicator.PASS : request.riskIndicator();

        Optional<SimulatorProperties.MerchantFixture> merchant =
                properties.profile().merchant(request.merchantRef());
        if (merchant.isEmpty()) {
            return Validation.fail(RejectionReason.UNKNOWN_MERCHANT);
        }
        if (!merchant.get().acquirer().equals(context.acquirerRef())) {
            return Validation.fail(RejectionReason.MERCHANT_ACQUIRER_MISMATCH);
        }
        Optional<SimulatorProperties.TerminalFixture> terminal =
                properties.profile().terminal(request.terminalRef());
        if (terminal.isEmpty()) {
            return Validation.fail(RejectionReason.UNKNOWN_TERMINAL);
        }
        if (!terminal.get().merchant().equals(request.merchantRef())) {
            return Validation.fail(RejectionReason.TERMINAL_MERCHANT_MISMATCH);
        }
        Optional<SimulatorProperties.CredentialRange> credential =
                properties.profile().credential(request.credentialRef());
        if (credential.isEmpty()) {
            return Validation.fail(RejectionReason.UNKNOWN_CREDENTIAL);
        }
        Route route = credential.get().route();
        if (!acquirer.get().routes().contains(route) || !context.permittedRoutes().contains(route)) {
            return Validation.fail(RejectionReason.ROUTE_NOT_PERMITTED);
        }
        if (risk == RiskIndicator.REJECT) {
            return Validation.fail(RejectionReason.RISK_REJECTED);
        }

        long amountMinor = request.amount().setScale(2).movePointRight(2).longValueExact();
        String businessKey = context.acquirerRef() + "|" + request.merchantRef() + "|"
                + request.channelPaymentReference().trim();
        String fingerprint = fingerprint(amountMinor, request);
        return Validation.ok(new Resolved(
                credential.get().decision(), route, risk, amountMinor, businessKey, fingerprint));
    }

    private static boolean validAmount(BigDecimal amount) {
        if (amount.stripTrailingZeros().scale() > 2) {
            return false;
        }
        if (amount.compareTo(BigDecimal.ONE) < 0 || amount.compareTo(new BigDecimal("9999.99")) > 0) return false;
        BigDecimal normalized = amount.setScale(2);
        long minor = normalized.movePointRight(2).longValueExact();
        return minor >= MIN_AMOUNT_MINOR && minor <= MAX_AMOUNT_MINOR;
    }

    private static String fingerprint(long amountMinor, PurchaseRequest request) {
        String canonical = String.join("|",
                Long.toString(amountMinor),
                CURRENCY,
                request.credentialRef(),
                request.terminalRef(),
                request.entryMode().name(),
                request.cvmIndicator().name(),
                Boolean.toString(request.cryptogramPresent()),
                (request.riskIndicator() == null ? RiskIndicator.PASS : request.riskIndicator()).name());
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private void appendEvent(
            AuthorizationRecord record,
            String eventType,
            String decision,
            DecisionSource source,
            String correlationId,
            String detail,
            Instant recordedAt) {
        repository.appendEvent(new EvidenceEvent(
                "EVT-" + randomId(),
                record.id(),
                eventType,
                decision,
                source,
                correlationId,
                detail,
                recordedAt.toEpochMilli()));
    }

    private static boolean isConfirmed(AuthorizationRecord record) {
        return record.state() == AuthorizationState.NETWORK_REJECTED
                || record.state() == AuthorizationState.APPROVED
                || record.state() == AuthorizationState.DECLINED;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String randomId() {
        return SafeIdentifier.normalize(identifiers.nextCandidate());
    }

    private Duration window() {
        return properties.authorization().duplicateWindow();
    }

    private Duration timeout() {
        return properties.authorization().responseTimeout();
    }

    private record Resolved(
            com.acquiring.simulator.domain.model.CredentialDecision credentialDecision,
            Route route,
            RiskIndicator risk,
            long amountMinor,
            String businessKey,
            String fingerprint) {
    }

    private record Validation(Resolved resolved, RejectionReason reason) {
        static Validation ok(Resolved resolved) {
            return new Validation(resolved, null);
        }

        static Validation fail(RejectionReason reason) {
            return new Validation(null, reason);
        }
    }
}
