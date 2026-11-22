package com.acquiring.simulator.adapter.aws;

import com.acquiring.simulator.domain.event.CanonicalEvent;
import com.acquiring.simulator.domain.event.CriticalCapacity;
import com.acquiring.simulator.domain.event.EventContext;
import com.acquiring.simulator.domain.event.EventDurability;
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
import com.acquiring.simulator.domain.model.DownstreamReply;
import com.acquiring.simulator.domain.model.IssuerDecision;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
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
 * Atomic late verified-evidence composition (observation contract O3/P5, R07/R20, OBS-08).
 *
 * <p>Late evidence is evidence whose <em>evidence time</em> is strictly after the operation's
 * dispatch deadline, so the original exchange could not have reported it. Evidence that became
 * available at or before the deadline but reached the processor afterwards is a
 * <em>delivery delay</em>, not late evidence, and belongs to the normal verified-result path. This
 * composer therefore refuses it instead of re-labelling it.
 *
 * <p>One {@code TransactWriteItems} call carries: the reconciled business record (still
 * {@code AWAITING} with no determined outcome), its explicitly advanced committed journal version
 * 3, the issuer evidence receipt, the immutable critical consultable event, the EVENT HEAD and
 * dedupe metadata, and the optional critical slot reservation. A failed condition commits nothing.
 *
 * <p>No new event task, reason, status, provenance or state is introduced to mark evidence as
 * late. The approved vocabulary is reused exactly as the normal result uses it
 * ({@code BUSINESS_PERSIST}/{@code SUCCEEDED}/{@code VALID}, journal version 3, state after
 * {@code APPROVED} or {@code DECLINED}); the late distinction is carried by receipt metadata
 * outside the canonical event hash (delivery receipt, evidence time, delivery time, deadline). The
 * canonical {@code occurredAt} stays the evidence time, so a redelivery of identical evidence
 * produces identical canonical bytes and classifies as {@code EXACT_REPLAY} instead of a second
 * transition.
 *
 * <p>Precedence is durable, not advisory: the business write is conditioned on the exact
 * CAS/committed pair written by the dispatch-intent transition, so an already-determined outcome,
 * a contradictory confirmation or a legacy row without the additive committed version aborts the
 * whole transaction. This class never overrides a confirmed result, never rescinds or reverses,
 * never resends and never calls downstream. It is not referenced by {@code AuthorizationService} or
 * {@code AwsRuntime}, so it is not production-wired.
 */
public final class LateEvidenceCriticalComposer implements CriticalWriteComposer {

    /**
     * Internal admission routing classification. This is <strong>not</strong> an event task,
     * reason, status, provenance or business state, and it is never persisted as an event label.
     */
    public enum Timing {
        /** Evidence became available at or before the deadline and was delivered by the deadline. */
        ON_TIME,
        /** Evidence became available at or before the deadline but was delivered after it. */
        DELIVERY_DELAY,
        /** Evidence became available strictly after the deadline. */
        LATE
    }

    /** The late confirmation must follow the committed dispatch intent. */
    public static final long PRIOR_INTENT_JOURNAL_VERSION =
            ResultCriticalComposer.PRIOR_INTENT_JOURNAL_VERSION;
    /** The confirmed result is the third committed business version, even when it is late. */
    public static final long LATE_EVIDENCE_JOURNAL_VERSION =
            ResultCriticalComposer.RESULT_JOURNAL_VERSION;
    /** Channel-contract bounds for {@code deliveryReceiptId}. */
    public static final int MAX_RECEIPT_ID_LENGTH = 64;

    /** Receipt metadata names are additive journal attributes, not canonical event fields. */
    static final String RECEIPT_ID_ATTRIBUTE = "evidenceReceiptId";
    static final String EVIDENCE_AT_ATTRIBUTE = "evidenceAvailableAt";
    static final String DELIVERED_AT_ATTRIBUTE = "evidenceDeliveredAt";
    static final String DEADLINE_ATTRIBUTE = "evidenceDeadline";
    static final String LATE_FLAG_ATTRIBUTE = "lateEvidence";

    /** Storage CAS base written by the dispatch-intent transition. */
    private static final long PRIOR_CAS_VERSION = 1L;
    /** Storage CAS value written by this late confirmation. */
    private static final long LATE_EVIDENCE_CAS_VERSION = 2L;

    private static final DateTimeFormatter UTC_SECONDS =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").withZone(ZoneOffset.UTC);

    private final String journalTable;
    private final String eventTable;
    private final AuthorizationRecord intent;
    private final AuthorizationRecord result;
    private final DownstreamReply evidence;
    private final Instant deadline;
    private final Instant deliveredAt;
    private final String deliveryReceiptId;
    private final String resultStateAfter;
    private final String resultEventId;
    private final CriticalCapacity capacity;

