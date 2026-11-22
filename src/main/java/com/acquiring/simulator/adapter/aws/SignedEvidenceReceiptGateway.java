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
import com.acquiring.simulator.domain.event.EventDeduplication;
import com.acquiring.simulator.domain.event.EventSubmission;
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
import java.util.Objects;
import java.util.Optional;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.dynamodb.model.TransactionCanceledException;

/**
 * One internal signed-evidence receipt gateway for an on-time verified issuer decision
 * (observation contract O3/P5; channel contract C4/P6; profile P5 step (c)).
 *
 * <p>The gateway itself performs the real C4 DIRECT/RELAY verification and the real signed-P5/P6
 * binding. It accepts no caller-supplied "verified", "bound" or "valid" flag and no arbitrary
 * trusted-evidence DTO: a public caller cannot declare verification. The exact signed attestation,
 * the received response octets and the persisted original instruction octets are all inputs; the
 * verified result and the bound decision are produced inside this call.</p>
 *
 * <p>Ordering is deliberate. The trusted transport context is authorized first, before any storage
 * read. The C4 verifier then authenticates the candidate against the configured key registry and
 * the authenticated sender. Only authenticated evidence may name the operation, so the strongly
 * consistent reads of the persisted original business record and the persisted original
 * {@code 0100} octets happen after verification and before any receipt lookup. A receipt is a
 * durable fact about the first admitted evidence; it is never an oracle a caller can consult with
 * forged or unauthenticated material.</p>
 *
 * <p>Receipt identity is exactly {@code (environmentId, attestingPrincipal, evidenceId)} and the
 * content hash is the signed attestation canonical SHA-256. An exact canonical-hash replay returns
 * the stored receipt together with the persisted business version and confirmed state without
 * composing a second transaction. A different content hash for an existing identity is a
 * {@link Status#CONTENT_CONFLICT}: the first receipt and outcome are preserved and never
 * overwritten.</p>
 *
 * <p>A conflict also writes one durable, sanitized quarantine marker through
 * {@link ConflictRecorder}: the reused identity with changed content, or a different signed
 * evidence identity that contradicts a determined record. The marker carries the identity triple,
 * the candidate canonical hash, an optional established hash and the established business
 * reference, never the proof, signature or a second payload. It is written only after trusted
 * context authorization, real C4 verification and real signed-P5/P6 binding, so unauthorized
 * material cannot poison it. A repeated conflict keeps the first witness and advances no business
 * version, source sequence, critical slot or receipt; a persistence failure can neither delete nor
 * change the established outcome.</p>
 *
 * <p>The first admission uses the existing {@link EvidenceReceiptCriticalComposer} and therefore the
 * same one {@code TransactWriteItems}: business record, explicitly advanced committed version,
 * critical result event, EVENT HEAD, dedupe metadata, optional critical slot and normalized receipt.
 * Capacity is mandatory and explicit; the O8 numbers remain PROPOSED, so no budget is defaulted.
 * The original-wire hash is computed by the binder from the persisted bytes, never from a caller
 * argument.</p>
 *
 * <p>An ambiguous SDK outcome is resolved by one strongly consistent lookup of all three facts:
 * receipt, business version/state and committed event. Only a mutually consistent triple is
 * {@link Status#RECOVERED} (knowledge only). Absence or a failed read is {@link Status#UNKNOWN},
 * never proof that nothing was committed. There is no resend, no legacy {@code updateOutcome}, no
 * legacy event append and no fallback path.</p>
 *
 * <p><strong>Not production-wired.</strong> This class is not referenced by
 * {@code AuthorizationService} or {@code AwsRuntime}. It has no HTTP route, no late-delivery
 * envelope, no service caller, no runtime activation and no downstream call. The quarantine marker
 * is metadata only: it is not a financial resolution, resend, reversal or automatically authorized
 * correction.</p>
 */
public final class SignedEvidenceReceiptGateway {

    /**
     * Trusted transport/context authorization supplied by the composition root.
     *
     * <p>The production binding must reflect the authenticated service transport (TLS/IAM
     * principal mapping), never a request header, body field or other caller-controlled flag. This
     * gateway invokes it before any storage read.</p>
     */
    @FunctionalInterface
    public interface TrustedContextAuthorizer {

        /**
         * @throws RuntimeException when the context is not authorized for this environment
         */
        void requireAuthorized(C4AuthenticatedTransportContext context);
    }

    /**
     * Records one durable, sanitized conflict/quarantine marker. Production binds this to
     * {@link DynamoAuthorizationRepository#recordEvidenceConflict}. A thrown runtime failure means
     * only that the marker could not be persisted; it must never delete or change the established
     * outcome.
     */
    @FunctionalInterface
    public interface ConflictRecorder {

