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
import com.acquiring.simulator.domain.port.NetworkResultGateway;
import java.util.Objects;
import java.util.Optional;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.dynamodb.model.TransactionCanceledException;

/**
 * DynamoDB adapter for {@link NetworkResultGateway}: exactly one composed explicit-network-rejection
 * transaction through {@link NetworkRejectedCriticalComposer}.
 *
 * <p>The admitted evidence is only an authenticated, correlated {@code NETWORK_REJECTED} reply after
 * a durable dispatch intent; the composer refuses anything else before this adapter writes. A
 * success is reported only after the stored record is read back with {@code NETWORK_REJECTED},
 * {@link DecisionSource#NETWORK} provenance, the preserved dispatch correlation and committed
 * version 3 over CAS 2. Without that evidence the result is {@code UNKNOWN}, never a synthesised
 * success.
 *
 * <p>An ambiguous transport outcome is resolved by one strongly consistent lookup of the stable
 * dedupe identity {@code NETWORK_REJECTED-<authorizationId>}: exact committed metadata is recovered
 * knowledge; different content is a conflict; an empty or failed lookup stays {@code UNKNOWN} and is
 * never converted to "no rejection". A cancelled transaction is {@code NOT_COMMITTED}: a competing
 * issuer-confirmed outcome has already advanced the conditioned pair and is retained untouched.
 *
 * <p>This adapter never resends, never reverses, never calls {@code updateOutcome},
 * {@code markDispatched}, a legacy event append or a legacy rejection append. Capacity is mandatory
 * and explicit: the O8 numbers remain PROPOSED, so no budget is defaulted here.
 * <strong>Not activated in {@code AwsRuntime}</strong>; {@code AuthorizationService} now consumes the
 * port, while runtime activation remains a later slice.
 */
public final class DynamoNetworkResultGateway implements NetworkResultGateway {

    /** Storage CAS value written by the network-rejection transition; the intent write left 1. */
    private static final long EXPECTED_STORAGE_VERSION = 2L;

    private final DynamoEventStore store;
    private final DynamoAuthorizationRepository repository;
    private final String journalTable;
    private final String eventTable;
    private final CriticalCapacity capacity;
    private final String environment;
    private final String instanceId;

    public DynamoNetworkResultGateway(DynamoEventStore store,
            DynamoAuthorizationRepository repository, String journalTable, String eventTable,
            CriticalCapacity capacity, String environment, String instanceId) {
        this.store = Objects.requireNonNull(store, "store");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.journalTable = requireText(journalTable, "journalTable");
        this.eventTable = requireText(eventTable, "eventTable");
        this.capacity = Objects.requireNonNull(capacity, "capacity");
        this.environment = requireText(environment, "environment");
        this.instanceId = requireText(instanceId, "instanceId");
    }

    @Override
    public Decision recordNetworkRejection(AuthorizationRecord intent, DownstreamReply evidence) {
        Objects.requireNonNull(intent, "intent");
        Objects.requireNonNull(evidence, "evidence");

        // Refuses non-network, uncorrelated, pre-dispatch or already-determined evidence before
        // any write. A MALFORMED/NO_RESPONSE/APPROVED/DECLINED reply never reaches the store.
        EventSubmission submission = NetworkRejectedCriticalComposer.submission(intent, evidence,
                environment, instanceId);
        NetworkRejectedCriticalComposer composer = NetworkRejectedCriticalComposer
                .withSlotAccounting(journalTable, eventTable, intent, evidence, capacity);

        try {
            EventAppendResult result = store.append(submission, composer);
            return switch (result.decision()) {
                case NEW -> persisted(Status.COMMITTED, intent);
                case EXACT_REPLAY -> persisted(Status.REPLAY, intent);
                case CONTENT_CONFLICT -> new Decision(Status.CONTENT_CONFLICT, null, null,
                        "network rejection identity exists with different content");
            };
        } catch (TransactionCanceledException aborted) {
            // Conditioned transition failed: no rejection, no event, no HEAD advance, no slot.
            // A competing issuer-confirmed outcome is left exactly as stored.
            return new Decision(Status.NOT_COMMITTED, null, null, "transaction cancelled");
        } catch (SdkException ambiguous) {
            return resolveAmbiguous(submission, intent);
        }
    }