    private LateEvidenceCriticalComposer(String journalTable, String eventTable,
            AuthorizationRecord intent, DownstreamReply evidence, Instant deadline,
            Instant deliveredAt, String deliveryReceiptId, CriticalCapacity capacity) {
        this.journalTable = requireText(journalTable, "journalTable");
        this.eventTable = requireText(eventTable, "eventTable");
        this.intent = Objects.requireNonNull(intent, "intent");
        this.evidence = Objects.requireNonNull(evidence, "evidence");
        this.deadline = Objects.requireNonNull(deadline, "deadline");
        this.deliveredAt = Objects.requireNonNull(deliveredAt, "deliveredAt");
        this.deliveryReceiptId = requireBounded(receiptId(deliveryReceiptId), "deliveryReceiptId",
                MAX_RECEIPT_ID_LENGTH);

        requireReconcilableUncertainty(intent);
        requireIssuerEvidence(intent, evidence);
        if (deadline.isBefore(intent.dispatchedAt())) {
            throw new IllegalArgumentException(
                    "deadline cannot precede the durable dispatch");
        }
        if (deliveredAt.isBefore(evidence.availableAt())) {
            throw new IllegalArgumentException(
                    "delivery cannot precede the evidence availability time");
        }
        Timing timing = classify(evidence.availableAt(), deliveredAt, deadline);
        if (timing != Timing.LATE) {
            throw new IllegalArgumentException("evidence timing " + timing
                    + " is not late evidence: it belongs to the normal verified-result path");
        }

        this.resultStateAfter = evidence.decision() == IssuerDecision.APPROVED
                ? ResultCriticalComposer.APPROVED_STATE_AFTER
                : ResultCriticalComposer.DECLINED_STATE_AFTER;
        this.resultEventId = this.resultStateAfter + "-" + intent.id();
        this.result = intent.withOutcome(AuthorizationState.valueOf(resultStateAfter),
                DecisionSource.ISSUER, null, evidence.availableAt());
        if (result.state() != AuthorizationState.valueOf(resultStateAfter)
                || result.decisionSource() != DecisionSource.ISSUER
                || result.dispatchCorrelationId() == null
                || result.dispatchedAt() == null
                || result.outcomeDeterminedAt() == null) {
            throw new IllegalArgumentException(
                    "late confirmation must preserve the durable dispatch and issuer provenance");
        }
        this.capacity = capacity;
    }

    /** Late confirmation without slot accounting; no capacity value is implied. */
    public static LateEvidenceCriticalComposer withoutSlotAccounting(String journalTable,
            String eventTable, AuthorizationRecord intent, DownstreamReply evidence,
            Instant deadline, Instant deliveredAt, String deliveryReceiptId) {
        return new LateEvidenceCriticalComposer(journalTable, eventTable, intent, evidence,
                deadline, deliveredAt, deliveryReceiptId, null);
    }

    /** Late confirmation that reserves one critical slot against the explicit budget. */
    public static LateEvidenceCriticalComposer withSlotAccounting(String journalTable,
            String eventTable, AuthorizationRecord intent, DownstreamReply evidence,
            Instant deadline, Instant deliveredAt, String deliveryReceiptId,
            CriticalCapacity capacity) {
        return new LateEvidenceCriticalComposer(journalTable, eventTable, intent, evidence,
                deadline, deliveredAt, deliveryReceiptId,
                Objects.requireNonNull(capacity, "capacity"));
    }

    /**
     * Late versus delivery-delay is decided by the evidence time, never by the delivery time.
     * Evidence at or before the deadline is on time even if it arrives later; only evidence
     * strictly after the deadline is late. Exactly at the deadline stays on time: A24 keeps a
     * confirmation recorded at the deadline as the confirmed result.
     */
    public static Timing classify(Instant evidenceAt, Instant deliveredAt, Instant deadline) {
        Objects.requireNonNull(evidenceAt, "evidenceAt");
        Objects.requireNonNull(deliveredAt, "deliveredAt");
        Objects.requireNonNull(deadline, "deadline");
        if (deliveredAt.isBefore(evidenceAt)) {
            throw new IllegalArgumentException(
                    "delivery cannot precede the evidence availability time");
        }
        if (evidenceAt.isAfter(deadline)) {
            return Timing.LATE;
        }
        return deliveredAt.isAfter(deadline) ? Timing.DELIVERY_DELAY : Timing.ON_TIME;
    }