        void record(SignedEvidenceConflict conflict);
    }

    /** Outcome classification of exactly one on-time signed-evidence receipt attempt. */
    public enum Status {
        /** This invocation committed one new receipt, confirmed result and critical event. */
        COMMITTED,
        /** The identical canonical evidence was already retained; nothing was written again. */
        REPLAY,
        /** An ambiguous write was resolved by lookup as already committed; knowledge only. */
        RECOVERED,
        /** The identity exists with different content, or a competing determined outcome is retained. */
        CONTENT_CONFLICT,
        /** Authentication, context authorization or signed binding refused the candidate before writes. */
        REFUSED,
        /** The transaction was definitively cancelled and no identical commit was proven. */
        NOT_COMMITTED,
        /** State could not be established; UNAVAILABLE semantics, never proof of no-send. */
        UNKNOWN
    }

    /**
     * @param status          outcome classification
     * @param confirmedState  {@code APPROVED}/{@code DECLINED} read back from persistence, or null
     * @param evidence        persisted committed-version evidence, or {@code null} when not read
     * @param receipt         stored normalized processor receipt, or {@code null} when not read
     * @param detail          safe non-payload diagnostic; never contains event content or evidence
     * @param quarantine      durable sanitized conflict marker written for this attempt, or
     *                        {@code null} when none applies or persistence failed
     */
    public record Decision(Status status, AuthorizationState confirmedState,
            CommittedVersionEvidence evidence, ProcessorEvidenceReceipt receipt, String detail,
            SignedEvidenceConflict quarantine) {

        public Decision {
            Objects.requireNonNull(status, "status");
        }

        /**
         * True only when the receipt, a confirmed state and the persisted committed-version evidence
         * were all read back. Replay and recovery confirm existence; they grant no downstream action.
         */
        public boolean recorded() {
            return (status == Status.COMMITTED || status == Status.REPLAY
                            || status == Status.RECOVERED)
                    && (confirmedState == AuthorizationState.APPROVED
                            || confirmedState == AuthorizationState.DECLINED)
                    && evidence != null
                    && evidence.committedVersionRecorded()
                    && receipt != null;
        }
    }

    /** Storage CAS base written by the dispatch-intent transition. */
    private static final long PRIOR_INTENT_STORAGE_VERSION = 1L;
    /** Storage CAS value written by the receipt/result transition. */
    private static final long RESULT_STORAGE_VERSION = 2L;

    private final DynamoEventStore store;
    private final DynamoAuthorizationRepository repository;
    private final OriginalInstructionReader originals;
    private final TrustedContextAuthorizer contextAuthorizer;
    private final ConflictRecorder conflictRecorder;
    private final String journalTable;
    private final String eventTable;
    private final CriticalCapacity capacity;
    private final String environment;
    private final String instanceId;

    public SignedEvidenceReceiptGateway(DynamoEventStore store,
            DynamoAuthorizationRepository repository, OriginalInstructionReader originals,
            TrustedContextAuthorizer contextAuthorizer, String journalTable, String eventTable,
            CriticalCapacity capacity, String environment, String instanceId) {
        this(store, repository, originals, contextAuthorizer, journalTable, eventTable, capacity,
                environment, instanceId,
                repository == null ? null : repository::recordEvidenceConflict);
    }

    /**
     * Internal seam: the same admission mechanics with an explicit conflict recorder. A local test
     * can therefore simulate a quarantine-persistence failure without a second storage write.
     */
    public SignedEvidenceReceiptGateway(DynamoEventStore store,
            DynamoAuthorizationRepository repository, OriginalInstructionReader originals,
            TrustedContextAuthorizer contextAuthorizer, String journalTable, String eventTable,
            CriticalCapacity capacity, String environment, String instanceId,
            ConflictRecorder conflictRecorder) {
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
     * Admit one signed on-time issuer decision whose proof is carried with its exact response.
     *
     * @param candidate            shape-checked but explicitly unverified C4 candidate
     * @param signed               exact signed attestation content the candidate carries
     * @param context              authenticated transport context (never a client value)
     * @param registry             configured principal/key registry
     * @param receivedResponse0110 exact received {@code 0110} response octets
     */
    public Decision admit(C4CarrierCandidate candidate, C4EvidenceAttestation signed,
            C4AuthenticatedTransportContext context, C4KeyRegistry registry,
            byte[] receivedResponse0110) {
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
                return unknown("persisted original record is absent");
            }
            record = found.get();
        } catch (RuntimeException unreadable) {
            return unknown("persisted original record is unreadable");
        }

