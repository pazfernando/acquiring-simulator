package com.acquiring.simulator.adapter.aws;

import com.acquiring.simulator.channel.C4AuthenticatedTransportContext;
import com.acquiring.simulator.channel.C4CarrierCandidate;
import com.acquiring.simulator.channel.C4EvidenceAttestation;
import com.acquiring.simulator.channel.C4EvidenceVerifier;
import com.acquiring.simulator.channel.C4KeyRegistry;
import com.acquiring.simulator.channel.C4P6BindingDecision;
import com.acquiring.simulator.channel.C4P6BindingException;
import com.acquiring.simulator.channel.C4P6SignedFieldBinder;
import com.acquiring.simulator.channel.C4VerificationException;
import com.acquiring.simulator.channel.C4VerifiedAttestation;
import com.acquiring.simulator.domain.event.CanonicalEventCodec;
import com.acquiring.simulator.domain.event.CommittedVersionEvidence;
import com.acquiring.simulator.domain.event.CriticalCapacity;
import com.acquiring.simulator.domain.event.EventAppendResult;
import com.acquiring.simulator.domain.event.EventDedupeKey;
import com.acquiring.simulator.domain.event.EventDeduplication;
import com.acquiring.simulator.domain.event.EventSubmission;
import com.acquiring.simulator.domain.event.LateEvidenceReceipt;
import com.acquiring.simulator.domain.event.OperationAManifest;
import com.acquiring.simulator.domain.event.ProcessorEvidenceReceipt;
import com.acquiring.simulator.domain.event.SignedEvidenceConflict;
import com.acquiring.simulator.domain.event.StoredEvent;
import com.acquiring.simulator.domain.model.AuthorizationRecord;
import com.acquiring.simulator.domain.model.AuthorizationState;
import com.acquiring.simulator.domain.model.DecisionSource;
import com.acquiring.simulator.domain.model.DownstreamReply;
import com.acquiring.simulator.domain.model.IssuerDecision;
import com.acquiring.simulator.domain.port.OriginalInstructionReader;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.Optional;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.dynamodb.model.TransactionCanceledException;

/**
 * One internal signed late-evidence admission gateway (observation contract O3/P5; channel contract
 * C4/P6 late-delivery paragraph; R07; OBS-08/OBS-10).
 *
 * <p>This is the late counterpart of {@link SignedEvidenceReceiptGateway} and deliberately reuses
 * its trust model instead of introducing a second one: the same
 * {@link SignedEvidenceReceiptGateway.TrustedContextAuthorizer} seam, the same real C4 DIRECT/RELAY
 * verifier, the same real signed-P5/P6 binder against the persisted original and exact received
 * response, the same {@link ProcessorEvidenceReceipt} identity
 * {@code (environmentId, attestingPrincipal, evidenceId)} and canonical attestation hash, and the
 * same {@link SignedEvidenceReceiptGateway.ConflictRecorder} durable quarantine marker. There is no
 * caller-supplied "verified"/"bound"/"already verified" flag and no pre-verified reply DTO: the
 * verified result and the bound decision are produced inside this call.</p>
 *
 * <p>Order is deliberate and identical in spirit to the normal gateway. The trusted transport
 * context is authorized first, before any storage read. Only authenticated evidence may name the
 * operation, so the strongly consistent reads of the persisted original record and the persisted
 * original {@code 0100} octets happen after verification. The late envelope inputs (deadline,
 * delivery time, delivery receipt id) are authorized against the durable dispatch only after the
 * signed binding. The receipt lookup and any conflict marker come last: a receipt is a durable fact
 * about the first admitted evidence, never an oracle for forged, unauthenticated or unbound
 * material.</p>
 *
 * <p>Evidence timing is decided by the <em>evidence time</em> only
 * ({@link LateEvidenceCriticalComposer#classify}). Evidence available at or before the deadline is
 * on time even when it is delivered afterwards ({@link LateEvidenceCriticalComposer.Timing#ON_TIME}
 * or {@code DELIVERY_DELAY}); this gateway writes nothing for it, performs no receipt lookup and
 * returns {@link Status#NORMAL_RESULT_ADMISSION}, so the normal verified-result admission remains
 * the single owner of that write. It is never relabelled as late and never declared lost.</p>
 *
 * <p>Only {@link LateEvidenceCriticalComposer.Timing#LATE} evidence proceeds. A fresh receipt match
 * on the exact P5 identity and canonical hash is a {@link Status#REPLAY} even after the record has
 * reached a terminal state: the first receipt, its first {@code collectedAt}, the first late
 * metadata, the critical slot and the event sequence are all preserved and no second transaction is
 * composed. A different content hash for an existing identity, or a different signed identity that
 * contradicts a determined record, writes one durable sanitized
 * {@link SignedEvidenceConflict} marker and preserves the established confirmation.</p>
 *
 * <p>A first late admission composes through the existing
 * {@link LateSignedEvidenceReceiptComposer}, so the confirmed record, its explicitly advanced
 * committed version, the late receipt metadata, the immutable critical result event, EVENT HEAD,
 * dedupe metadata, the optional critical slot and the normalized processor receipt all travel in
 * ONE {@code TransactWriteItems}. Capacity is mandatory and explicit; the O8 numbers remain
 * PROPOSED, so no budget is defaulted.</p>
 *
 * <p>An ambiguous SDK outcome is resolved by exactly one strongly consistent lookup of the receipt,
 * the retained result event and the business version/state. Only a mutually consistent triple is
 * {@link Status#RECOVERED} (knowledge only). Absence or a failed read is {@link Status#UNKNOWN},
 * never proof that nothing was committed. There is no resend, no retry of the business write, no
 * fallback and no downstream call.</p>
 *
 * <p><strong>Not production-wired.</strong> This class is not referenced by
 * {@code AuthorizationService} or {@code AwsRuntime}. It has no HTTP route, no public endpoint, no
 * runtime activation and no downstream call. The quarantine marker is metadata only: it is not a
 * financial resolution, resend, reversal or automatically authorized correction.</p>
 */
