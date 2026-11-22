package com.acquiring.simulator.adapter.aws;

import com.acquiring.simulator.domain.event.CanonicalEventCodec;
import com.acquiring.simulator.domain.event.CriticalCapacity;
import com.acquiring.simulator.domain.event.EventAppendResult;
import com.acquiring.simulator.domain.event.EventDeduplication;
import com.acquiring.simulator.domain.event.EventSubmission;
import com.acquiring.simulator.domain.event.StoredEvent;
import com.acquiring.simulator.domain.model.AuthorizationRecord;
import java.util.Objects;
import java.util.Optional;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.dynamodb.model.TransactionCanceledException;

/**
 * One-shot atomic INITIAL CLAIM commit with ambiguous-commit recovery (observation contract O3).
 *
 * <p>This adapter is the seam a service would call <em>instead of</em> {@code repository.claim()}
 * followed by an event append: the business record, business head, critical claim event, EVENT head,
 * dedupe metadata and (only when an explicit capacity is supplied) the slot reservation travel in
 * exactly ONE {@code TransactWriteItems} call through {@link DynamoEventStore}. This class never
 * calls {@code claim()} itself, so a caller cannot claim first and append later.
 *
 * <p>It deliberately does NOT dispatch payment, retry a sent request or mutate the initial record's
 * persisted {@code version}. On an ambiguous transport outcome the only follow-up is a strongly
 * consistent lookup of the stable dedupe identity (environment-bound producerId/eventId, which the
 * approved contract requires to be allocated from the original transition token). If the lookup
 * cannot establish state, the outcome is {@link Outcome#UNKNOWN} (channel UNAVAILABLE semantics),
 * never "no send" and never a resend.
 *
 * <p><strong>Not production-wired.</strong> {@code AuthorizationService} and {@code AwsRuntime} do
 * not reference this class. The public {@code journalVersion}/internal {@code version} relation is
 * implemented as an additive persistence mapping (claim stores committed version 1 beside CAS 0 and
 * recovery reads the stored committed value); it is not a spec amendment and this slice still only
 * proves the transaction and recovery mechanics. Capacity is mandatory and explicit: there is no
 * default budget.
 */
public final class AtomicClaimGateway {

    /** Result class of one claim-commit attempt, including the ambiguous-resolution outcome. */
    public enum Outcome {
        /** The one transaction committed a new claim event. */
        COMMITTED,
        /** The write outcome was ambiguous, but a consistent lookup proved the commit happened. */
        RECOVERED,
        /** The same claim event was already stored; no second business write or slot reservation. */
        REPLAY,
        /** The stable identity exists with different immutable content; never overwritten. */
        CONTENT_CONFLICT,
        /** DynamoDB rejected the transaction; nothing was committed and nothing is sent. */
        NOT_COMMITTED,
        /** State could not be established; treat as UNAVAILABLE, never as proof of no-send. */
        UNKNOWN
    }

    /**
     * @param outcome        classification of this attempt
     * @param appendResult   store result when known ({@code null} for NOT_COMMITTED/UNKNOWN)
     * @param recoveredEvent the strongly consistent dedupe row used to resolve an ambiguous write
     * @param detail         safe, non-payload diagnostic; never contains event content
     */
    public record ClaimCommit(Outcome outcome, EventAppendResult appendResult,
            StoredEvent recoveredEvent, String detail) {

        public ClaimCommit {
            Objects.requireNonNull(outcome, "outcome");
        }

        /** True only when this attempt established a durably committed claim. */
        public boolean committed() {
            return outcome == Outcome.COMMITTED || outcome == Outcome.RECOVERED;
        }
    }

    private final DynamoEventStore store;
    private final ClaimCriticalComposer composer;

    private AtomicClaimGateway(DynamoEventStore store, ClaimCriticalComposer composer) {
        this.store = Objects.requireNonNull(store, "store");
        this.composer = Objects.requireNonNull(composer, "composer");
    }

    /**
     * Claim gateway for one initial claim with an explicit critical-slot budget. Capacity is never
     * defaulted: the O8 numbers remain PROPOSED, so a caller must supply the approved policy.
     */
    public static AtomicClaimGateway forClaim(DynamoEventStore store, String journalTable,
            String eventTable, AuthorizationRecord claim, CriticalCapacity capacity) {
        Objects.requireNonNull(capacity, "capacity");
        return new AtomicClaimGateway(store,
                ClaimCriticalComposer.withSlotAccounting(journalTable, eventTable, claim, capacity));
    }

    /**
     * Perform exactly one composed claim transaction. No retry of the write, no payment dispatch and
     * no record-version migration happen here.
     */
    public ClaimCommit commit(EventSubmission submission) {
        Objects.requireNonNull(submission, "submission");
        try {
            return classify(store.append(submission, composer));
        } catch (TransactionCanceledException aborted) {
            // DynamoDB cancelled the transaction: no business row, event, HEAD, dedupe or slot is
            // committed. This is NOT ambiguous and must not be retried as a fresh claim.
            return new ClaimCommit(Outcome.NOT_COMMITTED, null, null, "transaction cancelled");
        } catch (SdkException ambiguous) {
            // Timeout/transport failure after the request may have committed. Resolve by lookup only.
            return resolveAmbiguous(submission);
        }
    }

    private static ClaimCommit classify(EventAppendResult result) {
        return switch (result.decision()) {
            case NEW -> new ClaimCommit(Outcome.COMMITTED, result, null, null);
            case EXACT_REPLAY -> new ClaimCommit(Outcome.REPLAY, result, null, null);
            case CONTENT_CONFLICT -> new ClaimCommit(Outcome.CONTENT_CONFLICT, result, null, null);
        };
    }

    /**
     * Strongly consistent lookup of the stable transition identity. The identity is the dedupe key
     * (environment, producerId, eventId), which survives restarts and is not caller-selected.
     */
    private ClaimCommit resolveAmbiguous(EventSubmission submission) {
        String incomingSha = CanonicalEventCodec.sha256(submission.event());
        Optional<StoredEvent> stored;
        try {
            stored = store.find(submission.dedupeKey());
        } catch (SdkException lookupFailure) {
            return new ClaimCommit(Outcome.UNKNOWN, null, null, "ambiguous commit; lookup failed");
        }
        if (stored.isEmpty()) {
            return new ClaimCommit(Outcome.UNKNOWN, null, null,
                    "ambiguous commit; no committed event found");
        }
        StoredEvent event = stored.get();
        EventDeduplication.Decision decision =
                EventDeduplication.classify(event.eventSha256(), incomingSha);
        if (decision == EventDeduplication.Decision.EXACT_REPLAY) {
            return new ClaimCommit(Outcome.RECOVERED,
                    EventAppendResult.replay(event.sourceSequence(), incomingSha,
                            event.eventSha256()),
                    event, null);
        }
        // A stored identity with different content (or no hash) is an integrity conflict; the
        // ambiguous write is never replayed over it.
        return new ClaimCommit(Outcome.CONTENT_CONFLICT,
                EventAppendResult.conflict(event.sourceSequence(), incomingSha,
                        event.eventSha256()),
                event, null);
    }
}
