package com.acquiring.simulator.channel;

import com.acquiring.simulator.channel.C3Response.HttpStatus;
import com.acquiring.simulator.channel.C3ResponseEnvelope.Disposition;
import com.acquiring.simulator.channel.C3ResponseEnvelope.ErrorCode;
import com.acquiring.simulator.channel.C3ResponseEnvelope.Observation;
import com.acquiring.simulator.channel.C3ResponseEnvelope.Outcome;
import com.acquiring.simulator.channel.C3ResponseEnvelope.ServerFreshness;
import com.acquiring.simulator.domain.model.AuthorizationState;
import java.time.Instant;
import java.util.Objects;

/**
 * Pure C3 response precedence builder.
 *
 * <p>Input is a closed set of <em>trusted internal facts</em>: durable/restart knowledge,
 * this-invocation observations and the transport authentication decision. There is no
 * field for a caller-declared outcome, no fabricated identifier and no version derived
 * from an identifier. The legacy {@code AuthorizationResult} (outcome + reference) is
 * deliberately not accepted: it carries neither the original identifiers nor the
 * committed version, so reusing it here would silently invent C3 evidence.</p>
 *
 * <p>Precedence is applied strictly top to bottom, exactly as the channel contract's C3
 * table orders it:</p>
 * <ol>
 *   <li>authentication/context denial (401/403);</li>
 *   <li>pre-admission schema/media/size/conflict/reference failure (400/409/413/415);</li>
 *   <li>authorized history target unknown/inaccessible (404);</li>
 *   <li>readable committed confirmation, including after a later observation failure;</li>
 *   <li>positively unsent claim or positively unsent new admission (503, or 200 history);</li>
 *   <li>possible send known in this invocation while the journal is unavailable (503);</li>
 *   <li>readable possible send: active (202) or deadline reached (200);</li>
 *   <li>unreadable after restart/ambiguous commit (503).</li>
 * </ol>
 *
 * <p>Asynchronous notification/query defects only degrade {@code observation} and
 * {@code serverFreshness}; they can never replace a readable committed confirmation,
 * change its outcome or force RETRY semantics. This class has no Retry-After concept and
 * never triggers or authorizes a send, retry, refresh or rollback. It performs no I/O
 * and reads no clock: {@code asOf} is a supplied fact.</p>
 */
public final class C3ResponseBuilder {

    private C3ResponseBuilder() {
    }

    /**
     * Derive one HTTP/JSON response from trusted facts. The first applicable row of the
     * C3 precedence table wins; later facts cannot upgrade or downgrade it.
     */
    public static C3Response build(Facts facts) {
        Objects.requireNonNull(facts, "facts");
        if (facts.denial() != Denial.NONE) {
            return denial(facts);
        }
        if (facts.preAdmissionFailure() != null) {
            return preAdmission(facts);
        }
        if (facts.historyTargetUnknown()) {
            return historyUnknown(facts);
        }
        return evidence(facts);
    }

    private static C3Response denial(Facts facts) {
        ErrorCode error = facts.denial() == Denial.UNAUTHENTICATED
                ? ErrorCode.UNAUTHENTICATED
                : ErrorCode.CONTEXT_DENIED;
        return new C3Response(facts.denial().status(),
                envelope(null, null, facts.attempt().attemptId(),
                        facts.attempt().correlationId(), null,
                        null, Disposition.REJECTED, null, facts.asOf(),
                        Observation.UNKNOWN, ServerFreshness.UNKNOWN, error));
    }

    private static C3Response preAdmission(Facts facts) {
        PreAdmissionFailure failure = facts.preAdmissionFailure();
        return new C3Response(failure.status(),
                envelope(null, null, facts.attempt().attemptId(),
                        facts.attempt().correlationId(), null,
                        null, Disposition.REJECTED, null, facts.asOf(),
                        Observation.UNKNOWN, ServerFreshness.UNKNOWN, failure.error()));
    }

