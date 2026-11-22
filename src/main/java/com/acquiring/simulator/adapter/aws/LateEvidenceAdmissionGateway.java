package com.acquiring.simulator.adapter.aws;

import com.acquiring.simulator.domain.event.CanonicalEventCodec;
import com.acquiring.simulator.domain.event.CommittedVersionEvidence;
import com.acquiring.simulator.domain.event.CriticalCapacity;
import com.acquiring.simulator.domain.event.EventAppendResult;
import com.acquiring.simulator.domain.event.EventDeduplication;
import com.acquiring.simulator.domain.event.EventDedupeKey;
import com.acquiring.simulator.domain.event.EventSubmission;
import com.acquiring.simulator.domain.event.LateEvidenceReceipt;
import com.acquiring.simulator.domain.event.OperationAManifest;
import com.acquiring.simulator.domain.event.StoredEvent;
import com.acquiring.simulator.domain.model.AuthorizationRecord;
import com.acquiring.simulator.domain.model.AuthorizationState;
import com.acquiring.simulator.domain.model.DecisionSource;
import com.acquiring.simulator.domain.model.DownstreamReply;
import com.acquiring.simulator.domain.model.IssuerDecision;
import com.acquiring.simulator.domain.model.LateEvidenceAdmission;
import com.acquiring.simulator.domain.port.EventStore;
import com.acquiring.simulator.domain.port.LateEvidenceGateway;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.dynamodb.model.TransactionCanceledException;

/**
 * Internal verified-evidence admission gateway for late issuer evidence (observation contract
 * O3/P5; channel contract C4 late-delivery paragraph; R07; OBS-08/OBS-10).
 *
 * <p>The approved text admits late evidence through the <em>same internal admission</em> as normal
 * evidence: {@code observation-contract.md:29} ("Late evidence uses same internal admission; no
 * public late endpoint/automatic financial recovery") and {@code iso8583-profile.md:93-94}
 * ("Normal and late admission use the SAME internal processor handler contract"). This class is the
 * narrow, local admission step of that handler. It reads the durable original strongly, authorizes
 * the candidate against it, classifies the evidence timing, and only then either recognizes an
 * already-retained result or composes one atomic late confirmation. There is no HTTP route, no
 * public endpoint, no notifier and no service/runtime wiring here.
 *
 * <p><strong>Authentication seam.</strong> The {@link DownstreamReply} handed to {@link #admit} is
 * the explicit internal test seam: it is assumed to have passed C4 sender/relay/role/key/signature
 * and hash verification <em>before</em> this gateway. This class does NOT verify envelopes,
 * signatures, keys, P6 ISO structure or attestations, and it accepts no {@code VALID} flag. It
 * performs only context authorization that needs the durable record: the reply must be a correlated
 * {@code APPROVED}/{@code DECLINED} issuer decision available no earlier than the durable dispatch,
 * with a bounded delivery receipt and a delivery time no earlier than the evidence time. A green
 * test is therefore <strong>not</strong> proof that a reply was cryptographically authenticated
 * ({@code channel-contract.md:42-43}).
 *
 * <p><strong>Order.</strong> Authorization runs before any dedupe lookup and before any
 * state-based refusal. Only after the candidate is authorized does the gateway apply evidence-time
 * classification and dedupe/replay logic. That ordering is what keeps an unauthorized candidate
 * from being reported as an exact replay or a content conflict.
 *
 * <p><strong>Evidence time, not delivery time.</strong> Late versus delayed is decided by the
 * evidence availability time only ({@code iso8583-profile.md:52,54}: evidence time may be later than
 * sent; late replies retain the original send time and use {@code at} as reply time). Evidence
 * available at or before the deadline is on time even if it is delivered afterwards: that is
 * {@link LateEvidenceCriticalComposer.Timing#DELIVERY_DELAY}, and it belongs to the <em>normal</em>
 * verified-result admission. It is not permanently lost evidence and this gateway never relabels it
 * as late or refuses it as finished.
 *
 * <p><strong>Fresh-read replay.</strong> A known outcome is recognized from a freshly read terminal
 * record plus a strongly consistent lookup of the stable result identity, never from a caller-held
 * snapshot. An identical redelivery returns the persisted state and committed version without a
 * second event, HEAD advance, slot or receipt. Evidence whose stable identity already exists with
 * different content is a conflict: the retained confirmation is preserved, never overwritten
 * ({@code iso8583-profile.md:81}; {@code acceptance-scenarios.md:21,23}).
 *
 * <p><strong>Missing is unknown.</strong> An absent record, unreadable version evidence, a confirmed
 * record with no retained result row, a failed lookup, or a legacy uncertainty row without the
 * additive committed version is {@link Status#UNKNOWN}. Nothing is migrated, no committed version
 * is fabricated, and unknown is never reported as "no result".
 *
 * <p><strong>Ambiguous commit.</strong> If the atomic write's outcome cannot be established, the
 * gateway performs exactly one strongly consistent receipt lookup and never resends. A matching
 * retained result is {@link Status#RECOVERED} (knowledge only); a different one is a conflict; an
 * empty or failing lookup stays {@link Status#UNKNOWN}. A definite transaction cancellation is
 * {@link Status#NOT_COMMITTED} unless a concurrent identical commit can be proven.
 *
 * <p>Deliberately out of scope: durable quarantine/flagging of contradictory evidence, the legacy
 * {@code NOT_CONFIRMED} disposition, timeout event vocabulary, the {@code AuthorizationService}
 * branch, C4 cryptographic verification, HTTP transport and {@code AwsRuntime} activation.
 * Capacity is mandatory and explicit; the O8 numbers remain PROPOSED, so no budget is defaulted.
 */
