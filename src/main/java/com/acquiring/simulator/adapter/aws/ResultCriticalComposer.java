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
 * Atomic VERIFIED RESULT composition (observation contract O3/P5, transitions APPROVED/DECLINED).
 *
 * <p>The committed business result, its explicitly advanced committed journal version, the
 * immutable critical result event, the EVENT head and dedupe metadata, and the optional critical
 * slot reservation all travel in ONE {@code TransactWriteItems} call after a durable dispatch
 * intent. A failed condition commits nothing: no partial result, no state override, no event and no
 * slot.
 *
 * <p>Only authenticated, correlated issuer evidence is admitted. The evidence decision must be
 * {@link IssuerDecision#APPROVED} or {@link IssuerDecision#DECLINED}, its correlation must equal the
 * persisted dispatch correlation, and it cannot predate the durable dispatch. The transition is
 * fixed by this type: it must follow committed journal version 2 / {@code POSSIBLE_SEND} with a
 * CAS base of 1 and an undispatched-outcome record, and it commits journal version 3 with the
 * matching {@code APPROVED} or {@code DECLINED} state. The caller cannot label another state or
 * version as the result.
 *
 * <p>Precedence: an existing determined outcome aborts the whole transaction, so a duplicate or
 * contradictory confirmation never overwrites the first committed result. Late evidence (issuer
 * evidence available after the dispatch deadline) commits the same confirmed state with
 * {@code outcomeDeterminedAt} equal to the evidence time; the caller's boundary outcome remains
 * conservative. Evidence/conflict event variants and ambiguous-result resolution are later slices.
 *
 * <p>This class performs no downstream call, no resend, no reversal and no inquiry. Slot accounting
 * is opt-in via {@link #withSlotAccounting}; there is no implicit capacity default because the O8
 * numbers remain PROPOSED. It is not referenced by {@code AuthorizationService} or
 * {@code AwsRuntime}, so it is not production-wired.
 */
public final class ResultCriticalComposer implements CriticalWriteComposer {

    /** Channel-contract state after verified issuer approval. */
    public static final String APPROVED_STATE_AFTER = "APPROVED";
    /** Channel-contract state after verified issuer decline. */
    public static final String DECLINED_STATE_AFTER = "DECLINED";
    /** The result must follow the committed dispatch intent. */
    public static final long PRIOR_INTENT_JOURNAL_VERSION = 2L;
    /** The verified result is the third committed business version. */
    public static final long RESULT_JOURNAL_VERSION = 3L;
    /** Storage CAS base written by the dispatch-intent transition. */
    private static final long PRIOR_CAS_VERSION = 1L;
    /** Storage CAS value written by the result transition. */
    private static final long RESULT_CAS_VERSION = 2L;
    /** Producer-local sequence of the result event in manifest A-1. */
    private static final long RESULT_PRODUCER_SEQUENCE = 3L;
    /** Capability version carried by the event; manifest version is the approved A-1. */
    private static final String EVENT_VERSION = "1";

    private static final DateTimeFormatter UTC_SECONDS =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").withZone(ZoneOffset.UTC);

    private final String journalTable;
    private final String eventTable;
    private final AuthorizationRecord intent;
    private final AuthorizationRecord result;
    private final String resultEventId;
    private final String resultStateAfter;
    private final Instant determinedAt;
    private final CriticalCapacity capacity;

    private ResultCriticalComposer(String journalTable, String eventTable, AuthorizationRecord intent,
            DownstreamReply evidence, CriticalCapacity capacity) {
        this.journalTable = Objects.requireNonNull(journalTable, "journalTable");
        this.eventTable = Objects.requireNonNull(eventTable, "eventTable");
        this.intent = Objects.requireNonNull(intent, "intent");
        Objects.requireNonNull(evidence, "evidence");
        requireDurableIntent(intent);
        requireIssuerEvidence(intent, evidence);
        this.determinedAt = evidence.availableAt();
        this.resultStateAfter = evidence.decision() == IssuerDecision.APPROVED
                ? APPROVED_STATE_AFTER
                : DECLINED_STATE_AFTER;
        this.resultEventId = resultEventId(intent, resultStateAfter);
        this.result = intent.withOutcome(AuthorizationState.valueOf(resultStateAfter),
                DecisionSource.ISSUER, null, determinedAt);
        if (result.state() != AuthorizationState.valueOf(resultStateAfter)
                || result.decisionSource() != DecisionSource.ISSUER
                || result.dispatchCorrelationId() == null
                || result.dispatchedAt() == null
                || result.outcomeDeterminedAt() == null) {
            throw new IllegalArgumentException(
                    "result must preserve the durable dispatch and record issuer provenance");
        }
        this.capacity = capacity;
    }

    /** Result composition without slot accounting; no capacity value is implied. */
    public static ResultCriticalComposer withoutSlotAccounting(String journalTable,
            String eventTable, AuthorizationRecord intent, DownstreamReply evidence) {
        return new ResultCriticalComposer(journalTable, eventTable, intent, evidence, null);
    }

    /** Result composition that reserves one critical slot against the explicit budget. */
    public static ResultCriticalComposer withSlotAccounting(String journalTable, String eventTable,
            AuthorizationRecord intent, DownstreamReply evidence, CriticalCapacity capacity) {
        return new ResultCriticalComposer(journalTable, eventTable, intent, evidence,
                Objects.requireNonNull(capacity, "capacity"));
    }

    /**
     * Build the fixed result submission for one durable dispatch intent. The stable event identity
     * derives from the authorization reference and the confirmed state, so a retried attempt targets
     * the existing dedupe row instead of creating a second result.
     */
    public static EventSubmission submission(AuthorizationRecord intent, DownstreamReply evidence,
            String environment, String instanceId) {
        Objects.requireNonNull(intent, "intent");
        Objects.requireNonNull(evidence, "evidence");
        String env = requireText(environment, "environment");
        String instance = requireText(instanceId, "instanceId");
        requireDurableIntent(intent);
        requireIssuerEvidence(intent, evidence);
        String stateAfter = evidence.decision() == IssuerDecision.APPROVED
                ? APPROVED_STATE_AFTER
                : DECLINED_STATE_AFTER;
        String formatted = UTC_SECONDS.format(evidence.availableAt());
        String reference = intent.id();
        EventContext context = new EventContext(env, intent.acquirerRef(), intent.merchantRef(),
                intent.channelPaymentRef());
        CanonicalEvent event = CanonicalEvent.builder()
                .eventId(resultEventId(intent, stateAfter))
                .producerId(OperationAManifest.PRODUCER_PROCESSOR)
                .instanceId(instance)
                .actor(EventActor.PROCESSOR)
                .capability(OperationAManifest.CAPABILITY)
                .version(EVENT_VERSION)
                .manifestVersion(OperationAManifest.MANIFEST_VERSION)
                .acquirerRef(intent.acquirerRef())
                .merchantRef(intent.merchantRef())
                .terminalRef(intent.terminalRef())
                .transactionId(intent.channelPaymentRef())
                .operationId(reference)
                .attemptId("ATT-" + reference)
                .correlationId(intent.dispatchCorrelationId())
                .taskExecutionId("RESULT-" + reference)
                .taskName(EventTask.BUSINESS_PERSIST)
                .status(EventStatus.SUCCEEDED)
                .occurredAt(formatted)
                .producerSequence(RESULT_PRODUCER_SEQUENCE)
                .provenance(EventProvenance.OBSERVED)
                .reason(EventReason.VALID)
                .build();
        return new EventSubmission(context, event, EventDurability.CRITICAL_COMMITTED,
                RESULT_JOURNAL_VERSION, formatted, formatted, formatted, stateAfter);
    }

    @Override
    public List<TransactWriteItem> compose(EventSubmission submission, long sequence,
            EventSourceWrapper wrapper) {
        requireResultSubmission(submission, wrapper);

        List<TransactWriteItem> items = new ArrayList<>(2);
        items.add(TransactWriteItem.builder().put(recordPut()).build());
        if (capacity != null) {
            items.add(TransactWriteItem.builder()
                    .update(slotUpdate(submission.context(), submission.storedAt())).build());
        }
        return List.copyOf(items);
    }

    /**
     * Reject a submission that does not describe exactly this fixed result transition, so a caller
     * cannot label another state or version as the committed outcome.
     */
    private void requireResultSubmission(EventSubmission submission, EventSourceWrapper wrapper) {
        EventContext context = submission.context();
        CanonicalEvent event = submission.event();
        if (!context.acquirerRef().equals(intent.acquirerRef())
                || !context.merchantRef().equals(intent.merchantRef())
                || !context.transactionId().equals(intent.channelPaymentRef())) {
            throw new IllegalArgumentException(
                    "result context does not match the durable dispatch intent");
        }
        if (!event.acquirerRef().equals(intent.acquirerRef())
                || !event.merchantRef().equals(intent.merchantRef())
                || !event.transactionId().equals(intent.channelPaymentRef())
                || !event.terminalRef().equals(intent.terminalRef())) {
            throw new IllegalArgumentException(
                    "result event does not describe the durable dispatch intent");
        }
        if (!event.operationId().equals(intent.id())
                || !event.eventId().equals(resultEventId)) {
            throw new IllegalArgumentException(
                    "result event identity is not the stable transition identity");
        }
        if (event.taskName() != EventTask.BUSINESS_PERSIST
                || event.status() != EventStatus.SUCCEEDED
                || event.reason() != EventReason.VALID
                || !event.producerId().equals(OperationAManifest.PRODUCER_PROCESSOR)) {
            throw new IllegalArgumentException(
                    "result event must be the PROCESSOR business-persist transition");
        }
        if (submission.deliveryClass() != EventDurability.CRITICAL_COMMITTED) {
            throw new IllegalArgumentException("result requires a critical committed event");
        }
        if (!event.correlationId().equals(intent.dispatchCorrelationId())) {
            throw new IllegalArgumentException(
                    "result event correlation must equal the issued dispatch correlation");
        }
        Long journalVersion = submission.journalVersion();
        if (journalVersion == null || journalVersion != RESULT_JOURNAL_VERSION) {
            throw new IllegalArgumentException("result event must declare journal version "
                    + RESULT_JOURNAL_VERSION);
        }
        Long wrapperVersion = wrapper.journalVersion();
        if (wrapperVersion == null || wrapperVersion != RESULT_JOURNAL_VERSION) {
            throw new IllegalArgumentException("result wrapper journal version must equal"
                    + " the persisted committed version " + RESULT_JOURNAL_VERSION);
        }
        if (!resultStateAfter.equals(submission.stateAfter())) {
            throw new IllegalArgumentException(
                    "result event must declare stateAfter " + resultStateAfter);
        }
        String committedAt = UTC_SECONDS.format(determinedAt);
        if (!committedAt.equals(submission.committedAt())) {
            throw new IllegalArgumentException(
                    "result commit time must equal the recorded evidence time");
        }
    }

    /**
     * Business transition from POSSIBLE_SEND to the confirmed result. The condition requires the
     * exact CAS/committed pair written only by the durable dispatch-intent transition, so a
     * missing, legacy, concurrent or already-determined record aborts the whole transaction
     * instead of overriding a state.
     *
     * <p>The pair (CAS 1, committed 2) is the persisted dispatch-intent marker; the business
     * payload is intentionally not the condition, because the collector never treats a payload
     * field as storage authority. Any later write advances at least the CAS counter, so a stale
     * result cannot overwrite a newer outcome.
     */
    private Put recordPut() {
        Map<String, AttributeValue> item = new HashMap<>(
                DynamoAuthorizationRepository.recordKey(intent.id()));
        item.put("payload", DynamoAuthorizationRepository.text(WireJson.write(result)));
        item.put("version", AttributeValue.builder()
                .n(Long.toString(RESULT_CAS_VERSION)).build());
        // Explicit public committed version; never derived from the CAS counter.
        DynamoAuthorizationRepository.putCommittedJournalVersion(item, RESULT_JOURNAL_VERSION);
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

    /** A result requires a durable, correlated dispatch and no recorded outcome yet. */
    private static void requireDurableIntent(AuthorizationRecord intent) {
        if (intent.state() != AuthorizationState.AWAITING
                || intent.decisionSource() != DecisionSource.NONE
                || intent.dispatchedAt() == null
                || intent.dispatchCorrelationId() == null
                || intent.dispatchCorrelationId().isBlank()
                || intent.outcomeDeterminedAt() != null) {
            throw new IllegalArgumentException(
                    "result requires an undispatched-outcome POSSIBLE_SEND intent");
        }
    }

    /** Only authenticated, correlated APPROVED/DECLINED issuer evidence can become a result. */
    private static void requireIssuerEvidence(AuthorizationRecord intent, DownstreamReply evidence) {
        if (evidence.decision() != IssuerDecision.APPROVED
                && evidence.decision() != IssuerDecision.DECLINED) {
            throw new IllegalArgumentException(
                    "result evidence must be an APPROVED or DECLINED issuer decision");
        }
        if (evidence.correlationId() == null || evidence.correlationId().isBlank()
                || !evidence.correlationId().equals(intent.dispatchCorrelationId())) {
            throw new IllegalArgumentException(
                    "result evidence correlation must equal the issued dispatch correlation");
        }
        if (evidence.availableAt() == null) {
            throw new IllegalArgumentException("result evidence requires an availability time");
        }
        if (evidence.availableAt().isBefore(intent.dispatchedAt())) {
            throw new IllegalArgumentException(
                    "result evidence cannot predate the durable dispatch");
        }
    }

    /** Fixed state/event mapping: APPROVED and DECLINED each get one stable identity. */
    private static String resultEventId(AuthorizationRecord intent, String stateAfter) {
        return stateAfter + "-" + intent.id();
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value;
    }
}