    private static C3Response historyUnknown(Facts facts) {
        return new C3Response(HttpStatus.NOT_FOUND,
                envelope(null, null, facts.attempt().attemptId(),
                        facts.attempt().correlationId(), null,
                        null, Disposition.UNAVAILABLE, null, facts.asOf(),
                        Observation.UNKNOWN, ServerFreshness.UNKNOWN, ErrorCode.NOT_FOUND));
    }

    private static C3Response evidence(Facts facts) {
        return switch (facts.evidence()) {
            case Evidence.ConfirmedAuthorization confirmed -> confirmed(facts, confirmed);
            case Evidence.UnsentClaim unsentClaim -> unsentClaim(facts, unsentClaim);
            case Evidence.NoClaimUnsent unsent -> noClaimUnsent(facts, unsent);
            case Evidence.PossibleSendStoreUnavailable unavailable ->
                    possibleSendStoreUnavailable(facts, unavailable);
            case Evidence.ReadablePossibleSend readable -> readablePossibleSend(facts, readable);
            case Evidence.UnreadableStore unreadable -> unreadableStore(facts, unreadable);
        };
    }

    private static C3Response confirmed(Facts facts, Evidence.ConfirmedAuthorization confirmed) {
        Quality quality = confirmationQuality(confirmed.health());
        Identifiers ids = confirmed.identifiers();
        Disposition disposition = facts.origin() == Origin.SUBMISSION
                ? Disposition.COMPLETED
                : Disposition.REPLAY;
        return new C3Response(HttpStatus.OK,
                envelope(ids.authorizationId(), ids.transactionId(), ids.attemptId(),
                        ids.correlationId(), ids.originalCorrelationId(),
                        wireOutcome(confirmed.state()), disposition,
                        confirmed.committedVersion(), facts.asOf(),
                        quality.observation(), quality.freshness(), quality.error()));
    }

    private static C3Response unsentClaim(Facts facts, Evidence.UnsentClaim unsent) {
        Identifiers ids = unsent.identifiers();
        HttpStatus status = facts.origin() == Origin.HISTORY_READ
                ? HttpStatus.OK
                : HttpStatus.SERVICE_UNAVAILABLE;
        return new C3Response(status,
                envelope(ids.authorizationId(), ids.transactionId(), ids.attemptId(),
                        ids.correlationId(), ids.originalCorrelationId(),
                        null, Disposition.NOT_DISPATCHED, unsent.committedVersion(), facts.asOf(),
                        Observation.PARTIAL, ServerFreshness.CURRENT,
                        ErrorCode.RESERVED_UNSENT));
    }

    private static C3Response noClaimUnsent(Facts facts, Evidence.NoClaimUnsent unsent) {
        AttemptIdentifiers attempt = facts.attempt();
        return new C3Response(HttpStatus.SERVICE_UNAVAILABLE,
                envelope(null, null, attempt.attemptId(), attempt.correlationId(), null,
                        null, Disposition.NOT_DISPATCHED, null, facts.asOf(),
                        Observation.UNKNOWN, ServerFreshness.UNKNOWN, unsent.cause().error()));
    }

    private static C3Response possibleSendStoreUnavailable(
            Facts facts, Evidence.PossibleSendStoreUnavailable unavailable) {
        Identifiers ids = unavailable.identifiers();
        return new C3Response(HttpStatus.SERVICE_UNAVAILABLE,
                envelope(ids.authorizationId(), ids.transactionId(), ids.attemptId(),
                        ids.correlationId(), ids.originalCorrelationId(),
                        Outcome.NOT_CONFIRMED, Disposition.PENDING,
                        unavailable.committedVersion(), facts.asOf(),
                        Observation.UNKNOWN, ServerFreshness.UNKNOWN,
                        ErrorCode.BUSINESS_JOURNAL_UNAVAILABLE));
    }