public final class LateEvidenceAdmissionGateway implements LateEvidenceGateway {

    /**
     * Result of exactly one admission attempt. {@code timing} is the evidence-time routing
     * classification; it is {@code null} only when the attempt could not be authorized or the
     * record was unreadable.
     */
    public enum Status {
        /** This attempt committed one late confirmed result. */
        COMMITTED,
        /** Fresh terminal read and receipt lookup show the identical evidence is already retained. */
        REPLAY,
        /** An ambiguous write was resolved by lookup as already committed; knowledge only. */
        RECOVERED,
        /**
         * Evidence was available at or before the deadline. It belongs to the normal verified-result
         * admission (on time, or a delivery delay); this gateway wrote nothing and lost nothing.
         */
        NORMAL_RESULT_ADMISSION,
        /** A different confirmed outcome or retained content exists; the known outcome is preserved. */
        CONTENT_CONFLICT,
        /** The candidate is not authorized for this operation; nothing was read for dedupe or written. */
        REFUSED,
        /** The transaction was definitively rejected and no concurrent identical commit was proven. */
        NOT_COMMITTED,
        /** State could not be established; never proof that no result exists. */
        UNKNOWN
    }

    /** The one atomic append seam; production binds it to {@link DynamoEventStore#append}. */
    @FunctionalInterface
    public interface Committer {
        EventAppendResult append(EventSubmission submission, CriticalWriteComposer composer);
    }

    /**
     * @param status          outcome classification
     * @param timing          evidence-time routing classification, or {@code null} when unauthorized
     * @param confirmedState  confirmed state read back from persistence, or {@code null}
     * @param evidence        persisted committed-version evidence, or {@code null} when not read
     * @param detail          safe, non-payload diagnostic; never contains event content
     */
    public record Decision(Status status, LateEvidenceCriticalComposer.Timing timing,
            AuthorizationState confirmedState, CommittedVersionEvidence evidence, String detail) {

        public Decision {
            Objects.requireNonNull(status, "status");
        }

        /** True only for a confirmed result read back from persistence with recorded evidence. */
        public boolean recorded() {
            return (status == Status.COMMITTED || status == Status.REPLAY
                            || status == Status.RECOVERED)
                    && (confirmedState == AuthorizationState.APPROVED
                            || confirmedState == AuthorizationState.DECLINED)
                    && evidence != null
                    && evidence.committedVersionRecorded();
        }
    }

    /** Storage CAS base written by the dispatch-intent transition. */
    private static final long PRIOR_INTENT_STORAGE_VERSION = 1L;
    /** Storage CAS value written by the late confirmation. */
    private static final long LATE_EVIDENCE_STORAGE_VERSION = 2L;
    /** Channel-contract bound for {@code deliveryReceiptId}. */
    private static final int MAX_RECEIPT_ID_LENGTH =
            LateEvidenceCriticalComposer.MAX_RECEIPT_ID_LENGTH;

    private final EventStore store;
    private final Committer committer;
    private final DynamoAuthorizationRepository repository;
    private final String journalTable;
    private final String eventTable;
    private final CriticalCapacity capacity;
    private final String environment;
    private final String instanceId;