public final class LateSignedEvidenceReceiptGateway {

    /** Outcome classification of exactly one signed late-evidence admission attempt. */
    public enum Status {
        /** This attempt committed one new late receipt, confirmed result and critical event. */
        COMMITTED,
        /** The identical P5 identity and canonical hash were already retained; nothing was written. */
        REPLAY,
        /** An ambiguous write was resolved by lookup as already committed; knowledge only. */
        RECOVERED,
        /**
         * Evidence was available at or before the deadline (on time, or a delivery delay). The
         * normal verified-result admission owns it; this gateway wrote nothing and lost nothing.
         */
        NORMAL_RESULT_ADMISSION,
        /**
         * The identity exists with different content, or a different signed identity contradicts a
         * determined outcome; the established confirmation is preserved.
         */
        CONTENT_CONFLICT,
        /** Authentication, context authorization or signed binding refused the candidate. */
        REFUSED,
        /** The transaction was definitively cancelled and no identical commit was proven. */
        NOT_COMMITTED,
        /** State could not be established; UNAVAILABLE semantics, never proof of no-send. */
        UNKNOWN
    }

    /**
     * @param status          outcome classification
     * @param timing          evidence-time routing classification, or {@code null} when the attempt
     *                        was refused or could not be read before classification
     * @param confirmedState  {@code APPROVED}/{@code DECLINED} read back from persistence, or null
     * @param evidence        persisted committed-version evidence, or {@code null} when not read
     * @param receipt         stored normalized processor receipt, or {@code null} when not read
     * @param lateMetadata    first persisted late-evidence receipt metadata, or {@code null} when
     *                        the first admission was not late or none was read
     * @param detail          safe non-payload diagnostic; never contains event content or evidence
     * @param quarantine      durable sanitized conflict marker written for this attempt, or
     *                        {@code null} when none applies or persistence failed
     */
    public record Decision(Status status, LateEvidenceCriticalComposer.Timing timing,
            AuthorizationState confirmedState, CommittedVersionEvidence evidence,
            ProcessorEvidenceReceipt receipt, LateEvidenceReceipt lateMetadata, String detail,
            SignedEvidenceConflict quarantine) {

        public Decision {
            Objects.requireNonNull(status, "status");
        }

        /**
         * True only when the receipt, a confirmed state, the persisted committed-version evidence
         * and the retained late metadata were all read back. Replay and recovery confirm existence;
         * they grant no downstream action.
         */
        public boolean recorded() {
            return (status == Status.COMMITTED || status == Status.REPLAY
                            || status == Status.RECOVERED)
                    && (confirmedState == AuthorizationState.APPROVED
                            || confirmedState == AuthorizationState.DECLINED)
                    && evidence != null
                    && evidence.committedVersionRecorded()
                    && receipt != null
                    && lateMetadata != null
                    && lateMetadata.late();
        }
    }