    private static C3Response readablePossibleSend(
            Facts facts, Evidence.ReadablePossibleSend readable) {
        Identifiers ids = readable.identifiers();
        Quality quality = possibleSendQuality(readable.health());
        boolean deadline = readable.progress() == Evidence.Progress.DEADLINE;
        Disposition disposition;
        HttpStatus status;
        if (deadline) {
            status = HttpStatus.OK;
            disposition = facts.origin() == Origin.SUBMISSION
                    ? Disposition.COMPLETED
                    : Disposition.REPLAY;
        } else {
            status = HttpStatus.ACCEPTED;
            disposition = Disposition.PENDING;
        }
        return new C3Response(status,
                envelope(ids.authorizationId(), ids.transactionId(), ids.attemptId(),
                        ids.correlationId(), ids.originalCorrelationId(),
                        Outcome.NOT_CONFIRMED, disposition, readable.committedVersion(),
                        facts.asOf(), quality.observation(), quality.freshness(), quality.error()));
    }

    private static C3Response unreadableStore(Facts facts, Evidence.UnreadableStore unreadable) {
        InvocationIdentifiers ids = unreadable.identifiers();
        return new C3Response(HttpStatus.SERVICE_UNAVAILABLE,
                envelope(ids.authorizationId(), ids.transactionId(), ids.attemptId(),
                        ids.correlationId(), null,
                        null, Disposition.UNAVAILABLE, null, facts.asOf(),
                        Observation.UNKNOWN, ServerFreshness.UNKNOWN,
                        ErrorCode.BUSINESS_JOURNAL_UNAVAILABLE));
    }

    private static Outcome wireOutcome(AuthorizationState state) {
        return switch (state) {
            case APPROVED -> Outcome.APPROVED;
            case DECLINED -> Outcome.DECLINED;
            case NETWORK_REJECTED -> Outcome.PROCESSOR_REJECTED;
            case AWAITING, NOT_CONFIRMED -> throw new IllegalArgumentException(
                    "a committed confirmation cannot be " + state);
        };
    }

    /**
     * Committed confirmation quality. A readable payment decision is never downgraded by
     * an asynchronous defect; only observation/freshness/error describe that defect. The
     * first applicable source problem is reported.
     */
    private static Quality confirmationQuality(AsyncHealth health) {
        if (!health.eventHistoryReadable()) {
            return new Quality(Observation.PARTIAL, ServerFreshness.UNKNOWN,
                    ErrorCode.EVENT_HISTORY_UNAVAILABLE);
        }
        if (health.liveDeliveryGap()) {
            return new Quality(Observation.PARTIAL, ServerFreshness.STALE,
                    ErrorCode.LIVE_DELIVERY_LAG);
        }
        if (!health.liveNotificationsAvailable()) {
            return new Quality(Observation.PARTIAL, ServerFreshness.UNKNOWN,
                    ErrorCode.LIVE_NOTIFICATIONS_UNAVAILABLE);
        }
        if (health.storeLostAfterRead()) {
            return new Quality(Observation.PARTIAL, ServerFreshness.UNKNOWN, null);
        }
        return new Quality(Observation.COMPLETE, ServerFreshness.CURRENT, null);
    }

    /** In-flight knowledge is never COMPLETE, and its exchange ending is not finality. */
    private static Quality possibleSendQuality(AsyncHealth health) {
        if (!health.eventHistoryReadable()) {
            return new Quality(Observation.PARTIAL, ServerFreshness.UNKNOWN,
                    ErrorCode.EVENT_HISTORY_UNAVAILABLE);
        }
        if (health.liveDeliveryGap()) {
            return new Quality(Observation.PARTIAL, ServerFreshness.STALE,
                    ErrorCode.LIVE_DELIVERY_LAG);
        }
        if (!health.liveNotificationsAvailable()) {
            return new Quality(Observation.PARTIAL, ServerFreshness.UNKNOWN,
                    ErrorCode.LIVE_NOTIFICATIONS_UNAVAILABLE);
        }
        if (health.storeLostAfterRead()) {
            return new Quality(Observation.PARTIAL, ServerFreshness.UNKNOWN, null);
        }
        return new Quality(Observation.PARTIAL, ServerFreshness.CURRENT, null);
    }

    private static C3ResponseEnvelope envelope(String authorizationId, String transactionId,
            String attemptId, String correlationId, String originalCorrelationId,
            Outcome outcome, Disposition disposition, Long businessVersion, Instant asOf,
            Observation observation, ServerFreshness freshness, ErrorCode error) {
        return new C3ResponseEnvelope(C3ResponseEnvelope.CONTRACT_VERSION,
                authorizationId, transactionId, attemptId, correlationId,
                originalCorrelationId, outcome, disposition, businessVersion, asOf,
                observation, freshness, error);
    }

