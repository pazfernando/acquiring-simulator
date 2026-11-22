package com.acquiring.simulator.adapter.aws;

import com.acquiring.simulator.domain.event.CriticalCapacity;
import com.acquiring.simulator.domain.event.EventContext;
import com.acquiring.simulator.domain.event.EventSourceWrapper;
import com.acquiring.simulator.domain.event.EventSubmission;
import com.acquiring.simulator.domain.event.EventTableKeys;
import com.acquiring.simulator.domain.model.AuthorizationRecord;
import com.acquiring.simulator.domain.model.AuthorizationState;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.Put;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItem;
import software.amazon.awssdk.services.dynamodb.model.Update;

/**
 * Atomic INITIAL CLAIM composition (observation contract O3, transition CLAIMED_NOT_SENT).
 *
 * <p>The claim business record, its business head, the critical claim event, the EVENT head and the
 * dedupe metadata all travel in ONE {@code TransactWriteItems} call: event-table rows from
 * {@link DynamoEventStore}, business rows and the optional slot reservation from here. There is no
 * business-write-then-event dual write: if any condition fails, DynamoDB commits nothing and the
 * source sequence does not advance.
 *
 * <p>Scope is deliberately narrow: this composes the initial claim only. POSSIBLE_SEND, result and
 * ambiguous-send paths are not wired, and this class is not called by {@code AuthorizationService}
 * yet, so it is not production-wired.
 *
 * <p>Slot accounting is opt-in via {@link #withSlotAccounting}. There is NO implicit capacity: the
 * O8 numbers remain PROPOSED and unapproved, so a caller that has not injected an explicit
 * {@link CriticalCapacity} gets no slot write rather than a silently active budget.
 *
 * <p>Claim metadata is checked against the approved state vocabulary: the event must declare the
 * first journal version and the CLAIMED_NOT_SENT state, and the record must still be an
 * undispatched AWAITING claim. A caller cannot label a claim event as a later transition.
 *
 * <p>Version mapping: the approved public committed journal version (1 at claim) is persisted as an
 * additive {@code committedJournalVersion} attribute, alongside the unchanged internal CAS
 * {@code version} (still 0). The public value is never derived as CAS+1; the CAS counter remains the
 * lost-update guard, and a future business transition must advance the committed version atomically.
 */
public final class ClaimCriticalComposer implements CriticalWriteComposer {

    /** Channel-contract claim state (C3/O3): durable claim exists, nothing may have been sent. */
    public static final String CLAIM_STATE_AFTER = "CLAIMED_NOT_SENT";
    /** The claim is the first committed business version for the operation. */
    public static final long CLAIM_JOURNAL_VERSION = 1L;

    private final String journalTable;
    private final String eventTable;
    private final AuthorizationRecord claim;
    private final CriticalCapacity capacity;

    private ClaimCriticalComposer(String journalTable, String eventTable, AuthorizationRecord claim,
            CriticalCapacity capacity) {
        this.journalTable = Objects.requireNonNull(journalTable, "journalTable");
        this.eventTable = Objects.requireNonNull(eventTable, "eventTable");
        this.claim = Objects.requireNonNull(claim, "claim");
        this.capacity = capacity;
    }

    /** Claim composition without slot accounting; no capacity value is implied. */
    public static ClaimCriticalComposer withoutSlotAccounting(String journalTable, String eventTable,
            AuthorizationRecord claim) {
        return new ClaimCriticalComposer(journalTable, eventTable, claim, null);
    }

    /** Claim composition that reserves one critical slot against the explicitly supplied budget. */
    public static ClaimCriticalComposer withSlotAccounting(String journalTable, String eventTable,
            AuthorizationRecord claim, CriticalCapacity capacity) {
        return new ClaimCriticalComposer(journalTable, eventTable, claim,
                Objects.requireNonNull(capacity, "capacity"));
    }

    @Override
    public List<TransactWriteItem> compose(EventSubmission submission, long sequence,
            EventSourceWrapper wrapper) {
        requireInitialClaimSubmission(submission, wrapper);

        List<TransactWriteItem> items = new ArrayList<>(3);
        items.add(TransactWriteItem.builder().put(recordPut()).build());
        items.add(TransactWriteItem.builder().put(headPut()).build());
        if (capacity != null) {
            items.add(TransactWriteItem.builder()
                    .update(slotUpdate(submission.context(), submission.storedAt())).build());
        }
        return List.copyOf(items);
    }