    /** Storage CAS base written by the dispatch-intent transition. */
    private static final long PRIOR_INTENT_STORAGE_VERSION = 1L;
    /** Storage CAS value written by the late confirmation. */
    private static final long LATE_EVIDENCE_STORAGE_VERSION = 2L;
    /** Channel-contract bound for {@code deliveryReceiptId}. */
    private static final int MAX_RECEIPT_ID_LENGTH =
            LateEvidenceCriticalComposer.MAX_RECEIPT_ID_LENGTH;

    private final DynamoEventStore store;
    private final DynamoAuthorizationRepository repository;
    private final OriginalInstructionReader originals;
    private final SignedEvidenceReceiptGateway.TrustedContextAuthorizer contextAuthorizer;
    private final SignedEvidenceReceiptGateway.ConflictRecorder conflictRecorder;
    private final String journalTable;
    private final String eventTable;
    private final CriticalCapacity capacity;
    private final String environment;
    private final String instanceId;

    public LateSignedEvidenceReceiptGateway(DynamoEventStore store,
            DynamoAuthorizationRepository repository, OriginalInstructionReader originals,
            SignedEvidenceReceiptGateway.TrustedContextAuthorizer contextAuthorizer,
            String journalTable, String eventTable, CriticalCapacity capacity, String environment,
            String instanceId) {
        this(store, repository, originals, contextAuthorizer, journalTable, eventTable, capacity,
                environment, instanceId,
                repository == null ? null : repository::recordEvidenceConflict);
    }

    /**
     * Internal seam: the same admission mechanics with an explicit conflict recorder, so a local
     * test can simulate a quarantine-persistence failure without a second storage write.
     */
    public LateSignedEvidenceReceiptGateway(DynamoEventStore store,
            DynamoAuthorizationRepository repository, OriginalInstructionReader originals,
            SignedEvidenceReceiptGateway.TrustedContextAuthorizer contextAuthorizer,
            String journalTable, String eventTable, CriticalCapacity capacity, String environment,
            String instanceId, SignedEvidenceReceiptGateway.ConflictRecorder conflictRecorder) {
        this.store = Objects.requireNonNull(store, "store");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.originals = Objects.requireNonNull(originals, "originals");
        this.contextAuthorizer = Objects.requireNonNull(contextAuthorizer, "contextAuthorizer");
        this.conflictRecorder = Objects.requireNonNull(conflictRecorder, "conflictRecorder");
        this.journalTable = requireText(journalTable, "journalTable");
        this.eventTable = requireText(eventTable, "eventTable");
        this.capacity = Objects.requireNonNull(capacity, "capacity");
        this.environment = requireText(environment, "environment");
        this.instanceId = requireText(instanceId, "instanceId");
    }