    /**
     * Build the fixed submission for one late confirmation. The stable event identity derives from
     * the authorization reference and the confirmed state, so a redelivered identical evidence
     * targets the existing dedupe row instead of creating a second result.
     */
    public static EventSubmission submission(AuthorizationRecord intent, DownstreamReply evidence,
            String environment, String instanceId) {
        return ResultCriticalComposer.submission(intent, evidence, environment, instanceId);
    }

    @Override
    public List<TransactWriteItem> compose(EventSubmission submission, long sequence,
            EventSourceWrapper wrapper) {
        requireLateSubmission(submission, wrapper);

        List<TransactWriteItem> items = new ArrayList<>(2);
        items.add(TransactWriteItem.builder().put(recordPut()).build());
        if (capacity != null) {
            items.add(TransactWriteItem.builder()
                    .update(slotUpdate(submission.context(), submission.storedAt())).build());
        }
        return List.copyOf(items);
    }

    /** Reject a submission that does not describe exactly this fixed late confirmation. */
    private void requireLateSubmission(EventSubmission submission, EventSourceWrapper wrapper) {
        EventContext context = submission.context();
        CanonicalEvent event = submission.event();
        if (!context.acquirerRef().equals(intent.acquirerRef())
                || !context.merchantRef().equals(intent.merchantRef())
                || !context.transactionId().equals(intent.channelPaymentRef())) {
            throw new IllegalArgumentException(
                    "late evidence context does not match the durable dispatch intent");
        }
        if (!event.acquirerRef().equals(intent.acquirerRef())
                || !event.merchantRef().equals(intent.merchantRef())
                || !event.transactionId().equals(intent.channelPaymentRef())
                || !event.terminalRef().equals(intent.terminalRef())) {
            throw new IllegalArgumentException(
                    "late evidence event does not describe the durable dispatch intent");
        }
        if (!event.operationId().equals(intent.id()) || !event.eventId().equals(resultEventId)) {
            throw new IllegalArgumentException(
                    "late evidence event identity is not the stable confirmation identity");
        }
        if (event.taskName() != EventTask.BUSINESS_PERSIST
                || event.status() != EventStatus.SUCCEEDED
                || event.reason() != EventReason.VALID
                || !event.producerId().equals(OperationAManifest.PRODUCER_PROCESSOR)) {
            throw new IllegalArgumentException(
                    "late evidence event must be the PROCESSOR business-persist transition;"
                            + " no late-specific task or reason is approved");
        }
        if (submission.deliveryClass() != EventDurability.CRITICAL_COMMITTED) {
            throw new IllegalArgumentException("late evidence requires a critical committed event");
        }
        if (!event.correlationId().equals(intent.dispatchCorrelationId())) {
            throw new IllegalArgumentException(
                    "late evidence correlation must equal the issued dispatch correlation");
        }
        Long journalVersion = submission.journalVersion();
        if (journalVersion == null || journalVersion != LATE_EVIDENCE_JOURNAL_VERSION) {
            throw new IllegalArgumentException("late evidence must declare journal version "
                    + LATE_EVIDENCE_JOURNAL_VERSION);
        }
        Long wrapperVersion = wrapper.journalVersion();
        if (wrapperVersion == null || wrapperVersion != LATE_EVIDENCE_JOURNAL_VERSION) {
            throw new IllegalArgumentException("late evidence wrapper journal version must equal"
                    + " the persisted committed version " + LATE_EVIDENCE_JOURNAL_VERSION);
        }
        if (!resultStateAfter.equals(submission.stateAfter())) {
            throw new IllegalArgumentException(
                    "late evidence must declare stateAfter " + resultStateAfter);
        }
        String committedAt = UTC_SECONDS.format(evidence.availableAt());
        if (!committedAt.equals(submission.committedAt())) {
            throw new IllegalArgumentException(
                    "late commit time must equal the recorded evidence time");
        }
    }

