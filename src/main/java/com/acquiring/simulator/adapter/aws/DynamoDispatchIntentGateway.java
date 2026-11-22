package com.acquiring.simulator.adapter.aws;

import com.acquiring.simulator.domain.event.CanonicalEventCodec;
import com.acquiring.simulator.domain.event.CommittedVersionEvidence;
import com.acquiring.simulator.domain.event.CriticalCapacity;
import com.acquiring.simulator.domain.event.EventAppendResult;
import com.acquiring.simulator.domain.event.EventDeduplication;
import com.acquiring.simulator.domain.event.EventSubmission;
import com.acquiring.simulator.domain.event.StoredEvent;
import com.acquiring.simulator.domain.model.AuthorizationRecord;
import com.acquiring.simulator.domain.port.DispatchIntentGateway;
import com.acquiring.simulator.domain.port.OutboundInstructionMaterializer;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.dynamodb.model.TransactionCanceledException;

/**
 * DynamoDB adapter for {@link DispatchIntentGateway}: exactly one composed dispatch-intent
 * transaction through {@link PossibleSendCriticalComposer}.
 *
 * <p>A {@code COMMITTED} result reads both version facts back from the journal row and reports them
 * as persisted evidence; the service must not send otherwise. {@code REPLAY} and {@code RECOVERED}
 * only prove the intent already existed — they are knowledge, not a resend permission. {@code
 * UNKNOWN} is returned when an ambiguous transport outcome cannot be resolved by one strongly
 * consistent lookup; it is never converted to "not sent".
 *
 * <p>The adapter never calls {@link DynamoAuthorizationRepository#markDispatched} and never appends
 * a legacy event, so a failed intent cannot fall back to the old dispatch path. Capacity is
 * mandatory and explicit: the O8 numbers remain PROPOSED, so no budget is defaulted here.
 *
 * <p><strong>Not activated in {@code AwsRuntime}.</strong> Result coupling (confirmed outcome and
 * terminal-event atomicity) is a later slice, so the runtime stays on the legacy dispatch path.
 */
public final class DynamoDispatchIntentGateway implements DispatchIntentGateway {

    private final DynamoEventStore store;
    private final DynamoAuthorizationRepository repository;
    private final String journalTable;
    private final String eventTable;
    private final CriticalCapacity capacity;
    private final String environment;
    private final String instanceId;
    private final OutboundInstructionMaterializer materializer;

    public DynamoDispatchIntentGateway(DynamoEventStore store,
            DynamoAuthorizationRepository repository, String journalTable, String eventTable,
            CriticalCapacity capacity, String environment, String instanceId) {
        this(store, repository, journalTable, eventTable, capacity, environment, instanceId, null);
    }

    /**
     * Adapter variant that materializes the exact outbound instruction and persists it atomically
     * with the dispatch intent. A materialization failure commits nothing and permits no send.
     */
    public DynamoDispatchIntentGateway(DynamoEventStore store,
            DynamoAuthorizationRepository repository, String journalTable, String eventTable,
            CriticalCapacity capacity, String environment, String instanceId,
            OutboundInstructionMaterializer materializer) {
        this.store = Objects.requireNonNull(store, "store");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.journalTable = requireText(journalTable, "journalTable");
        this.eventTable = requireText(eventTable, "eventTable");
        this.capacity = Objects.requireNonNull(capacity, "capacity");
        this.environment = requireText(environment, "environment");
        this.instanceId = requireText(instanceId, "instanceId");
        this.materializer = materializer;
    }

    @Override
    public Decision dispatchIntent(AuthorizationRecord claim, String dispatchCorrelationId,
            Instant now) {
        Objects.requireNonNull(claim, "claim");
        String correlation = requireText(dispatchCorrelationId, "dispatchCorrelationId");
        Objects.requireNonNull(now, "now");

        byte[] originalInstruction = null;
        if (materializer != null) {
            try {
                originalInstruction = materializer.materialize(claim, correlation, now);
            } catch (RuntimeException materializationFailure) {
                // No original instruction means no send: commit nothing and permit nothing.
                return new Decision(Status.NOT_COMMITTED, null,
                        "outbound instruction could not be materialized");
            }
            if (originalInstruction == null || originalInstruction.length == 0) {
                return new Decision(Status.NOT_COMMITTED, null,
                        "outbound instruction is empty");
            }
        }

        EventSubmission submission = PossibleSendCriticalComposer.submission(claim, correlation,
                environment, instanceId, now);
        PossibleSendCriticalComposer composer = PossibleSendCriticalComposer.withSlotAccounting(
                journalTable, eventTable, claim, correlation, now, capacity, originalInstruction);

        try {
            EventAppendResult result = store.append(submission, composer);
            return switch (result.decision()) {
                case NEW -> committed(claim.id());
                case EXACT_REPLAY -> alreadyCommitted(Status.REPLAY, claim.id());
                case CONTENT_CONFLICT -> new Decision(Status.CONTENT_CONFLICT, null,
                        "dispatch-intent identity exists with different content");
            };
        } catch (TransactionCanceledException aborted) {
            // The conditioned business transition failed: no intent, no event, no slot. This is a
            // definite rejection of this attempt, not an ambiguous send.
            return new Decision(Status.NOT_COMMITTED, null, "transaction cancelled");
        } catch (SdkException ambiguous) {
            return resolveAmbiguous(submission);
        }
    }

    /** Read the exact transition back; without recorded evidence the send is not authorized. */
    private Decision committed(String authorizationId) {
        CommittedVersionEvidence evidence;
        try {
            evidence = repository.readVersionEvidence(authorizationId);
        } catch (RuntimeException unreadable) {
            return new Decision(Status.UNKNOWN, null,
                    "committed intent evidence unreadable");
        }
        if (!evidence.committedVersionRecorded()) {
            return new Decision(Status.UNKNOWN, null,
                    "committed intent evidence absent");
        }
        return new Decision(Status.COMMITTED, evidence, null);
    }

    /** Replay/recovery confirms existence; evidence is reported when readable, never invented. */
    private Decision alreadyCommitted(Status status, String authorizationId) {
        CommittedVersionEvidence evidence;
        try {
            evidence = repository.readVersionEvidence(authorizationId);
        } catch (RuntimeException unreadable) {
            evidence = null;
        }
        return new Decision(status, evidence, null);
    }

    /**
     * One strongly consistent lookup of the stable transition identity. A found exact intent is
     * {@link Status#RECOVERED} (no resend); a different payload is a conflict; an empty or failed
     * lookup stays {@link Status#UNKNOWN}.
     */
    private Decision resolveAmbiguous(EventSubmission submission) {
        String incomingSha = CanonicalEventCodec.sha256(submission.event());
        Optional<StoredEvent> stored;
        try {
            stored = store.find(submission.dedupeKey());
        } catch (SdkException lookupFailure) {
            return new Decision(Status.UNKNOWN, null, "ambiguous intent; lookup failed");
        }
        if (stored.isEmpty()) {
            return new Decision(Status.UNKNOWN, null,
                    "ambiguous intent; no committed intent found");
        }
        StoredEvent event = stored.get();
        EventDeduplication.Decision decision =
                EventDeduplication.classify(event.eventSha256(), incomingSha);
        if (decision == EventDeduplication.Decision.EXACT_REPLAY) {
            return alreadyCommitted(Status.RECOVERED, submission.event().operationId());
        }
        return new Decision(Status.CONTENT_CONFLICT, null,
                "stored intent has different content");
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value;
    }
}