    /**
     * Internal seam constructor. The {@code committer} exists so a local test can simulate one
     * ambiguous commit outcome without a second write; production should use {@link #over}.
     */
    public LateEvidenceAdmissionGateway(EventStore store, Committer committer,
            DynamoAuthorizationRepository repository, String journalTable, String eventTable,
            CriticalCapacity capacity, String environment, String instanceId) {
        this.store = Objects.requireNonNull(store, "store");
        this.committer = Objects.requireNonNull(committer, "committer");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.journalTable = requireText(journalTable, "journalTable");
        this.eventTable = requireText(eventTable, "eventTable");
        this.capacity = Objects.requireNonNull(capacity, "capacity");
        this.environment = requireText(environment, "environment");
        this.instanceId = requireText(instanceId, "instanceId");
    }

    /** Production binding: lookups and atomic appends both go through the same event store. */
    public static LateEvidenceAdmissionGateway over(DynamoEventStore store,
            DynamoAuthorizationRepository repository, String journalTable, String eventTable,
            CriticalCapacity capacity, String environment, String instanceId) {
        Objects.requireNonNull(store, "store");
        return new LateEvidenceAdmissionGateway(store, store::append, repository, journalTable,
                eventTable, capacity, environment, instanceId);
    }

    /**
     * Domain-port binding used by an optional internal service seam. It maps the adapter's
     * admission decision onto the narrow persisted model; no extra write, lookup or classification
     * happens here.
     */
    @Override
    public LateEvidenceAdmission submit(String authorizationId, DownstreamReply evidence,
            Instant deadline, Instant deliveredAt, String deliveryReceiptId) {
        Decision decision = admit(authorizationId, evidence, deadline, deliveredAt,
                deliveryReceiptId);
        return new LateEvidenceAdmission(
                LateEvidenceAdmission.Status.valueOf(decision.status().name()),
                decision.confirmedState(), decision.evidence(), decision.detail());
    }

    /**
     * Admit one already-authenticated late-evidence candidate for {@code authorizationId}.
     *
     * @param authorizationId       durable operation reference; the record is re-read consistently
     * @param evidence              already-verified issuer reply (internal seam, not C4 verification)
     * @param deadline              caller-supplied admission deadline; this gateway invents no default
     * @param deliveredAt           delivery time of this candidate; never used to relabel late vs on time
     * @param deliveryReceiptId     bounded delivery receipt; duplicate protection is idempotent
     */
    public Decision admit(String authorizationId, DownstreamReply evidence, Instant deadline,
            Instant deliveredAt, String deliveryReceiptId) {
        String id = requireText(authorizationId, "authorizationId");

        AuthorizationRecord record;
        try {
            Optional<AuthorizationRecord> found = repository.findById(id);
            if (found.isEmpty()) {
                return unknown(null, "authorization reference is not readable");
            }
            record = found.get();
        } catch (RuntimeException unreadable) {
            return unknown(null, "authorization reference is not readable");
        }

        // Authorization precedes every dedupe lookup and every state-based refusal.
        String refusal = authorize(record, evidence, deadline, deliveredAt, deliveryReceiptId);
        if (refusal != null) {
            return new Decision(Status.REFUSED, null, null, null, refusal);
        }

        LateEvidenceCriticalComposer.Timing timing;
        try {
            timing = LateEvidenceCriticalComposer.classify(
                    evidence.availableAt(), deliveredAt, deadline);
        } catch (IllegalArgumentException invalidTiming) {
            return new Decision(Status.REFUSED, null, null, null, invalidTiming.getMessage());
        }
        if (timing != LateEvidenceCriticalComposer.Timing.LATE) {
            // On time or delivery-delayed: the normal verified-result admission owns it. Nothing is
            // written and nothing is declared lost.
            return new Decision(Status.NORMAL_RESULT_ADMISSION, timing, null, null,
                    "evidence timing " + timing + " belongs to the normal verified-result"
                            + " admission, not to late evidence");
        }

        CommittedVersionEvidence versionEvidence;
        try {
            versionEvidence = repository.readVersionEvidence(id);
        } catch (RuntimeException unreadable) {
            return unknown(timing, "committed-version evidence is not readable");
        }
        if (!versionEvidence.committedVersionRecorded()) {
            return unknown(timing,
                    "legacy record has no committed-version evidence; nothing is migrated");
        }

        if (record.state() == AuthorizationState.APPROVED
                || record.state() == AuthorizationState.DECLINED) {
            return replayOrConflict(record, evidence, timing, deliveryReceiptId);
        }
        if (record.state() == AuthorizationState.NETWORK_REJECTED) {
            return new Decision(Status.CONTENT_CONFLICT, timing, null, null,
                    "a processor rejection is already determined and is retained");
        }
        if (record.state() == AuthorizationState.NOT_CONFIRMED) {
            return unknown(timing,
                    "legacy uncertainty records are not transitioned; no migration is performed");
        }
        if (record.state() != AuthorizationState.AWAITING
                || record.decisionSource() != DecisionSource.NONE
                || record.outcomeDeterminedAt() != null) {
            return new Decision(Status.CONTENT_CONFLICT, timing, null, null,
                    "a determined outcome is already retained");
        }
        if (!versionEvidence.agreesWith(PRIOR_INTENT_STORAGE_VERSION,
                LateEvidenceCriticalComposer.PRIOR_INTENT_JOURNAL_VERSION)) {
            return unknown(timing,
                    "record does not carry the dispatch-intent version pair");
        }
        return commitOnce(record, evidence, timing, deadline, deliveredAt, deliveryReceiptId);
    }