        byte[] persistedOriginal;
        try {
            Optional<byte[]> found = originals.readPersistedOriginal0100(verified.authorizationId());
            if (found.isEmpty()) {
                return unknown("persisted original instruction is absent");
            }
            persistedOriginal = found.get();
        } catch (RuntimeException unreadable) {
            return unknown("persisted original instruction is unreadable");
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

        // 5. Receipt lookup only after authentication, context authorization and binding.
        ProcessorEvidenceReceipt existing;
        try {
            Optional<ProcessorEvidenceReceipt> found = repository.readEvidenceReceipt(
                    verified.environmentId(), verified.signingPrincipal(), verified.evidenceId());
            if (found.isEmpty()) {
                return compose(record, verified, signed, decision, expected);
            }
            existing = found.get();
        } catch (RuntimeException unreadable) {
            return unknown("evidence receipt lookup failed");
        }
        if (!sameEvidence(existing, verified, signed, decision)) {
            return quarantined("evidence identity exists with different content",
                    reusedIdentity(verified, existing,
                            "evidence identity exists with different content"));
        }

        // An exact canonical-hash replay returns the stored receipt, version and confirmed state.
        return persisted(Status.REPLAY, expected, existing, verified.authorizationId());
    }

    /** Compose the one atomic receipt/result transaction for a still-undetermined intent. */
    private Decision compose(AuthorizationRecord record, C4VerifiedAttestation verified,
            C4EvidenceAttestation signed, C4P6BindingDecision decision,
            AuthorizationState expected) {
        if (determined(record.state())) {
            return quarantined("a competing confirmed outcome is retained",
                    contradictoryOutcome(record, verified,
                            "a competing confirmed outcome is retained"));
        }
        if (record.state() == AuthorizationState.NOT_CONFIRMED) {
            return unknown("legacy uncertainty records are not transitioned");
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
            return unknown("committed-version evidence is unreadable");
        }
        if (!version.agreesWith(PRIOR_INTENT_STORAGE_VERSION,
                ResultCriticalComposer.PRIOR_INTENT_JOURNAL_VERSION)) {
            return unknown("record does not carry the durable dispatch-intent version pair");
        }

        EventSubmission submission;
        EvidenceReceiptCriticalComposer composer;
        try {
            Instant availableAt = Instant.parse(signed.availableAt());
            DownstreamReply reply = new DownstreamReply(
                    signed.correlationId(), IssuerDecision.valueOf(decision.result()), availableAt);
            submission = ResultCriticalComposer.submission(record, reply, environment, instanceId);
            composer = EvidenceReceiptCriticalComposer.withSlotAccounting(
                    journalTable, eventTable, record, verified, signed, decision, capacity);
        } catch (IllegalArgumentException inconsistent) {
            // The record was read strongly but the authenticated evidence does not describe it.
            return refused("signed evidence does not describe the persisted original");
        }

        try {
            EventAppendResult result = store.append(submission, composer);
            return switch (result.decision()) {
                case NEW -> persisted(Status.COMMITTED, expected, composer.receipt(),
                        record.id());
                case EXACT_REPLAY -> inspectAfterAppend(
                        submission, verified, signed, decision, expected, record.id());
                case CONTENT_CONFLICT -> contentConflict(
                        "result identity exists with different content");
            };
        } catch (TransactionCanceledException cancelled) {
            Decision retained = inspectAfterAppend(
                    submission, verified, signed, decision, expected, record.id());
            if (retained.status() == Status.REPLAY) {
                return retained;
            }
            if (retained.status() == Status.CONTENT_CONFLICT) {
                return retained;
            }
            return new Decision(Status.NOT_COMMITTED, null, null, null,
                    "transaction cancelled; this attempt committed nothing", null);
        } catch (SdkException ambiguous) {
            Decision retained = inspectAfterAppend(
                    submission, verified, signed, decision, expected, record.id());
            if (retained.status() == Status.REPLAY) {
                return new Decision(Status.RECOVERED, retained.confirmedState(),
                        retained.evidence(), retained.receipt(), null, null);
            }
            if (retained.status() == Status.CONTENT_CONFLICT) {
                return retained;
            }
            return unknown("ambiguous commit; receipt, business and event are not consistently readable");
        }
    }

