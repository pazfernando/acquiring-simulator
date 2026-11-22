package com.acquiring.simulator.adapter.aws;

import com.acquiring.simulator.domain.event.CanonicalEvent;
import com.acquiring.simulator.domain.event.CriticalCapacity;
import com.acquiring.simulator.domain.event.EventActor;
import com.acquiring.simulator.domain.event.EventContext;
import com.acquiring.simulator.domain.event.EventDurability;
import com.acquiring.simulator.domain.event.EventProvenance;
import com.acquiring.simulator.domain.event.EventReason;
import com.acquiring.simulator.domain.event.EventSourceWrapper;
import com.acquiring.simulator.domain.event.EventStatus;
import com.acquiring.simulator.domain.event.EventSubmission;
import com.acquiring.simulator.domain.event.EventTableKeys;
import com.acquiring.simulator.domain.event.EventTask;
import com.acquiring.simulator.domain.event.OperationAManifest;
import com.acquiring.simulator.domain.model.AuthorizationRecord;
import com.acquiring.simulator.domain.model.AuthorizationState;
import com.acquiring.simulator.domain.model.DecisionSource;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.Put;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItem;
import software.amazon.awssdk.services.dynamodb.model.Update;

/**
 * Atomic dispatch-intent composition (observation contract O3, transition POSSIBLE_SEND).
 *
 * <p>The conditioned business transition, its explicitly advanced committed journal version, the
 * immutable critical event, the EVENT head and dedupe metadata, and the optional critical slot
 * reservation all travel in ONE {@code TransactWriteItems} call. A failed condition commits nothing:
 * no partial dispatch intent, no event, no sequence advance and no slot.
 *
 * <p>This is the smallest send-boundary composer. It records that a send is now <em>possible</em>;
 * it does not invoke the downstream, return a payment result or recover an ambiguous send. An
 * ambiguous commit must be resolved by a strongly consistent lookup of the stable event identity
 * ({@code POSSIBLE_SEND-<authorizationId>}); absence is not proof that nothing was sent, and this
 * class never authorises a resend.
 *
 * <p>The transition is fixed by this type, not selected by a caller: it must follow committed
 * journal version 1 / {@code CLAIMED_NOT_SENT} and commits journal version 2 /
 * {@code POSSIBLE_SEND}. The business record write is guarded by the recorded CAS base and committed
 * version, so a missing, legacy or concurrently changed claim fails closed. The internal CAS counter
 * advances to 1 as the lost-update guard; the public committed version advances to 2 explicitly and
 * is never derived from the CAS counter.
 *
 * <p>Slot accounting is opt-in via {@link #withSlotAccounting}. There is no implicit capacity: the
 * O8 numbers remain PROPOSED, so a caller without an explicit {@link CriticalCapacity} gets no slot
 * write rather than a silently active budget.
 *
 * <p><strong>Not production-wired.</strong> This class is not referenced by
 * {@code AuthorizationService} or {@code AwsRuntime}; result coupling and service activation are a
 * later slice.
 *
 * <p>When an exact outbound {@code 0100} is supplied, its immutable octets are persisted in the
 * SAME transaction under {@code AUTH#<id>/ORIGINAL}. The send therefore cannot become possible
 * without the original instruction that a later signed response must be bound to.</p>
 */
public final class PossibleSendCriticalComposer implements CriticalWriteComposer {

    /** Channel-contract state after the dispatch intent is durable. */
    public static final String POSSIBLE_SEND_STATE_AFTER = "POSSIBLE_SEND";
    /** The claim must have committed this journal version before a send can be possible. */
    public static final long PRIOR_CLAIM_JOURNAL_VERSION = 1L;
    /** The dispatch intent is the second committed business version. */
    public static final long POSSIBLE_SEND_JOURNAL_VERSION = 2L;
    /** Storage CAS base written by the atomic initial claim. */
    private static final long PRIOR_CAS_VERSION = 0L;
    /** Producer-local sequence of the dispatch-intent event in manifest A-1. */
    private static final long POSSIBLE_SEND_PRODUCER_SEQUENCE = 2L;
    /** Capability version carried by the event; manifest version is the approved A-1. */
    private static final String EVENT_VERSION = "1";