    private record Quality(Observation observation, ServerFreshness freshness, ErrorCode error) {
    }

    /** Transport authentication/context decision; never inferred from a body field. */
    public enum Denial {
        NONE,
        UNAUTHENTICATED,
        CONTEXT_DENIED;

        private HttpStatus status() {
            return this == UNAUTHENTICATED ? HttpStatus.UNAUTHORIZED : HttpStatus.FORBIDDEN;
        }
    }

    /** Pre-admission failures enumerated by the C3 row; no business result is implied. */
    public enum PreAdmissionFailure {
        INVALID_INPUT(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_INPUT),
        UNSUPPORTED_MEDIA(HttpStatus.UNSUPPORTED_MEDIA_TYPE, ErrorCode.UNSUPPORTED_MEDIA),
        BODY_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, ErrorCode.BODY_TOO_LARGE),
        PAYLOAD_CONFLICT(HttpStatus.CONFLICT, ErrorCode.PAYLOAD_CONFLICT),
        INVALID_REFERENCE(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_REFERENCE);

        private final HttpStatus status;
        private final ErrorCode error;

        PreAdmissionFailure(HttpStatus status, ErrorCode error) {
            this.status = status;
            this.error = error;
        }

        public HttpStatus status() {
            return status;
        }

        public ErrorCode error() {
            return error;
        }
    }

    /** Why the current exchange happened; it affects replay/deadline display only. */
    public enum Origin {
        /** First submission of this attempt. */
        SUBMISSION,
        /** A duplicate/linked retry of an already-known operation. */
        DUPLICATE,
        /** A read-only history/status view. */
        HISTORY_READ
    }

    /**
     * Identifiers independently known for the current request before any durable read.
     * Both are nullable: an earlier rejection may not have allocated them.
     */
    public record AttemptIdentifiers(String attemptId, String correlationId) {
        public static AttemptIdentifiers unknown() {
            return new AttemptIdentifiers(null, null);
        }
    }

    /** Complete identifiers of a durably readable operation; all five are required. */
    public record Identifiers(String authorizationId, String transactionId, String attemptId,
            String correlationId, String originalCorrelationId) {
        public Identifiers {
            Objects.requireNonNull(authorizationId, "authorizationId");
            Objects.requireNonNull(transactionId, "transactionId");
            Objects.requireNonNull(attemptId, "attemptId");
            Objects.requireNonNull(correlationId, "correlationId");
            Objects.requireNonNull(originalCorrelationId, "originalCorrelationId");
        }
    }

    /**
     * Identifiers known only inside the current invocation after a durable read failed.
     * Any component may be absent; absent components stay null in the response and an
     * original correlation/version is never synthesized.
     */
    public record InvocationIdentifiers(String authorizationId, String transactionId,
            String attemptId, String correlationId) {
        public static InvocationIdentifiers none(String correlationId) {
            return new InvocationIdentifiers(null, null, null, correlationId);
        }
    }

    /**
     * Readability of the surrounding observation surfaces. Defaults describe a healthy
     * journal, readable event history, deliverable notifications and no delivery gap.
     */
    public record AsyncHealth(boolean eventHistoryReadable,
            boolean liveNotificationsAvailable, boolean liveDeliveryGap,
            boolean storeLostAfterRead) {
        public AsyncHealth {
            if (liveDeliveryGap && !liveNotificationsAvailable) {
                throw new IllegalArgumentException(
                        "a live delivery gap is not meaningful while live delivery is unavailable");
            }
        }

        public static AsyncHealth healthy() {
            return new AsyncHealth(true, true, false, false);
        }

        public AsyncHealth withoutEventHistory() {
            return new AsyncHealth(false, liveNotificationsAvailable, liveDeliveryGap,
                    storeLostAfterRead);
        }

        public AsyncHealth withoutLiveNotifications() {
            return new AsyncHealth(eventHistoryReadable, false, false, storeLostAfterRead);
        }

        public AsyncHealth withLiveDeliveryGap() {
            return new AsyncHealth(eventHistoryReadable, liveNotificationsAvailable, true, false);
        }

        public AsyncHealth lostStoreAfterRead() {
            return new AsyncHealth(eventHistoryReadable, liveNotificationsAvailable,
                    liveDeliveryGap, true);
        }
    }

    /** The one evidence category available at this point of the precedence table. */
    public sealed interface Evidence {
        record ConfirmedAuthorization(AuthorizationState state, long committedVersion,
                Identifiers identifiers, AsyncHealth health) implements Evidence {
            public ConfirmedAuthorization {
                Objects.requireNonNull(state, "state");
                Objects.requireNonNull(identifiers, "identifiers");
                Objects.requireNonNull(health, "health");
                if (committedVersion < 1) {
                    throw new IllegalArgumentException("committed version must be positive");
                }
                if (state != AuthorizationState.APPROVED
                        && state != AuthorizationState.DECLINED
                        && state != AuthorizationState.NETWORK_REJECTED) {
                    throw new IllegalArgumentException(
                            "a committed confirmation cannot be " + state);
                }
            }
        }

        record UnsentClaim(long committedVersion, Identifiers identifiers) implements Evidence {
            public UnsentClaim {
                Objects.requireNonNull(identifiers, "identifiers");
                if (committedVersion < 1) {
                    throw new IllegalArgumentException("committed version must be positive");
                }
            }
        }

        /** A new unclaimed instruction provably not sent; no identifiers were invented. */
        record NoClaimUnsent(UnsentCause cause) implements Evidence {
            public NoClaimUnsent {
                Objects.requireNonNull(cause, "cause");
            }
        }

        /** Current invocation knows a send may have happened, but the journal is unavailable. */
        record PossibleSendStoreUnavailable(long committedVersion, Identifiers identifiers)
                implements Evidence {
            public PossibleSendStoreUnavailable {
                Objects.requireNonNull(identifiers, "identifiers");
                if (committedVersion < 1) {
                    throw new IllegalArgumentException("committed version must be positive");
                }
            }
        }

        record ReadablePossibleSend(long committedVersion, Identifiers identifiers,
                Progress progress, AsyncHealth health) implements Evidence {
            public ReadablePossibleSend {
                Objects.requireNonNull(identifiers, "identifiers");
                Objects.requireNonNull(progress, "progress");
                Objects.requireNonNull(health, "health");
                if (committedVersion < 1) {
                    throw new IllegalArgumentException("committed version must be positive");
                }
            }
        }

        /** Restart/history or ambiguous commit with no independent send/unsent proof. */
        record UnreadableStore(InvocationIdentifiers identifiers) implements Evidence {
            public UnreadableStore {
                Objects.requireNonNull(identifiers, "identifiers");
            }
        }

        enum Progress {
            ACTIVE,
            DEADLINE
        }
    }

    /** Positive-unsent causes allowed by the C3 row; never an issuer UNKNOWN. */
    public enum UnsentCause {
        ADMISSION_UNAVAILABLE(ErrorCode.BUSINESS_ADMISSION_UNAVAILABLE),
        CAPACITY(ErrorCode.EVENT_CAPACITY);

        private final ErrorCode error;

        UnsentCause(ErrorCode error) {
            this.error = error;
        }

        public ErrorCode error() {
            return error;
        }
    }

    /**
     * Closed fact bundle. {@code denial}, {@code preAdmissionFailure} and
     * {@code historyTargetUnknown} are checked before {@code evidence}, so a later fact
     * can never leak through an earlier denial.
     */
    public record Facts(Denial denial, PreAdmissionFailure preAdmissionFailure,
            boolean historyTargetUnknown, Evidence evidence, Origin origin,
            AttemptIdentifiers attempt, Instant asOf) {
        public Facts {
            Objects.requireNonNull(denial, "denial");
            Objects.requireNonNull(evidence, "evidence");
            Objects.requireNonNull(origin, "origin");
            Objects.requireNonNull(attempt, "attempt");
            Objects.requireNonNull(asOf, "asOf");
        }
    }
}