    /**
     * Admit one signed candidate carrying late-delivery envelope facts.
     *
     * @param candidate            shape-checked but explicitly unverified C4 candidate
     * @param signed               exact signed attestation content the candidate carries
     * @param context              authenticated transport context (never a client value)
     * @param registry             configured principal/key registry
     * @param receivedResponse0110 exact received {@code 0110} response octets
     * @param deadline             caller-supplied admission deadline; no default is invented
     * @param deliveredAt          delivery time of this candidate; never used to relabel late
     * @param deliveryReceiptId    bounded delivery receipt identity; not a decision authority
     */
    public Decision admit(C4CarrierCandidate candidate, C4EvidenceAttestation signed,
            C4AuthenticatedTransportContext context, C4KeyRegistry registry,
            byte[] receivedResponse0110, Instant deadline, Instant deliveredAt,
            String deliveryReceiptId) {
        Objects.requireNonNull(candidate, "candidate");
        Objects.requireNonNull(signed, "signed");
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(registry, "registry");

        // 1. Trusted context authorization precedes every storage read.
        try {
            contextAuthorizer.requireAuthorized(context);
        } catch (RuntimeException denied) {
            return refused("trusted transport context is not authorized");
        }

        // 2. Real C4 verification: signature, key, principal, role, mandate, hashes.
        C4VerifiedAttestation verified;
        try {
            verified = C4EvidenceVerifier.verify(candidate, context, registry);
        } catch (C4VerificationException rejected) {
            return refused("C4 evidence verification was refused");
        }
        if (!environment.equals(verified.environmentId())) {
            return refused("verified evidence environment does not match this admission");
        }

        // 3. Strongly read the persisted original business record and exact original bytes.
        AuthorizationRecord record;
        try {
            Optional<AuthorizationRecord> found = repository.findById(verified.authorizationId());
            if (found.isEmpty()) {
                return unknown(null, "persisted original record is absent");
            }
            record = found.get();
        } catch (RuntimeException unreadable) {
            return unknown(null, "persisted original record is unreadable");
        }

        byte[] persistedOriginal;
        try {
            Optional<byte[]> found = originals.readPersistedOriginal0100(verified.authorizationId());
            if (found.isEmpty()) {
                return unknown(null, "persisted original instruction is absent");
            }
            persistedOriginal = found.get();
        } catch (RuntimeException unreadable) {
            return unknown(null, "persisted original instruction is unreadable");
        }

        // 4. Real signed-P5/P6 binding over the persisted original and exact received response.
        C4P6BindingDecision decision;
        try {
            decision = C4P6SignedFieldBinder.bind(
                    verified, signed, context, persistedOriginal, receivedResponse0110);
        } catch (C4P6BindingException rejected) {
            return refused("signed evidence does not bind to the persisted original and response");
        }
        AuthorizationState expected = expectedState(decision);

        // 5. Late envelope authorization against the durable dispatch, after authentication.
        String refusal = authorizeEnvelope(record, signed, deadline, deliveredAt,
                deliveryReceiptId);
        if (refusal != null) {
            return refused(refusal);
        }

        // 6. Evidence time alone decides late versus normal. On-time and delivery-delayed evidence
        //    is handed to the normal verified-result admission untouched.
        LateEvidenceCriticalComposer.Timing timing;
        try {
            timing = LateEvidenceCriticalComposer.classify(
                    Instant.parse(signed.availableAt()), deliveredAt, deadline);
        } catch (IllegalArgumentException invalidTiming) {
            return refused(invalidTiming.getMessage());
        }
        if (timing != LateEvidenceCriticalComposer.Timing.LATE) {
            return new Decision(Status.NORMAL_RESULT_ADMISSION, timing, null, null, null, null,
                    "evidence timing " + timing + " belongs to the normal verified-result"
                            + " admission; nothing was written or lost", null);
        }

        // 7. Receipt lookup only after authentication, binding and timing classification.
        ProcessorEvidenceReceipt existing;
        try {
            Optional<ProcessorEvidenceReceipt> found = repository.readEvidenceReceipt(
                    verified.environmentId(), verified.signingPrincipal(), verified.evidenceId());
            if (found.isEmpty()) {
                return compose(record, verified, signed, decision, expected, timing, deadline,
                        deliveredAt, deliveryReceiptId);
            }
            existing = found.get();
        } catch (RuntimeException unreadable) {
            return unknown(timing, "evidence receipt lookup failed");
        }
        if (!sameEvidence(existing, verified, signed, decision)) {
            return quarantined("evidence identity exists with different content",
                    reusedIdentity(verified, existing,
                            "evidence identity exists with different content"));
        }

        // An exact P5 identity/hash replay returns the first persisted facts, including when the
        // record already reached its terminal state.
        return persisted(Status.REPLAY, timing, expected, existing, verified.authorizationId(),
                resultIdentity(expected, verified.authorizationId()));
    }