    private static final DateTimeFormatter UTC_SECONDS =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").withZone(ZoneOffset.UTC);

    private final String journalTable;
    private final String eventTable;
    private final AuthorizationRecord claim;
    private final AuthorizationRecord intent;
    private final CriticalCapacity capacity;
    private final byte[] originalInstruction0100;

    private PossibleSendCriticalComposer(String journalTable, String eventTable,
            AuthorizationRecord claim, AuthorizationRecord intent, CriticalCapacity capacity,
            byte[] originalInstruction0100) {
        this.journalTable = Objects.requireNonNull(journalTable, "journalTable");
        this.eventTable = Objects.requireNonNull(eventTable, "eventTable");
        this.claim = Objects.requireNonNull(claim, "claim");
        this.intent = Objects.requireNonNull(intent, "intent");
        this.capacity = capacity;
        if (originalInstruction0100 != null) {
            if (originalInstruction0100.length == 0) {
                throw new IllegalArgumentException(
                        "original instruction must not be empty when supplied");
            }
            this.originalInstruction0100 = originalInstruction0100.clone();
        } else {
            this.originalInstruction0100 = null;
        }
    }

    /** Dispatch-intent composition without slot accounting; no capacity value is implied. */
    public static PossibleSendCriticalComposer withoutSlotAccounting(String journalTable,
            String eventTable, AuthorizationRecord claim, String dispatchCorrelationId,
            Instant dispatchedAt) {
        return withoutSlotAccounting(journalTable, eventTable, claim, dispatchCorrelationId,
                dispatchedAt, null);
    }

    /**
     * Dispatch-intent composition that also persists the exact outbound {@code 0100} octets in the
     * same transaction; no capacity value is implied.
     */
    public static PossibleSendCriticalComposer withoutSlotAccounting(String journalTable,
            String eventTable, AuthorizationRecord claim, String dispatchCorrelationId,
            Instant dispatchedAt, byte[] originalInstruction0100) {
        return new PossibleSendCriticalComposer(journalTable, eventTable, claim,
                intent(claim, dispatchCorrelationId, dispatchedAt), null, originalInstruction0100);
    }

    /** Dispatch-intent composition that reserves one critical slot against the explicit budget. */
    public static PossibleSendCriticalComposer withSlotAccounting(String journalTable,
            String eventTable, AuthorizationRecord claim, String dispatchCorrelationId,
            Instant dispatchedAt, CriticalCapacity capacity) {
        return withSlotAccounting(journalTable, eventTable, claim, dispatchCorrelationId,
                dispatchedAt, capacity, null);
    }

    /**
     * Dispatch-intent composition that reserves one critical slot and persists the exact outbound
     * {@code 0100} octets in the same transaction.
     */
    public static PossibleSendCriticalComposer withSlotAccounting(String journalTable,
            String eventTable, AuthorizationRecord claim, String dispatchCorrelationId,
            Instant dispatchedAt, CriticalCapacity capacity, byte[] originalInstruction0100) {
        return new PossibleSendCriticalComposer(journalTable, eventTable, claim,
                intent(claim, dispatchCorrelationId, dispatchedAt),
                Objects.requireNonNull(capacity, "capacity"), originalInstruction0100);
    }