    /**
     * Context authorization against the durable record. Returns a safe refusal detail, or
     * {@code null} when the candidate may proceed to timing/dedupe handling.
     */
    private static String authorize(AuthorizationRecord record, DownstreamReply evidence,
            Instant deadline, Instant deliveredAt, String receiptId) {
        if (record.dispatchedAt() == null || record.dispatchCorrelationId() == null
                || record.dispatchCorrelationId().isBlank()) {
            return "no durable dispatch correlation authorizes this evidence";
        }
        if (evidence == null) {
            return "evidence is missing";
        }
        if (evidence.decision() != IssuerDecision.APPROVED
                && evidence.decision() != IssuerDecision.DECLINED) {
            return "only an APPROVED or DECLINED issuer decision is admissible";
        }
        if (evidence.correlationId() == null || evidence.correlationId().isBlank()
                || !evidence.correlationId().equals(record.dispatchCorrelationId())) {
            return "evidence correlation does not match the durable dispatch";
        }
        if (evidence.availableAt() == null) {
            return "evidence availability time is missing";
        }
        if (evidence.availableAt().isBefore(record.dispatchedAt())) {
            return "evidence cannot predate the durable dispatch";
        }
        if (deliveredAt == null) {
            return "delivery time is missing";
        }
        if (deliveredAt.isBefore(evidence.availableAt())) {
            return "delivery cannot precede evidence availability";
        }
        if (deadline == null) {
            return "admission deadline is missing";
        }
        if (deadline.isBefore(record.dispatchedAt())) {
            return "admission deadline cannot precede the durable dispatch";
        }
        if (receiptId == null || receiptId.strip().isEmpty()
                || receiptId.strip().length() > MAX_RECEIPT_ID_LENGTH) {
            return "deliveryReceiptId must be 1-" + MAX_RECEIPT_ID_LENGTH + " characters";
        }
        return null;
    }