    /**
     * One strongly consistent lookup of the receipt, committed event and business version. This is
     * used for an ambiguous SDK outcome and for an event-store replay classification; it never
     * appends, retries or resends.
     */
    private Decision inspectAfterAppend(EventSubmission submission, C4VerifiedAttestation verified,
            C4EvidenceAttestation signed, C4P6BindingDecision decision,
            AuthorizationState expected, String authorizationId) {
        ProcessorEvidenceReceipt receipt;
        try {
            Optional<ProcessorEvidenceReceipt> found = repository.readEvidenceReceipt(
                    verified.environmentId(), verified.signingPrincipal(), verified.evidenceId());
            if (found.isEmpty()) {
                return unknown("no committed receipt was found");
            }
            receipt = found.get();
        } catch (RuntimeException unreadable) {
            return unknown("receipt lookup failed");
        }
        if (!sameEvidence(receipt, verified, signed, decision)) {
            return quarantined("retained receipt has different content",
                    reusedIdentity(verified, receipt,
                            "retained receipt has different content"));
        }

        StoredEvent stored;
        try {
            Optional<StoredEvent> found = store.find(submission.dedupeKey());
            if (found.isEmpty()) {
                return unknown("no committed event was found");
            }
            stored = found.get();
        } catch (RuntimeException unreadable) {
            return unknown("committed event lookup failed");
        }
        String incomingSha = CanonicalEventCodec.sha256(submission.event());
        if (EventDeduplication.classify(stored.eventSha256(), incomingSha)
                != EventDeduplication.Decision.EXACT_REPLAY) {
            return contentConflict("retained event has different content");
        }
        if (stored.journalVersion() == null
                || stored.journalVersion() != ResultCriticalComposer.RESULT_JOURNAL_VERSION
                || !expected.name().equals(stored.stateAfter())
                || !StoredEvent.ACTIVE.equals(stored.status())) {
            return contentConflict("retained event metadata does not match the confirmed result");
        }
        return persisted(Status.REPLAY, expected, receipt, authorizationId);
    }

    /**
     * Read the confirmed record, its committed version and the stored receipt back. Missing,
     * unreadable, non-matching or non-issuer provenance is UNKNOWN or a preserved conflict, never a
     * synthesised success.
     */
    private Decision persisted(Status status, AuthorizationState expected,
            ProcessorEvidenceReceipt receipt, String authorizationId) {
        AuthorizationRecord stored;
        CommittedVersionEvidence version;
        ProcessorEvidenceReceipt reread;
        try {
            Optional<AuthorizationRecord> found = repository.findById(authorizationId);
            if (found.isEmpty()) {
                return unknown("confirmed record is absent");
            }
            stored = found.get();
            version = repository.readVersionEvidence(authorizationId);
            Optional<ProcessorEvidenceReceipt> read = repository.readEvidenceReceipt(
                    receipt.environmentId(), receipt.attestingPrincipal(), receipt.evidenceId());
            if (read.isEmpty()) {
                return unknown("committed receipt is absent");
            }
            reread = read.get();
        } catch (RuntimeException unreadable) {
            return unknown("confirmed record evidence is unreadable");
        }
        if (!reread.equals(receipt)) {
            return contentConflict("receipt changed while the result was being read back");
        }
        if (stored.state() != expected) {
            if (determined(stored.state())) {
                return contentConflict("competing terminal outcome retained");
            }
            return unknown("confirmed state does not match");
        }
        if (stored.decisionSource() != DecisionSource.ISSUER) {
            return contentConflict("confirmed result lacks issuer provenance");
        }
        if (!version.agreesWith(RESULT_STORAGE_VERSION,
                ResultCriticalComposer.RESULT_JOURNAL_VERSION)) {
            return unknown("committed result version does not match");
        }
        if (!receipt.stateAfter().equals(expected.name())
                || receipt.journalVersion() != ResultCriticalComposer.RESULT_JOURNAL_VERSION
                || !receipt.eventId().equals(expected.name() + "-" + stored.id())) {
            return contentConflict("retained receipt does not describe the confirmed result");
        }
        return new Decision(status, expected, version, receipt, null, null);
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
                && receipt.journalVersion() == ResultCriticalComposer.RESULT_JOURNAL_VERSION
                && receipt.evidenceAvailableAt().equals(Instant.parse(signed.availableAt()));
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
        return new Decision(Status.REFUSED, null, null, null, detail, null);
    }

    /**
     * Preserve a conflict that already has a durable marker elsewhere (the event-store integrity
     * marker) or that must not rewrite evidence metadata.
     */
    private static Decision contentConflict(String detail) {
        return new Decision(Status.CONTENT_CONFLICT, null, null, null, detail, null);
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
        return new Decision(Status.CONTENT_CONFLICT, null, null, null, detail, persisted);
    }

    private static Decision unknown(String detail) {
        return new Decision(Status.UNKNOWN, null, null, null, detail, null);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value;
    }
}