    /**
     * Read the committed rejection back and require the network state and provenance, the preserved
     * dispatch correlation and the committed version. Missing, unreadable or non-matching evidence
     * is UNKNOWN or a retained conflict, never a success.
     */
    private Decision persisted(Status status, AuthorizationRecord intent) {
        AuthorizationRecord stored;
        CommittedVersionEvidence evidence;
        try {
            Optional<AuthorizationRecord> found = repository.findById(intent.id());
            if (found.isEmpty()) {
                return new Decision(Status.UNKNOWN, null, null,
                        "committed network rejection record absent");
            }
            stored = found.get();
            evidence = repository.readVersionEvidence(intent.id());
        } catch (RuntimeException unreadable) {
            return new Decision(Status.UNKNOWN, null, null,
                    "committed network rejection evidence unreadable");
        }
        if (stored.state() != AuthorizationState.NETWORK_REJECTED) {
            if (issuerDetermined(stored.state())) {
                return new Decision(Status.CONTENT_CONFLICT, null, null,
                        "competing issuer outcome retained");
            }
            return new Decision(Status.UNKNOWN, null, null,
                    "committed rejection state does not match");
        }
        if (stored.decisionSource() != DecisionSource.NETWORK) {
            return new Decision(Status.CONTENT_CONFLICT, null, null,
                    "committed rejection lacks network provenance");
        }
        if (intent.dispatchCorrelationId() == null
                || !intent.dispatchCorrelationId().equals(stored.dispatchCorrelationId())) {
            return new Decision(Status.CONTENT_CONFLICT, null, null,
                    "committed rejection dispatch correlation differs");
        }
        if (!evidence.agreesWith(EXPECTED_STORAGE_VERSION,
                NetworkRejectedCriticalComposer.NETWORK_REJECTED_JOURNAL_VERSION)) {
            return new Decision(Status.UNKNOWN, null, null,
                    "committed rejection version does not match");
        }
        return new Decision(status, AuthorizationState.NETWORK_REJECTED, evidence, null);
    }

    /**
     * One strongly consistent lookup of the stable transition identity. Exact committed metadata is
     * recovered knowledge (still verified against the stored record); different content is a
     * conflict; an empty or failed lookup stays UNKNOWN and never means "no rejection".
     */
    private Decision resolveAmbiguous(EventSubmission submission, AuthorizationRecord intent) {
        String incomingSha = CanonicalEventCodec.sha256(submission.event());
        Optional<StoredEvent> stored;
        try {
            stored = store.find(submission.dedupeKey());
        } catch (SdkException lookupFailure) {
            return new Decision(Status.UNKNOWN, null, null,
                    "ambiguous rejection; lookup failed");
        }
        if (stored.isEmpty()) {
            return new Decision(Status.UNKNOWN, null, null,
                    "ambiguous rejection; no committed rejection found");
        }
        StoredEvent event = stored.get();
        EventDeduplication.Decision decision =
                EventDeduplication.classify(event.eventSha256(), incomingSha);
        if (decision != EventDeduplication.Decision.EXACT_REPLAY) {
            return new Decision(Status.CONTENT_CONFLICT, null, null,
                    "stored rejection has different content");
        }
        Long journalVersion = event.journalVersion();
        if (journalVersion == null
                || journalVersion != NetworkRejectedCriticalComposer.NETWORK_REJECTED_JOURNAL_VERSION
                || !NetworkRejectedCriticalComposer.NETWORK_REJECTED_STATE_AFTER
                        .equals(event.stateAfter())) {
            return new Decision(Status.CONTENT_CONFLICT, null, null,
                    "stored rejection metadata does not match the network transition");
        }
        return persisted(Status.RECOVERED, intent);
    }

    /** Only an issuer decision is a competing determined outcome for this transition. */
    private static boolean issuerDetermined(AuthorizationState state) {
        return state == AuthorizationState.APPROVED || state == AuthorizationState.DECLINED;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value;
    }
}