    /**
     * Authorize the envelope facts against the durable record. These are not signed by the issuer
     * and are therefore used only after the signed binding.
     */
    private static String authorizeEnvelope(AuthorizationRecord record,
            C4EvidenceAttestation signed, Instant deadline, Instant deliveredAt,
            String deliveryReceiptId) {
        if (record.dispatchedAt() == null || record.dispatchCorrelationId() == null
                || record.dispatchCorrelationId().isBlank()) {
            return "no durable dispatch correlation authorizes this evidence";
        }
        if (deadline == null) {
            return "admission deadline is missing";
        }
        if (deadline.isBefore(record.dispatchedAt())) {
            return "admission deadline cannot precede the durable dispatch";
        }
        if (deliveredAt == null) {
            return "delivery time is missing";
        }
        Instant availableAt;
        try {
            availableAt = Instant.parse(signed.availableAt());
        } catch (RuntimeException invalid) {
            return "evidence availability time is not readable";
        }
        if (deliveredAt.isBefore(availableAt)) {
            return "delivery cannot precede evidence availability";
        }
        if (deliveryReceiptId == null || deliveryReceiptId.strip().isEmpty()
                || deliveryReceiptId.strip().length() > MAX_RECEIPT_ID_LENGTH) {
            return "deliveryReceiptId must be 1-" + MAX_RECEIPT_ID_LENGTH + " characters";
        }
        return null;
    }

    /** Compose the one atomic late receipt/result transaction for a still-undetermined intent. */
    private Decision compose(AuthorizationRecord record, C4VerifiedAttestation verified,
            C4EvidenceAttestation signed, C4P6BindingDecision decision,
            AuthorizationState expected, LateEvidenceCriticalComposer.Timing timing,
            Instant deadline, Instant deliveredAt, String deliveryReceiptId) {
        if (determined(record.state())) {
            return quarantined("a competing confirmed outcome is retained",
                    contradictoryOutcome(record, verified,
                            "a competing confirmed outcome is retained"));
        }
        if (record.state() == AuthorizationState.NOT_CONFIRMED) {
            return unknown(timing, "legacy uncertainty records are not transitioned");
        }
        if (record.state() != AuthorizationState.AWAITING
                || record.decisionSource() != DecisionSource.NONE
                || record.outcomeDeterminedAt() != null) {
            return quarantined("a determined outcome is already retained",
                    contradictoryOutcome(record, verified,
                            "a determined outcome is already retained"));
        }
        CommittedVersionEvidence version;
        try {
            version = repository.readVersionEvidence(record.id());
        } catch (RuntimeException unreadable) {
            return unknown(timing, "committed-version evidence is unreadable");
        }
        if (!version.agreesWith(PRIOR_INTENT_STORAGE_VERSION,
                ResultCriticalComposer.PRIOR_INTENT_JOURNAL_VERSION)) {
            return unknown(timing, "record does not carry the durable dispatch-intent version pair");
        }

        EventSubmission submission;
        LateSignedEvidenceReceiptComposer composer;
        try {
            Instant availableAt = Instant.parse(signed.availableAt());
            DownstreamReply reply = new DownstreamReply(
                    signed.correlationId(), IssuerDecision.valueOf(decision.result()), availableAt);
            submission = LateSignedEvidenceReceiptComposer.submission(record, reply, environment,
                    instanceId);
            composer = LateSignedEvidenceReceiptComposer.withSlotAccounting(
                    journalTable, eventTable, record, verified, signed, decision, deadline,
                    deliveredAt, deliveryReceiptId, capacity);
        } catch (IllegalArgumentException inconsistent) {
            // The record was read strongly but the authenticated evidence does not describe it.
            return refused("signed evidence does not describe the persisted original");
        }

        try {
            EventAppendResult result = store.append(submission, composer);
            return switch (result.decision()) {
                case NEW -> persisted(Status.COMMITTED, timing, expected, composer.receipt(),
                        record.id(), submission.dedupeKey());
                case EXACT_REPLAY -> inspectAfterAppend(
                        submission, verified, signed, decision, expected, timing, record.id());
                case CONTENT_CONFLICT -> contentConflict(
                        "result identity exists with different content");
            };
        } catch (TransactionCanceledException cancelled) {
            Decision retained = inspectAfterAppend(
                    submission, verified, signed, decision, expected, timing, record.id());
            if (retained.status() == Status.REPLAY) {
                return retained;
            }
            if (retained.status() == Status.CONTENT_CONFLICT) {
                return retained;
            }
            return new Decision(Status.NOT_COMMITTED, timing, null, null, null, null,
                    "transaction cancelled; this attempt committed nothing", null);
        } catch (SdkException ambiguous) {
            Decision retained = inspectAfterAppend(
                    submission, verified, signed, decision, expected, timing, record.id());
            if (retained.status() == Status.REPLAY) {
                return new Decision(Status.RECOVERED, timing, retained.confirmedState(),
                        retained.evidence(), retained.receipt(), retained.lateMetadata(), null,
                        null);
            }
            if (retained.status() == Status.CONTENT_CONFLICT) {
                return retained;
            }
            return unknown(timing,
                    "ambiguous commit; receipt, business and event are not consistently readable");
        }
    }

