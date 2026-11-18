package com.acquiring.simulator.domain;

import com.acquiring.simulator.config.SimulatorProperties;
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
import com.acquiring.simulator.domain.model.RejectionAudit;
import com.acquiring.simulator.domain.model.RejectionReason;
import com.acquiring.simulator.domain.model.RiskIndicator;
import com.acquiring.simulator.domain.model.Route;
import com.acquiring.simulator.domain.model.SimulatedDecisionRequest;
import com.acquiring.simulator.domain.port.AuthorizationRepository;
import com.acquiring.simulator.domain.port.DuplicateClaimException;
import com.acquiring.simulator.domain.port.IssuerSimulator;
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
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
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

    public AuthorizationService(
            AuthorizationRepository repository,
            IssuerSimulator issuerSimulator,
            SimulatorProperties properties,
            Clock clock) {
        this.repository = repository;
        this.issuerSimulator = issuerSimulator;
        this.properties = properties;
        this.clock = clock;
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

    /** Internal boundary evaluation; a confirmed result never regresses to unknown. */
    public Optional<AuthorizationRecord> evaluateDeadline(String authorizationId, Instant at) {
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

    private static String randomId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 24);
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