    /**
     * Fresh terminal-record replay/conflict handling. The stable receipt/event identity comes from
     * the persisted receipt (evidence time and confirmed state) and the retained result event, not
     * from a caller-held snapshot and not from the boot instance that produced the candidate. A
     * known outcome is never composed over.
     */
    private Decision replayOrConflict(AuthorizationRecord record, DownstreamReply evidence,
            LateEvidenceCriticalComposer.Timing timing, String deliveryReceiptId) {
        AuthorizationState expected = expectedState(evidence);
        LateEvidenceReceipt receipt;
        CommittedVersionEvidence versionEvidence;
        try {
            Optional<LateEvidenceReceipt> found = repository.readLateEvidenceReceipt(record.id());
            if (found.isEmpty()) {
                return new Decision(Status.CONTENT_CONFLICT, timing, null, null,
                        "a confirmed outcome from a different evidence origin is retained");
            }
            receipt = found.get();
            versionEvidence = repository.readVersionEvidence(record.id());
        } catch (RuntimeException unreadable) {
            return unknown(timing, "retained late-evidence receipt is not readable");
        }
        if (!receipt.late()
                || !receipt.evidenceAt().equals(
                        evidence.availableAt().truncatedTo(java.time.temporal.ChronoUnit.SECONDS))
                || record.state() != expected
                || record.decisionSource() != DecisionSource.ISSUER
                || !evidence.availableAt().equals(record.outcomeDeterminedAt())) {
            return new Decision(Status.CONTENT_CONFLICT, timing, null, null,
                    "a different confirmed outcome is retained");
        }

        EventDedupeKey key = resultIdentity(record, expected);
        Optional<StoredEvent> stored;
        try {
            stored = store.find(key);
        } catch (RuntimeException lookupFailure) {
            return unknown(timing, "retained result lookup failed; state is unknown");
        }
        if (stored.isEmpty()) {
            return unknown(timing, "confirmed record has no retained result event");
        }
        StoredEvent event = stored.get();
        if (event.journalVersion() == null
                || event.journalVersion()
                        != LateEvidenceCriticalComposer.LATE_EVIDENCE_JOURNAL_VERSION
                || !expected.name().equals(event.stateAfter())
                || !StoredEvent.ACTIVE.equals(event.status())) {
            return unknown(timing,
                    "retained result metadata is not readable as a committed result");
        }
        if (!versionEvidence.agreesWith(LATE_EVIDENCE_STORAGE_VERSION,
                LateEvidenceCriticalComposer.LATE_EVIDENCE_JOURNAL_VERSION)) {
            return unknown(timing, "committed result version does not match");
        }
        String detail = receipt.deliveryReceiptId().equals(
                deliveryReceiptId == null ? null : deliveryReceiptId.strip())
                ? null
                : "identical evidence already retained under the original delivery receipt";
        return new Decision(Status.REPLAY, timing, expected, versionEvidence, detail);
    }

    /** Compose the one atomic late confirmation for a durable, still-undetermined intent. */
    private Decision commitOnce(AuthorizationRecord intent, DownstreamReply evidence,
            LateEvidenceCriticalComposer.Timing timing, Instant deadline, Instant deliveredAt,
            String deliveryReceiptId) {
        AuthorizationState expected = expectedState(evidence);
        EventSubmission submission = LateEvidenceCriticalComposer.submission(
                intent, evidence, environment, instanceId);
        LateEvidenceCriticalComposer composer =
                LateEvidenceCriticalComposer.withSlotAccounting(journalTable, eventTable, intent,
                        evidence, deadline, deliveredAt, deliveryReceiptId, capacity);
        try {
            EventAppendResult result = committer.append(submission, composer);
            return switch (result.decision()) {
                case NEW -> persisted(Status.COMMITTED, timing, expected, intent.id());
                case EXACT_REPLAY -> persisted(Status.REPLAY, timing, expected, intent.id());
                case CONTENT_CONFLICT -> new Decision(Status.CONTENT_CONFLICT, timing, null,
                        null, "late evidence identity exists with different content");
            };
        } catch (TransactionCanceledException cancelled) {
            return afterCancellation(submission, expected, timing, intent.id());
        } catch (SdkException ambiguous) {
            return resolveAmbiguous(submission, expected, timing, intent.id());
        }
    }

    /**
     * A cancelled transaction committed nothing. A concurrent identical commit may still be
     * provable from the fresh record and receipt; a different determined outcome is retained.
     */
    private Decision afterCancellation(EventSubmission submission, AuthorizationState expected,
            LateEvidenceCriticalComposer.Timing timing, String authorizationId) {
        Decision retained = lookupRetained(submission, expected, timing, authorizationId);
        if (retained.status() == Status.REPLAY) {
            return new Decision(Status.REPLAY, timing, retained.confirmedState(),
                    retained.evidence(), retained.detail());
        }
        if (retained.status() == Status.CONTENT_CONFLICT) {
            return retained;
        }
        return new Decision(Status.NOT_COMMITTED, timing, null, null,
                "transaction cancelled; this attempt committed nothing");
    }

    /**
     * One strongly consistent receipt lookup for an ambiguous write. No resend, no retry and no
     * second append: recovery is knowledge only.
     */
    private Decision resolveAmbiguous(EventSubmission submission, AuthorizationState expected,
            LateEvidenceCriticalComposer.Timing timing, String authorizationId) {
        Decision retained = lookupRetained(submission, expected, timing, authorizationId);
        if (retained.status() == Status.REPLAY) {
            return new Decision(Status.RECOVERED, timing, retained.confirmedState(),
                    retained.evidence(), null);
        }
        if (retained.status() == Status.CONTENT_CONFLICT) {
            return retained;
        }
        return unknown(timing, "ambiguous late-evidence commit; state could not be established");
    }