    /**
     * Build the fixed POSSIBLE_SEND submission for one claim. The stable event identity is derived
     * from the authorization reference, so a retried attempt targets the existing dedupe row instead
     * of creating a second send intent.
     */
    public static EventSubmission submission(AuthorizationRecord claim, String dispatchCorrelationId,
            String environment, String instanceId, Instant at) {
        Objects.requireNonNull(claim, "claim");
        String correlation = requireText(dispatchCorrelationId, "dispatchCorrelationId");
        String env = requireText(environment, "environment");
        String instance = requireText(instanceId, "instanceId");
        Objects.requireNonNull(at, "at");
        String formatted = UTC_SECONDS.format(at);
        String reference = claim.id();
        EventContext context = new EventContext(env, claim.acquirerRef(), claim.merchantRef(),
                claim.channelPaymentRef());
        CanonicalEvent event = CanonicalEvent.builder()
                .eventId("POSSIBLE_SEND-" + reference)
                .producerId(OperationAManifest.PRODUCER_PROCESSOR)
                .instanceId(instance)
                .actor(EventActor.PROCESSOR)
                .capability(OperationAManifest.CAPABILITY)
                .version(EVENT_VERSION)
                .manifestVersion(OperationAManifest.MANIFEST_VERSION)
                .acquirerRef(claim.acquirerRef())
                .merchantRef(claim.merchantRef())
                .terminalRef(claim.terminalRef())
                .transactionId(claim.channelPaymentRef())
                .operationId(reference)
                .attemptId("ATT-" + reference)
                .correlationId(correlation)
                .taskExecutionId("SEND-" + reference)
                .taskName(EventTask.BUSINESS_PERSIST)
                .status(EventStatus.SUCCEEDED)
                .occurredAt(formatted)
                .producerSequence(POSSIBLE_SEND_PRODUCER_SEQUENCE)
                .provenance(EventProvenance.OBSERVED)
                .reason(EventReason.VALID)
                .build();
        return new EventSubmission(context, event, EventDurability.CRITICAL_COMMITTED,
                POSSIBLE_SEND_JOURNAL_VERSION, formatted, formatted, formatted,
                POSSIBLE_SEND_STATE_AFTER);
    }

    @Override
    public List<TransactWriteItem> compose(EventSubmission submission, long sequence,
            EventSourceWrapper wrapper) {
        requireSendIntentSubmission(submission, wrapper);

        List<TransactWriteItem> items = new ArrayList<>(3);
        items.add(TransactWriteItem.builder().put(recordPut()).build());
        if (originalInstruction0100 != null) {
            items.add(TransactWriteItem.builder().put(originalInstructionPut()).build());
        }
        if (capacity != null) {
            items.add(TransactWriteItem.builder()
                    .update(slotUpdate(submission.context(), submission.storedAt())).build());
        }
        return List.copyOf(items);
    }

    /**
     * Reject a submission that does not describe exactly this fixed transition, so a caller cannot
     * label another state or version as the durable dispatch intent.
     */
    private void requireSendIntentSubmission(EventSubmission submission,
            EventSourceWrapper wrapper) {
        EventContext context = submission.context();
        CanonicalEvent event = submission.event();
        if (!context.acquirerRef().equals(claim.acquirerRef())
                || !context.merchantRef().equals(claim.merchantRef())
                || !context.transactionId().equals(claim.channelPaymentRef())) {
            throw new IllegalArgumentException(
                    "dispatch-intent context does not match the claimed record");
        }
        if (!event.acquirerRef().equals(claim.acquirerRef())
                || !event.merchantRef().equals(claim.merchantRef())
                || !event.transactionId().equals(claim.channelPaymentRef())
                || !event.terminalRef().equals(claim.terminalRef())) {
            throw new IllegalArgumentException(
                    "dispatch-intent event does not describe the claimed record");
        }
        if (!event.operationId().equals(claim.id())
                || !event.eventId().equals("POSSIBLE_SEND-" + claim.id())) {
            throw new IllegalArgumentException(
                    "dispatch-intent event identity is not the stable transition identity");
        }
        if (event.taskName() != EventTask.BUSINESS_PERSIST
                || event.status() != EventStatus.SUCCEEDED
                || event.reason() != EventReason.VALID
                || !event.producerId().equals(OperationAManifest.PRODUCER_PROCESSOR)) {
            throw new IllegalArgumentException(
                    "dispatch-intent event must be the PROCESSOR business-persist transition");
        }
        if (submission.deliveryClass() != EventDurability.CRITICAL_COMMITTED) {
            throw new IllegalArgumentException(
                    "dispatch intent requires a critical committed event");
        }
        if (!event.correlationId().equals(intent.dispatchCorrelationId())) {
            throw new IllegalArgumentException(
                    "dispatch-intent event correlation must match the recorded dispatch intent");
        }
        Long journalVersion = submission.journalVersion();
        if (journalVersion == null || journalVersion != POSSIBLE_SEND_JOURNAL_VERSION) {
            throw new IllegalArgumentException("dispatch-intent event must declare journal version "
                    + POSSIBLE_SEND_JOURNAL_VERSION);
        }
        Long wrapperVersion = wrapper.journalVersion();
        if (wrapperVersion == null || wrapperVersion != POSSIBLE_SEND_JOURNAL_VERSION) {
            throw new IllegalArgumentException("dispatch-intent wrapper journal version must equal"
                    + " the persisted committed version " + POSSIBLE_SEND_JOURNAL_VERSION);
        }
        if (!POSSIBLE_SEND_STATE_AFTER.equals(submission.stateAfter())) {
            throw new IllegalArgumentException("dispatch-intent event must declare stateAfter "
                    + POSSIBLE_SEND_STATE_AFTER);
        }
        String committedAt = UTC_SECONDS.format(intent.dispatchedAt());
        if (!committedAt.equals(submission.committedAt())) {
            throw new IllegalArgumentException(
                    "dispatch-intent commit time must equal the recorded dispatch time");
        }
    }