    /**
     * Business transition from the durable, still-undetermined possible send to the confirmed
     * result, plus the evidence receipt. The condition requires the exact CAS/committed pair
     * written only by the dispatch-intent transition, so an already-confirmed, contradictory or
     * legacy record aborts the whole transaction instead of overriding or backfilling it.
     */
    private Put recordPut() {
        Map<String, AttributeValue> item = new HashMap<>(
                DynamoAuthorizationRepository.recordKey(intent.id()));
        item.put("payload", DynamoAuthorizationRepository.text(WireJson.write(result)));
        item.put("version", AttributeValue.builder()
                .n(Long.toString(LATE_EVIDENCE_CAS_VERSION)).build());
        DynamoAuthorizationRepository.putCommittedJournalVersion(item,
                LATE_EVIDENCE_JOURNAL_VERSION);
        item.put(RECEIPT_ID_ATTRIBUTE, DynamoAuthorizationRepository.text(deliveryReceiptId));
        item.put(EVIDENCE_AT_ATTRIBUTE,
                DynamoAuthorizationRepository.text(UTC_SECONDS.format(evidence.availableAt())));
        item.put(DELIVERED_AT_ATTRIBUTE,
                DynamoAuthorizationRepository.text(UTC_SECONDS.format(deliveredAt)));
        item.put(DEADLINE_ATTRIBUTE,
                DynamoAuthorizationRepository.text(UTC_SECONDS.format(deadline)));
        item.put(LATE_FLAG_ATTRIBUTE, AttributeValue.builder().bool(true).build());
        return Put.builder().tableName(journalTable).item(item)
                .conditionExpression("#version = :expectedVersion"
                        + " AND committedJournalVersion = :expectedCommitted")
                .expressionAttributeNames(Map.of("#version", "version"))
                .expressionAttributeValues(Map.of(
                        ":expectedVersion", AttributeValue.builder()
                                .n(Long.toString(PRIOR_CAS_VERSION)).build(),
                        ":expectedCommitted", AttributeValue.builder()
                                .n(Long.toString(PRIOR_INTENT_JOURNAL_VERSION)).build()))
                .build();
    }

    /**
     * Reserve one critical slot in the same transaction. The first write is admitted only when the
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

    /** Evidence timing for admission, exposed for routing without a second classification. */
    public Timing timing() {
        return classify(evidence.availableAt(), deliveredAt, deadline);
    }

    public Instant deadline() {
        return deadline;
    }

    public String deliveryReceiptId() {
        return deliveryReceiptId;
    }

    public Instant evidenceAt() {
        return evidence.availableAt();
    }

    public Instant deliveredAt() {
        return deliveredAt;
    }

    public String resultStateAfter() {
        return resultStateAfter;
    }

    /**
     * A late confirmation reconciles an operation that may not yet know its outcome. A confirmed,
     * contradictory, network-rejected or legacy-uncertain record is retained as is: no override, no
     * migration and no fabricated committed version.
     */
    private static void requireReconcilableUncertainty(AuthorizationRecord intent) {
        AuthorizationState state = intent.state();
        if (state == null) {
            throw new IllegalArgumentException("late evidence requires a readable record state");
        }
        switch (state) {
            case APPROVED, DECLINED ->
                    throw new IllegalArgumentException("a confirmed outcome is already retained;"
                            + " late evidence never overrides it");
            case NETWORK_REJECTED ->
                    throw new IllegalArgumentException("a processor rejection is already determined;"
                            + " late evidence never overrides it");
            case NOT_CONFIRMED ->
                    throw new IllegalArgumentException("a legacy uncertainty row is not transitioned"
                            + " by this composer: no migration and no fabricated committed version");
            case AWAITING -> { }
        }
        if (intent.outcomeDeterminedAt() != null) {
            throw new IllegalArgumentException("a prior determined outcome is retained");
        }
        if (intent.decisionSource() != DecisionSource.NONE) {
            throw new IllegalArgumentException(
                    "late evidence requires an undetermined possible send");
        }
        if (intent.dispatchedAt() == null
                || intent.dispatchCorrelationId() == null
                || intent.dispatchCorrelationId().isBlank()) {
            throw new IllegalArgumentException(
                    "late evidence requires a durable dispatch intent");
        }
    }

    /** Only authenticated, correlated APPROVED/DECLINED issuer evidence can become a result. */
    private static void requireIssuerEvidence(AuthorizationRecord intent,
            DownstreamReply evidence) {
        if (evidence.decision() != IssuerDecision.APPROVED
                && evidence.decision() != IssuerDecision.DECLINED) {
            throw new IllegalArgumentException(
                    "late evidence must be an APPROVED or DECLINED issuer decision");
        }
        if (evidence.correlationId() == null || evidence.correlationId().isBlank()
                || !evidence.correlationId().equals(intent.dispatchCorrelationId())) {
            throw new IllegalArgumentException(
                    "late evidence correlation must equal the issued dispatch correlation");
        }
        if (evidence.availableAt() == null) {
            throw new IllegalArgumentException("late evidence requires an availability time");
        }
        if (evidence.availableAt().isBefore(intent.dispatchedAt())) {
            throw new IllegalArgumentException(
                    "late evidence cannot predate the durable dispatch");
        }
    }

    private static String receiptId(String value) {
        if (value == null) {
            throw new IllegalArgumentException("deliveryReceiptId is required");
        }
        return value.strip();
    }

    private static String requireBounded(String value, String field, int max) {
        if (value.isEmpty() || value.length() > max) {
            throw new IllegalArgumentException(
                    field + " must be 1-" + max + " characters");
        }
        return value;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value;
    }
}