    /** Classify the stable receipt/event identity against a fresh, strongly consistent lookup. */
    private Decision lookupRetained(EventSubmission submission, AuthorizationState expected,
            LateEvidenceCriticalComposer.Timing timing, String authorizationId) {
        String incomingSha = CanonicalEventCodec.sha256(submission.event());
        Optional<StoredEvent> stored;
        try {
            stored = store.find(submission.dedupeKey());
        } catch (RuntimeException lookupFailure) {
            return unknown(timing, "retained evidence lookup failed");
        }
        if (stored.isEmpty()) {
            return unknown(timing, "no retained evidence found");
        }
        StoredEvent event = stored.get();
        if (EventDeduplication.classify(event.eventSha256(), incomingSha)
                != EventDeduplication.Decision.EXACT_REPLAY) {
            return new Decision(Status.CONTENT_CONFLICT, timing, null, null,
                    "retained evidence has different content");
        }
        if (event.journalVersion() == null
                || event.journalVersion()
                        != LateEvidenceCriticalComposer.LATE_EVIDENCE_JOURNAL_VERSION
                || !expected.name().equals(event.stateAfter())) {
            return new Decision(Status.CONTENT_CONFLICT, timing, null, null,
                    "retained evidence metadata does not match the confirmed outcome");
        }
        return persisted(Status.REPLAY, timing, expected, authorizationId);
    }

    /**
     * Read the confirmed result and its committed version back. Missing, unreadable or
     * non-matching evidence is UNKNOWN or a preserved conflict, never a synthesised success.
     */
    private Decision persisted(Status status, LateEvidenceCriticalComposer.Timing timing,
            AuthorizationState expected, String authorizationId) {
        AuthorizationRecord stored;
        CommittedVersionEvidence evidence;
        try {
            Optional<AuthorizationRecord> found = repository.findById(authorizationId);
            if (found.isEmpty()) {
                return unknown(timing, "confirmed record is absent");
            }
            stored = found.get();
            evidence = repository.readVersionEvidence(authorizationId);
        } catch (RuntimeException unreadable) {
            return unknown(timing, "confirmed record evidence is unreadable");
        }
        if (stored.state() != expected) {
            if (determined(stored.state())) {
                return new Decision(Status.CONTENT_CONFLICT, timing, null, null,
                        "competing terminal outcome retained");
            }
            return unknown(timing, "confirmed state does not match");
        }
        if (stored.decisionSource() != DecisionSource.ISSUER) {
            return new Decision(Status.CONTENT_CONFLICT, timing, null, null,
                    "confirmed result lacks issuer provenance");
        }
        if (!evidence.agreesWith(LATE_EVIDENCE_STORAGE_VERSION,
                LateEvidenceCriticalComposer.LATE_EVIDENCE_JOURNAL_VERSION)) {
            return unknown(timing, "committed result version does not match");
        }
        return new Decision(status, timing, expected, evidence, null);
    }

    /**
     * Stable result identity: the confirmed state plus the operation reference, under the fixed
     * PROCESSOR producer in this environment. This is the same identity the late composer derives,
     * so no second event identity is invented.
     */
    private EventDedupeKey resultIdentity(AuthorizationRecord record, AuthorizationState expected) {
        return new EventDedupeKey(environment, OperationAManifest.PRODUCER_PROCESSOR,
                expected.name() + "-" + record.id());
    }

    private static AuthorizationState expectedState(DownstreamReply evidence) {
        if (evidence.decision() == IssuerDecision.APPROVED) {
            return AuthorizationState.APPROVED;
        }
        if (evidence.decision() == IssuerDecision.DECLINED) {
            return AuthorizationState.DECLINED;
        }
        throw new IllegalArgumentException("late evidence must be APPROVED or DECLINED");
    }

    private static boolean determined(AuthorizationState state) {
        return state == AuthorizationState.APPROVED || state == AuthorizationState.DECLINED;
    }

    private static Decision unknown(LateEvidenceCriticalComposer.Timing timing, String detail) {
        return new Decision(Status.UNKNOWN, timing, null, null, detail);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value;
    }
}
