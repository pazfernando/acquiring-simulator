package com.acquiring.simulator.adapter.aws;

import com.acquiring.simulator.domain.event.CanonicalEventCodec;
import com.acquiring.simulator.domain.event.CommittedVersionEvidence;
import com.acquiring.simulator.domain.event.CriticalCapacity;
import com.acquiring.simulator.domain.event.EventAppendResult;
import com.acquiring.simulator.domain.event.EventDeduplication;
import com.acquiring.simulator.domain.event.EventSubmission;
import com.acquiring.simulator.domain.event.StoredEvent;
import com.acquiring.simulator.domain.model.AuthorizationRecord;
import com.acquiring.simulator.domain.model.AuthorizationState;
import com.acquiring.simulator.domain.model.DecisionSource;
import com.acquiring.simulator.domain.model.DownstreamReply;
import com.acquiring.simulator.domain.model.IssuerDecision;
import com.acquiring.simulator.domain.port.ResultGateway;
import java.util.Objects;
import java.util.Optional;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.dynamodb.model.TransactionCanceledException;

/**
 * DynamoDB adapter for {@link ResultGateway}: exactly one composed verified-result transaction
 * through {@link ResultCriticalComposer}.
 *
 * <p>A success is reported only after the stored record is read back and its committed version,
 * confirmed state and issuer provenance are verified. The committed version in
 * {@link Decision#evidence()} is therefore a persisted fact, never derived from the internal CAS
 * counter or defaulted. If the stored record cannot be read or does not match, the outcome is
 * {@link Status#UNKNOWN}, not a synthesised success.
 *
 * <p>An ambiguous transport outcome is resolved by one strongly consistent lookup of the stable
 * dedupe identity ({@code APPROVED-<authorizationId>} / {@code DECLINED-<authorizationId>}): exact
 * committed metadata is recovered knowledge; different content is a conflict; an empty or failed
 * lookup stays UNKNOWN and is never converted to "no result".
 *
 * <p>A competing determined outcome is retained: the composer's conditioned transition cancels the
 * whole transaction, so this adapter never overwrites it, never appends a second result event and
 * never reserves a second slot. It also never calls {@code updateOutcome},
 * {@code markDispatched}, a legacy event append, a resend or a reversal.
 *
 * <p>Capacity is mandatory and explicit: the O8 numbers remain PROPOSED, so no budget is defaulted
 * here. <strong>Not activated in {@code AwsRuntime}.</strong> Service result wiring, late-evidence
 * variants and ambiguous-result disposition remain later slices.
 */
public final class DynamoResultGateway implements ResultGateway {

    /** Storage CAS value written by the result transition; the intent write left 1. */
    private static final long EXPECTED_STORAGE_VERSION = 2L;

    private final DynamoEventStore store;
    private final DynamoAuthorizationRepository repository;
    private final String journalTable;
    private final String eventTable;
    private final CriticalCapacity capacity;
    private final String environment;
    private final String instanceId;

    public DynamoResultGateway(DynamoEventStore store, DynamoAuthorizationRepository repository,
            String journalTable, String eventTable, CriticalCapacity capacity, String environment,
            String instanceId) {
        this.store = Objects.requireNonNull(store, "store");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.journalTable = requireText(journalTable, "journalTable");
        this.eventTable = requireText(eventTable, "eventTable");
        this.capacity = Objects.requireNonNull(capacity, "capacity");
        this.environment = requireText(environment, "environment");
        this.instanceId = requireText(instanceId, "instanceId");
    }

    @Override
    public Decision recordResult(AuthorizationRecord intent, DownstreamReply evidence) {
        Objects.requireNonNull(intent, "intent");
        Objects.requireNonNull(evidence, "evidence");

        // Refuses non-confirmed, uncorrelated or pre-dispatch evidence before any write.
        EventSubmission submission = ResultCriticalComposer.submission(intent, evidence, environment,
                instanceId);
        AuthorizationState expected = expectedState(evidence);
        ResultCriticalComposer composer = ResultCriticalComposer.withSlotAccounting(journalTable,
                eventTable, intent, evidence, capacity);

        try {
            EventAppendResult result = store.append(submission, composer);
            return switch (result.decision()) {
                case NEW -> persisted(Status.COMMITTED, expected, intent);
                case EXACT_REPLAY -> persisted(Status.REPLAY, expected, intent);
                case CONTENT_CONFLICT -> new Decision(Status.CONTENT_CONFLICT, null, null,
                        "result identity exists with different content");
            };
        } catch (TransactionCanceledException aborted) {
            // The conditioned transition failed: no result, no event, no HEAD advance, no slot.
            // A competing determined outcome is left exactly as stored.
            return new Decision(Status.NOT_COMMITTED, null, null, "transaction cancelled");
        } catch (SdkException ambiguous) {
            return resolveAmbiguous(submission, expected, intent);
        }
    }