    /**
     * One strongly consistent lookup of the receipt, committed event and business version. This is
     * used for an ambiguous SDK outcome and for an event-store replay classification; it never
     * appends, retries or resends.
     */
    private Decision inspectAfterAppend(EventSubmission submission,
            C4VerifiedAttestation verified, C4EvidenceAttestation signed,
            C4P6BindingDecision decision, AuthorizationState expected,
            LateEvidenceCriticalComposer.Timing timing, String authorizationId) {
        ProcessorEvidenceReceipt receipt;
        try {
            Optional<ProcessorEvidenceReceipt> found = repository.readEvidenceReceipt(
                    verified.environmentId(), verified.signingPrincipal(), verified.evidenceId());
            if (found.isEmpty()) {
                return unknown(timing, "no committed receipt was found");
            }
            receipt = found.get();
        } catch (RuntimeException unreadable) {
            return unknown(timing, "receipt lookup failed");
        }
        if (!sameEvidence(receipt, verified, signed, decision)) {
            return quarantined("retained receipt has different content",
                    reusedIdentity(verified, receipt, "retained receipt has different content"));
        }

        StoredEvent stored;
        try {
            Optional<StoredEvent> found = store.find(submission.dedupeKey());
            if (found.isEmpty()) {
                return unknown(timing, "no committed event was found");
            }
            stored = found.get();
        } catch (RuntimeException unreadable) {
            return unknown(timing, "committed event lookup failed");
        }
        String incomingSha = CanonicalEventCodec.sha256(submission.event());
        if (EventDeduplication.classify(stored.eventSha256(), incomingSha)
                != EventDeduplication.Decision.EXACT_REPLAY) {
            return contentConflict("retained event has different content");
        }
        if (stored.journalVersion() == null
                || stored.journalVersion() != LateEvidenceCriticalComposer.LATE_EVIDENCE_JOURNAL_VERSION
                || !expected.name().equals(stored.stateAfter())
                || !StoredEvent.ACTIVE.equals(stored.status())) {
            return contentConflict("retained event metadata does not match the confirmed result");
        }
        return persisted(Status.REPLAY, timing, expected, receipt, authorizationId,
                submission.dedupeKey());
    }