    /**
     * A claim event describes exactly one transition. Rejecting anything else here prevents the
     * store from committing a claim business write under a payload that claims a different state.
     */
    private void requireInitialClaimSubmission(EventSubmission submission,
            EventSourceWrapper wrapper) {
        EventContext context = submission.context();
        if (!context.acquirerRef().equals(claim.acquirerRef())
                || !context.merchantRef().equals(claim.merchantRef())) {
            throw new IllegalArgumentException("claim event context does not match the claimed record");
        }
        Long journalVersion = submission.journalVersion();
        if (journalVersion == null || journalVersion != CLAIM_JOURNAL_VERSION) {
            throw new IllegalArgumentException(
                    "initial claim event must declare journal version " + CLAIM_JOURNAL_VERSION);
        }
        // The wrapper value is the persisted committed version, not a value re-derived at read time.
        Long wrapperVersion = wrapper.journalVersion();
        if (wrapperVersion == null || wrapperVersion != CLAIM_JOURNAL_VERSION) {
            throw new IllegalArgumentException("claim wrapper journal version must equal the persisted"
                    + " committed version " + CLAIM_JOURNAL_VERSION);
        }
        if (!CLAIM_STATE_AFTER.equals(submission.stateAfter())) {
            throw new IllegalArgumentException(
                    "initial claim event must declare stateAfter " + CLAIM_STATE_AFTER);
        }
        if (claim.state() != AuthorizationState.AWAITING
                || claim.dispatchedAt() != null
                || claim.dispatchCorrelationId() != null) {
            throw new IllegalArgumentException("claim composition requires an undispatched claim");
        }
    }

    /** Business record, conditionally created; mirrors {@code claim()} plus the committed version. */
    private Put recordPut() {
        Map<String, AttributeValue> item = new HashMap<>(
                DynamoAuthorizationRepository.recordKey(claim.id()));
        item.put("payload", DynamoAuthorizationRepository.text(WireJson.write(claim)));
        // Internal CAS counter stays at its legacy base: unchanged, never used as the public version.
        item.put("version", AttributeValue.builder()
                .n(Long.toString(DynamoAuthorizationRepository.INITIAL_VERSION)).build());
        // Public committed journal version (1 at claim), persisted explicitly and additively.
        DynamoAuthorizationRepository.putCommittedJournalVersion(item, CLAIM_JOURNAL_VERSION);
        return Put.builder().tableName(journalTable).item(item)
                .conditionExpression("attribute_not_exists(pk)")
                .build();
    }

    /** Business head, conditionally created for generation 1 or advanced for a later generation. */
    private Put headPut() {
        Map<String, AttributeValue> head = new HashMap<>(
                DynamoAuthorizationRepository.businessHeadKey(claim.businessKey()));
        head.put("id", DynamoAuthorizationRepository.text(claim.id()));
        head.put("generation", AttributeValue.builder()
                .n(Integer.toString(claim.generation())).build());
        Put.Builder put = Put.builder().tableName(journalTable).item(head);
        if (claim.generation() == 1) {
            put.conditionExpression("attribute_not_exists(pk)");
        } else {
            put.conditionExpression("generation = :previous")
                    .expressionAttributeValues(Map.of(":previous", AttributeValue.builder()
                            .n(Integer.toString(claim.generation() - 1)).build()));
        }
        return put.build();
    }

    /**
     * Reserve one critical slot in the same transaction. The first write is only admitted when the
     * explicit budget is positive, so a zero budget cannot be overrun by creating the counter.
     */
    private Update slotUpdate(EventContext context, String storedAt) {
        return Update.builder()
                .tableName(eventTable)
                .key(Map.of(
                        "pk", DynamoAuthorizationRepository.text(
                                EventTableKeys.capacityPartition(context)),
                        "sk", DynamoAuthorizationRepository.text(
                                EventTableKeys.capacitySortKey())))
                .conditionExpression("(attribute_not_exists(#used) AND :budget > :zero)"
                        + " OR #used < :budget")
                .updateExpression("SET #budget = :budget, #updatedAt = :updated"
                        + " ADD #used :one")
                .expressionAttributeNames(Map.of(
                        "#used", "usedSlots",
                        "#budget", "slotBudget",
                        "#updatedAt", "updatedAt"))
                .expressionAttributeValues(Map.of(
                        ":one", AttributeValue.builder().n("1").build(),
                        ":zero", AttributeValue.builder().n("0").build(),
                        ":budget", AttributeValue.builder()
                                .n(Integer.toString(capacity.criticalSlotBudget())).build(),
                        ":updated", DynamoAuthorizationRepository.text(storedAt)))
                .build();
    }
}