    /**
     * Business transition from CLAIMED_NOT_SENT to POSSIBLE_SEND. The condition requires the
     * recorded CAS base, the recorded committed version and an undispatched record, so a legacy
     * row without committed-version evidence or a concurrent write aborts the whole transaction.
     */
    private Put recordPut() {
        Map<String, AttributeValue> item = new HashMap<>(
                DynamoAuthorizationRepository.recordKey(claim.id()));
        item.put("payload", DynamoAuthorizationRepository.text(WireJson.write(intent)));
        item.put("version", AttributeValue.builder()
                .n(Long.toString(PRIOR_CAS_VERSION + 1)).build());
        // Explicit public committed version; never derived from the CAS counter.
        DynamoAuthorizationRepository.putCommittedJournalVersion(item,
                POSSIBLE_SEND_JOURNAL_VERSION);
        return Put.builder().tableName(journalTable).item(item)
                .conditionExpression("#version = :expectedVersion"
                        + " AND committedJournalVersion = :expectedCommitted"
                        + " AND attribute_not_exists(dispatchedAt)")
                .expressionAttributeNames(Map.of("#version", "version"))
                .expressionAttributeValues(Map.of(
                        ":expectedVersion", AttributeValue.builder()
                                .n(Long.toString(PRIOR_CAS_VERSION)).build(),
                        ":expectedCommitted", AttributeValue.builder()
                                .n(Long.toString(PRIOR_CLAIM_JOURNAL_VERSION)).build()))
                .build();
    }

    /**
     * Persist the exact outbound {@code 0100} octets as an immutable journal item. The condition
     * makes a second write fail the whole transaction instead of overwriting the first instruction;
     * an exact intent replay returns from the event store before composition and never reaches it.
     */
    private Put originalInstructionPut() {
        Map<String, AttributeValue> item = new HashMap<>(
                DynamoOriginalInstructionReader.originalKey(claim.id()));
        item.put(DynamoOriginalInstructionReader.ORIGINAL_BYTES_ATTRIBUTE,
                AttributeValue.builder()
                        .b(SdkBytes.fromByteArray(originalInstruction0100))
                        .build());
        return Put.builder().tableName(journalTable).item(item)
                .conditionExpression("attribute_not_exists(pk)")
                .build();
    }

    /**
     * Reserve one critical slot in the same transaction. Mirrors the claim reservation: the first
     * write is admitted only when the explicit budget is positive, so a zero budget cannot be
     * overrun by creating the counter.
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

    /** Build the intent record from the previous claim; no caller-supplied state or version. */
    private static AuthorizationRecord intent(AuthorizationRecord claim, String correlationId,
            Instant dispatchedAt) {
        Objects.requireNonNull(claim, "claim");
        String correlation = requireText(correlationId, "dispatchCorrelationId");
        Objects.requireNonNull(dispatchedAt, "dispatchedAt");
        if (claim.state() != AuthorizationState.AWAITING
                || claim.dispatchedAt() != null
                || claim.dispatchCorrelationId() != null
                || claim.decisionSource() != DecisionSource.NONE) {
            throw new IllegalArgumentException(
                    "dispatch intent requires an undispatched CLAIMED_NOT_SENT claim");
        }
        AuthorizationRecord intent = claim.withDispatch(correlation, dispatchedAt);
        if (intent.state() != AuthorizationState.AWAITING
                || intent.decisionSource() != DecisionSource.NONE) {
            throw new IllegalArgumentException(
                    "dispatch intent must preserve the AWAITING/NONE business meaning");
        }
        return intent;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value;
    }
}