    /**
     * Read the confirmed record, its committed version, the stored receipt, the retained late
     * metadata and the retained result event back. Missing, unreadable, non-matching or
     * non-issuer provenance is UNKNOWN or a preserved conflict, never a synthesised success.
     */
    private Decision persisted(Status status, LateEvidenceCriticalComposer.Timing timing,
            AuthorizationState expected, ProcessorEvidenceReceipt receipt, String authorizationId,
            EventDedupeKey resultKey) {
        AuthorizationRecord stored;
        CommittedVersionEvidence version;
        ProcessorEvidenceReceipt reread;
        Optional<LateEvidenceReceipt> late;
        StoredEvent retained;
        try {
            Optional<AuthorizationRecord> found = repository.findById(authorizationId);
            if (found.isEmpty()) {
                return unknown(timing, "confirmed record is absent");
            }
            stored = found.get();
            version = repository.readVersionEvidence(authorizationId);
            Optional<ProcessorEvidenceReceipt> read = repository.readEvidenceReceipt(
                    receipt.environmentId(), receipt.attestingPrincipal(), receipt.evidenceId());
            if (read.isEmpty()) {
                return unknown(timing, "committed receipt is absent");
            }
            reread = read.get();
            late = repository.readLateEvidenceReceipt(authorizationId);
            Optional<StoredEvent> event = store.find(resultKey);
            if (event.isEmpty()) {
                return unknown(timing, "confirmed record has no retained result event");
            }
            retained = event.get();
        } catch (RuntimeException unreadable) {
            return unknown(timing, "confirmed record evidence is unreadable");
        }
        if (!reread.equals(receipt)) {
            return contentConflict("receipt changed while the result was being read back");
        }
        if (stored.state() != expected) {
            if (determined(stored.state())) {
                return contentConflict("competing terminal outcome retained");
            }
            return unknown(timing, "confirmed state does not match");
        }
        if (stored.decisionSource() != DecisionSource.ISSUER) {
            return contentConflict("confirmed result lacks issuer provenance");
        }
        if (stored.outcomeDeterminedAt() == null
                || !stored.outcomeDeterminedAt().equals(receipt.evidenceAvailableAt())) {
            return contentConflict("confirmed result is not tied to this evidence time");
        }
        if (!version.agreesWith(LATE_EVIDENCE_STORAGE_VERSION,
                LateEvidenceCriticalComposer.LATE_EVIDENCE_JOURNAL_VERSION)) {
            return unknown(timing, "committed result version does not match");
        }
        if (!receipt.stateAfter().equals(expected.name())
                || receipt.journalVersion()
                        != LateEvidenceCriticalComposer.LATE_EVIDENCE_JOURNAL_VERSION
                || !receipt.eventId().equals(expected.name() + "-" + stored.id())) {
            return contentConflict("retained receipt does not describe the confirmed result");
        }
        if (retained.journalVersion() == null
                || retained.journalVersion()
                        != LateEvidenceCriticalComposer.LATE_EVIDENCE_JOURNAL_VERSION
                || !expected.name().equals(retained.stateAfter())
                || !StoredEvent.ACTIVE.equals(retained.status())) {
            return contentConflict("retained event metadata does not match the confirmed result");
        }
        // The first admission's late metadata is a preserved fact, never rewritten by a replay. A
        // record confirmed by the normal path legitimately has none.
        if (late.isPresent()) {
            LateEvidenceReceipt metadata = late.get();
            if (!metadata.late()
                    || !metadata.evidenceAt()
                            .equals(receipt.evidenceAvailableAt()
                                    .truncatedTo(ChronoUnit.SECONDS))) {
                return contentConflict("retained late metadata does not describe this evidence");
            }
            return new Decision(status, timing, expected, version, receipt, metadata,
                    status == Status.REPLAY
                            ? "identical late evidence already retained under the original"
                                    + " delivery receipt"
                            : null,
                    null);
        }
        return new Decision(status, timing, expected, version, receipt, null,
                "identical evidence was already retained by the normal verified-result"
                        + " admission; no late metadata is added",
                null);
    }

    /**
     * One durable, sanitized quarantine marker for a reused identity carrying different content.
     * The established hash/reference come from the receipt that is preserved, never from the
     * candidate.
     */
    private static SignedEvidenceConflict reusedIdentity(C4VerifiedAttestation verified,
            ProcessorEvidenceReceipt established, String reason) {
        return new SignedEvidenceConflict(
                SignedEvidenceConflict.Type.REUSED_IDENTITY_DIFFERENT_CONTENT,
                verified.environmentId(),
                verified.signingPrincipal(),
                verified.evidenceId(),
                verified.canonicalAttestationSha256(),
                established.canonicalAttestationSha256(),
                established.authorizationId(),
                established.acquirerRef(),
                reason);
    }