    /**
     * Read the committed result back and require the matching state, issuer provenance and
     * committed version. Any missing, unreadable or non-matching evidence is UNKNOWN or a retained
     * conflict, never a success.
     */
    private Decision persisted(Status status, AuthorizationState expected, AuthorizationRecord intent) {
        AuthorizationRecord stored;
        CommittedVersionEvidence evidence;
        try {
            Optional<AuthorizationRecord> found = repository.findById(intent.id());
            if (found.isEmpty()) {
                return new Decision(Status.UNKNOWN, null, null,
                        "committed result record absent");
            }
            stored = found.get();
            evidence = repository.readVersionEvidence(intent.id());
        } catch (RuntimeException unreadable) {
            return new Decision(Status.UNKNOWN, null, null,
                    "committed result evidence unreadable");
        }
        if (stored.state() != expected) {
            if (determined(stored.state())) {
                return new Decision(Status.CONTENT_CONFLICT, null, null,
                        "competing terminal outcome retained");
            }
            return new Decision(Status.UNKNOWN, null, null,
                    "committed result state does not match");
        }
        if (stored.decisionSource() != DecisionSource.ISSUER) {
            return new Decision(Status.CONTENT_CONFLICT, null, null,
                    "committed result lacks issuer provenance");
        }
        if (intent.dispatchCorrelationId() == null
                || !intent.dispatchCorrelationId().equals(stored.dispatchCorrelationId())) {
            return new Decision(Status.CONTENT_CONFLICT, null, null,
                    "committed result dispatch correlation differs");
        }
        if (!evidence.agreesWith(EXPECTED_STORAGE_VERSION,
                ResultCriticalComposer.RESULT_JOURNAL_VERSION)) {
            return new Decision(Status.UNKNOWN, null, null,
                    "committed result version does not match");
        }
        return new Decision(status, expected, evidence, null);
    }

    /**
     * One strongly consistent lookup of the stable transition identity. Exact committed metadata is
     * recovered knowledge (still verified against the stored record); different content is a
     * conflict; an empty or failed lookup stays UNKNOWN and never means "no result".
     */
    private Decision resolveAmbiguous(EventSubmission submission, AuthorizationState expected,
            AuthorizationRecord intent) {
        String incomingSha = CanonicalEventCodec.sha256(submission.event());
        Optional<StoredEvent> stored;
        try {
            stored = store.find(submission.dedupeKey());
        } catch (SdkException lookupFailure) {
            return new Decision(Status.UNKNOWN, null, null, "ambiguous result; lookup failed");
        }
        if (stored.isEmpty()) {
            return new Decision(Status.UNKNOWN, null, null,
                    "ambiguous result; no committed result found");
        }
        StoredEvent event = stored.get();
        EventDeduplication.Decision decision =
                EventDeduplication.classify(event.eventSha256(), incomingSha);
        if (decision != EventDeduplication.Decision.EXACT_REPLAY) {
            return new Decision(Status.CONTENT_CONFLICT, null, null,
                    "stored result has different content");
        }
        Long journalVersion = event.journalVersion();
        if (journalVersion == null
                || journalVersion != ResultCriticalComposer.RESULT_JOURNAL_VERSION
                || !expected.name().equals(event.stateAfter())) {
            return new Decision(Status.CONTENT_CONFLICT, null, null,
                    "stored result metadata does not match the confirmed outcome");
        }
        return persisted(Status.RECOVERED, expected, intent);
    }

    /** The only confirmed states this transition can commit. */
    private static AuthorizationState expectedState(DownstreamReply evidence) {
        if (evidence.decision() == IssuerDecision.APPROVED) {
            return AuthorizationState.APPROVED;
        }
        if (evidence.decision() == IssuerDecision.DECLINED) {
            return AuthorizationState.DECLINED;
        }
        throw new IllegalArgumentException("result evidence must be APPROVED or DECLINED");
    }

    private static boolean determined(AuthorizationState state) {
        return state == AuthorizationState.APPROVED || state == AuthorizationState.DECLINED;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value;
    }
}