    /**
     * One durable, sanitized quarantine marker for a different evidence identity whose signed
     * decision contradicts a determined record. The business reference of the established record is
     * retained; no receipt hash is invented when none was read.
     */
    private static SignedEvidenceConflict contradictoryOutcome(AuthorizationRecord established,
            C4VerifiedAttestation verified, String reason) {
        return new SignedEvidenceConflict(
                SignedEvidenceConflict.Type.CONTRADICTORY_DETERMINED_OUTCOME,
                verified.environmentId(),
                verified.signingPrincipal(),
                verified.evidenceId(),
                verified.canonicalAttestationSha256(),
                null,
                established.id(),
                established.acquirerRef(),
                reason);
    }

    /**
     * True only when the stored receipt carries exactly the authenticated identity, hashes, bound
     * result and committed version. Any divergence means a different evidence origin is retained.
     */
    private static boolean sameEvidence(ProcessorEvidenceReceipt receipt,
            C4VerifiedAttestation verified, C4EvidenceAttestation signed,
            C4P6BindingDecision decision) {
        return receipt.environmentId().equals(verified.environmentId())
                && receipt.attestingPrincipal().equals(verified.signingPrincipal())
                && receipt.evidenceId().equals(verified.evidenceId())
                && receipt.canonicalAttestationSha256()
                        .equals(verified.canonicalAttestationSha256())
                && receipt.canonicalAttestationSha256().equals(signed.canonicalSha256())
                && receipt.instructionSha256().equals(verified.instructionSha256())
                && receipt.responseSha256().equals(verified.responseBodySha256())
                && receipt.acquirerRef().equals(verified.acquirerRef())
                && receipt.authorizationId().equals(verified.authorizationId())
                && receipt.transactionId().equals(verified.transactionId())
                && receipt.keyId().equals(verified.keyId())
                && receipt.attestationType().equals(verified.attestationType().name())
                && receipt.eventId().equals(decision.result() + "-" + verified.authorizationId())
                && receipt.stateAfter().equals(decision.result())
                && receipt.journalVersion()
                        == LateEvidenceCriticalComposer.LATE_EVIDENCE_JOURNAL_VERSION
                && receipt.evidenceAvailableAt().equals(Instant.parse(signed.availableAt()));
    }

    /**
     * Stable result identity: the confirmed state plus the operation reference under the fixed
     * PROCESSOR producer in this environment. This is the identity the composer derives, so no
     * second event identity is invented.
     */
    private EventDedupeKey resultIdentity(AuthorizationState expected, String authorizationId) {
        return new EventDedupeKey(environment, OperationAManifest.PRODUCER_PROCESSOR,
                expected.name() + "-" + authorizationId);
    }

    private static AuthorizationState expectedState(C4P6BindingDecision decision) {
        return switch (decision.result()) {
            case "APPROVED" -> AuthorizationState.APPROVED;
            case "DECLINED" -> AuthorizationState.DECLINED;
            default -> throw new IllegalArgumentException(
                    "receipt requires the signed APPROVED or DECLINED result");
        };
    }

    private static boolean determined(AuthorizationState state) {
        return state == AuthorizationState.APPROVED
                || state == AuthorizationState.DECLINED
                || state == AuthorizationState.NETWORK_REJECTED;
    }

    private static Decision refused(String detail) {
        return new Decision(Status.REFUSED, null, null, null, null, null, detail, null);
    }

    private static Decision contentConflict(String detail) {
        return new Decision(Status.CONTENT_CONFLICT, null, null, null, null, null, detail, null);
    }

    /**
     * Record the one sanitized quarantine marker and preserve the conflict outcome. The status and
     * every established fact are decided before the write: a persistence failure leaves both
     * unchanged and only means the marker is absent.
     */
    private Decision quarantined(String detail, SignedEvidenceConflict conflict) {
        SignedEvidenceConflict persisted = null;
        try {
            conflictRecorder.record(conflict);
            persisted = conflict;
        } catch (RuntimeException persistenceFailure) {
            // Failure to store the quarantine marker can neither delete nor change the outcome.
        }
        return new Decision(Status.CONTENT_CONFLICT, null, null, null, null, null, detail,
                persisted);
    }

    private static Decision unknown(LateEvidenceCriticalComposer.Timing timing, String detail) {
        return new Decision(Status.UNKNOWN, timing, null, null, null, null, detail, null);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value;
    }
}
