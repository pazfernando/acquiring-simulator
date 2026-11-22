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
 * Atomic explicit NETWORK_REJECTED composition (observation contract O3, guide NET-01).
 *
 * <p>The committed processor-visible rejection, its explicitly advanced committed journal version,
 * the immutable critical business-persist event, the EVENT head and dedupe metadata, and the
 * optional critical slot reservation all travel in ONE {@code TransactWriteItems} call after a
 * durable dispatch intent. A failed condition commits nothing: no partial outcome, no version
 * advance, no event and no slot.
 *
 * <p>Approved semantics this type preserves (sources in the slice manifest):
 * <ul>
 *   <li>Guide NET-01 / OBS-03: an explicit network rejection is reported as a
 *       processor/network rejection with its actual provenance and is never labelled an issuer
 *       decline or an issuer decision.</li>
 *   <li>R05/R10/R22: the persisted business state is
 *       {@link AuthorizationState#NETWORK_REJECTED} with {@link DecisionSource#NETWORK} provenance,
 *       so a pre-issuer network rejection stays distinct from an issuer decline. The caller-visible
 *       outcome stays {@code PROCESSOR_REJECTED} (mapped by the caller, not by this composer).</li>
 *   <li>Attribution of the reason string: NET-01/OBS-03 do not name {@code NETWORK_REJECTION}, and
 *       the approved event reason allowlist ({@link EventReason}, which mirrors
 *       {@code RejectionReason}) does not contain it. The constant below is existing internal
 *       domain-code vocabulary — the same literal the legacy {@code AuthorizationService} path
 *       records for this transition — written to the business record only. The critical event
 *       therefore carries {@link EventReason#VALID}; no new public code or event reason is
 *       introduced.</li>
 *   <li>Channel contract: a pre-issuer-dispatch explicit processor rejection committed as a
 *       business result has precedence as readable durable evidence.</li>
 * </ul>
 *
 * <p>The transition is fixed by this type: it must follow committed journal version 2 /
 * {@code POSSIBLE_SEND} with the CAS base 1 and an undispatched-outcome record, and it commits
 * journal version 3 with {@code NETWORK_REJECTED}. A later write advances at least the CAS counter,
 * so a stale rejection can never overwrite an issuer-confirmed result; a rejection of a rejection
 * is an exact replay, never a second event or slot.
 *
 * <p>The critical event is the PROCESSOR business-persist transition, exactly as the claim,
 * possible-send and verified-result composers: the manifest declares {@code BUSINESS_PERSIST} as a
 * PROCESSOR/critical task and has no network-rejection task or reason code. Network provenance is
 * carried by the business record, not fabricated as an ISSUER or NETWORK task event; skipped issuer
 * work therefore leaves no issuer event.
 *
 * <p>This class performs no downstream call, no resend, no reversal and no inquiry. It appends no
 * legacy event and never calls {@code updateOutcome}. Slot accounting is opt-in via
 * {@link #withSlotAccounting}; there is no implicit capacity default because the O8 numbers remain
 * PROPOSED. It is not referenced by {@code AwsRuntime}; {@code AuthorizationService} now consumes
 * the port through {@link DynamoNetworkResultGateway}, so production wiring remains a later slice.
 */
public final class NetworkRejectedCriticalComposer implements CriticalWriteComposer {

    /** Channel/business state after an explicit network rejection (existing domain state). */
    public static final String NETWORK_REJECTED_STATE_AFTER = "NETWORK_REJECTED";
    /** Existing internal domain category for an explicit network rejection (not a rule code). */
    public static final String NETWORK_REJECTION_REASON = "NETWORK_REJECTION";
    /** The rejection must follow the committed dispatch intent. */
    public static final long PRIOR_INTENT_JOURNAL_VERSION = 2L;
    /** The explicit rejection is the third committed business version. */
    public static final long NETWORK_REJECTED_JOURNAL_VERSION = 3L;
    /** Storage CAS base written by the dispatch-intent transition. */
    private static final long PRIOR_CAS_VERSION = 1L;
    /** Storage CAS value written by the rejection transition. */
    private static final long REJECTION_CAS_VERSION = 2L;
    /** Producer-local sequence of the rejection event in manifest A-1. */
    private static final long REJECTION_PRODUCER_SEQUENCE = 3L;
    /** Capability version carried by the event; manifest version is the approved A-1. */
    private static final String EVENT_VERSION = "1";

    private static final DateTimeFormatter UTC_SECONDS =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").withZone(ZoneOffset.UTC);

    private final String journalTable;
    private final String eventTable;
    private final AuthorizationRecord intent;
    private final AuthorizationRecord rejection;
    private final String rejectionEventId;
    private final Instant determinedAt;
    private final CriticalCapacity capacity;

    private NetworkRejectedCriticalComposer(String journalTable, String eventTable,
            AuthorizationRecord intent, DownstreamReply evidence, CriticalCapacity capacity) {
        this.journalTable = Objects.requireNonNull(journalTable, "journalTable");
        this.eventTable = Objects.requireNonNull(eventTable, "eventTable");
        this.intent = Objects.requireNonNull(intent, "intent");
        Objects.requireNonNull(evidence, "evidence");
        requireDurableIntent(intent);
        requireNetworkEvidence(intent, evidence);
        this.determinedAt = evidence.availableAt();
        this.rejectionEventId = rejectionEventId(intent);
        this.rejection = intent.withOutcome(AuthorizationState.NETWORK_REJECTED,
                DecisionSource.NETWORK, NETWORK_REJECTION_REASON, determinedAt);
        if (rejection.state() != AuthorizationState.NETWORK_REJECTED
                || rejection.decisionSource() != DecisionSource.NETWORK
                || !NETWORK_REJECTION_REASON.equals(rejection.reasonCategory())
                || rejection.dispatchCorrelationId() == null
                || rejection.dispatchedAt() == null
                || rejection.outcomeDeterminedAt() == null) {
            throw new IllegalArgumentException(
                    "network rejection must preserve the durable dispatch and record network provenance");
        }
        this.capacity = capacity;
    }

    /** Rejection composition without slot accounting; no capacity value is implied. */
    public static NetworkRejectedCriticalComposer withoutSlotAccounting(String journalTable,
            String eventTable, AuthorizationRecord intent, DownstreamReply evidence) {
        return new NetworkRejectedCriticalComposer(journalTable, eventTable, intent, evidence, null);
    }

    /** Rejection composition that reserves one critical slot against the explicit budget. */
    public static NetworkRejectedCriticalComposer withSlotAccounting(String journalTable,
            String eventTable, AuthorizationRecord intent, DownstreamReply evidence,
            CriticalCapacity capacity) {
        return new NetworkRejectedCriticalComposer(journalTable, eventTable, intent, evidence,
                Objects.requireNonNull(capacity, "capacity"));
    }

    /**
     * Build the fixed rejection submission for one durable dispatch intent. The stable event
     * identity derives from the authorization reference, so a retried attempt targets the existing
     * dedupe row instead of creating a second rejection.
     */
    public static EventSubmission submission(AuthorizationRecord intent, DownstreamReply evidence,
            String environment, String instanceId) {
        Objects.requireNonNull(intent, "intent");
        Objects.requireNonNull(evidence, "evidence");
        String env = requireText(environment, "environment");
        String instance = requireText(instanceId, "instanceId");
        requireDurableIntent(intent);
        requireNetworkEvidence(intent, evidence);
        String formatted = UTC_SECONDS.format(evidence.availableAt());
        String reference = intent.id();
        EventContext context = new EventContext(env, intent.acquirerRef(), intent.merchantRef(),
                intent.channelPaymentRef());
        CanonicalEvent event = CanonicalEvent.builder()
                .eventId(rejectionEventId(intent))
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
                .taskExecutionId("REJECT-" + reference)
                .taskName(EventTask.BUSINESS_PERSIST)
                .status(EventStatus.SUCCEEDED)
                .occurredAt(formatted)
                .producerSequence(REJECTION_PRODUCER_SEQUENCE)
                .provenance(EventProvenance.OBSERVED)
                .reason(EventReason.VALID)
                .build();
        return new EventSubmission(context, event, EventDurability.CRITICAL_COMMITTED,
                NETWORK_REJECTED_JOURNAL_VERSION, formatted, formatted, formatted,
                NETWORK_REJECTED_STATE_AFTER);
    }

    @Override
    public List<TransactWriteItem> compose(EventSubmission submission, long sequence,
            EventSourceWrapper wrapper) {
        requireRejectionSubmission(submission, wrapper);

        List<TransactWriteItem> items = new ArrayList<>(2);
        items.add(TransactWriteItem.builder().put(recordPut()).build());
        if (capacity != null) {
            items.add(TransactWriteItem.builder()
                    .update(slotUpdate(submission.context(), submission.storedAt())).build());
        }
        return List.copyOf(items);
    }

    /**
     * Reject a submission that does not describe exactly this fixed transition, so a caller cannot
     * label another state, version or identity as the committed rejection.
     */
    private void requireRejectionSubmission(EventSubmission submission, EventSourceWrapper wrapper) {
        EventContext context = submission.context();
        CanonicalEvent event = submission.event();
        if (!context.acquirerRef().equals(intent.acquirerRef())
                || !context.merchantRef().equals(intent.merchantRef())
                || !context.transactionId().equals(intent.channelPaymentRef())) {
            throw new IllegalArgumentException(
                    "rejection context does not match the durable dispatch intent");
        }
        if (!event.acquirerRef().equals(intent.acquirerRef())
                || !event.merchantRef().equals(intent.merchantRef())
                || !event.transactionId().equals(intent.channelPaymentRef())
                || !event.terminalRef().equals(intent.terminalRef())) {
            throw new IllegalArgumentException(
                    "rejection event does not describe the durable dispatch intent");
        }
        if (!event.operationId().equals(intent.id())
                || !event.eventId().equals(rejectionEventId)) {
            throw new IllegalArgumentException(
                    "rejection event identity is not the stable transition identity");
        }
        if (event.taskName() != EventTask.BUSINESS_PERSIST
                || event.status() != EventStatus.SUCCEEDED
                || event.reason() != EventReason.VALID
                || !event.producerId().equals(OperationAManifest.PRODUCER_PROCESSOR)) {
            throw new IllegalArgumentException(
                    "rejection event must be the PROCESSOR business-persist transition");
        }
        if (submission.deliveryClass() != EventDurability.CRITICAL_COMMITTED) {
            throw new IllegalArgumentException("rejection requires a critical committed event");
        }
        if (!event.correlationId().equals(intent.dispatchCorrelationId())) {
            throw new IllegalArgumentException(
                    "rejection event correlation must equal the issued dispatch correlation");
        }
        Long journalVersion = submission.journalVersion();
        if (journalVersion == null || journalVersion != NETWORK_REJECTED_JOURNAL_VERSION) {
            throw new IllegalArgumentException("rejection event must declare journal version "
                    + NETWORK_REJECTED_JOURNAL_VERSION);
        }
        Long wrapperVersion = wrapper.journalVersion();
        if (wrapperVersion == null || wrapperVersion != NETWORK_REJECTED_JOURNAL_VERSION) {
            throw new IllegalArgumentException("rejection wrapper journal version must equal"
                    + " the persisted committed version " + NETWORK_REJECTED_JOURNAL_VERSION);
        }
        if (!NETWORK_REJECTED_STATE_AFTER.equals(submission.stateAfter())) {
            throw new IllegalArgumentException("rejection event must declare stateAfter "
                    + NETWORK_REJECTED_STATE_AFTER);
        }
        String committedAt = UTC_SECONDS.format(determinedAt);
        if (!committedAt.equals(submission.committedAt())) {
            throw new IllegalArgumentException(
                    "rejection commit time must equal the recorded evidence time");
        }
    }

    /**
     * Business transition from POSSIBLE_SEND to NETWORK_REJECTED. The condition requires the exact
     * CAS/committed pair written only by the durable dispatch-intent transition, so a missing,
     * legacy, concurrent or already-determined record aborts the whole transaction instead of
     * overriding a state. An issuer-confirmed outcome has already advanced that pair and therefore
     * cancels a stale rejection.
     */
    private Put recordPut() {
        Map<String, AttributeValue> item = new HashMap<>(
                DynamoAuthorizationRepository.recordKey(intent.id()));
        item.put("payload", DynamoAuthorizationRepository.text(WireJson.write(rejection)));
        item.put("version", AttributeValue.builder()
                .n(Long.toString(REJECTION_CAS_VERSION)).build());
        // Explicit public committed version; never derived from the CAS counter.
        DynamoAuthorizationRepository.putCommittedJournalVersion(item,
                NETWORK_REJECTED_JOURNAL_VERSION);
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

    /** A rejection requires a durable, correlated dispatch and no recorded outcome yet. */
    private static void requireDurableIntent(AuthorizationRecord intent) {
        if (intent.state() != AuthorizationState.AWAITING
                || intent.decisionSource() != DecisionSource.NONE
                || intent.dispatchedAt() == null
                || intent.dispatchCorrelationId() == null
                || intent.dispatchCorrelationId().isBlank()
                || intent.outcomeDeterminedAt() != null) {
            throw new IllegalArgumentException(
                    "network rejection requires an undispatched-outcome POSSIBLE_SEND intent");
        }
    }

    /**
     * Only a correlated, timed NETWORK_REJECTED reply is admitted. APPROVED/DECLINED stay issuer
     * decisions for the result composer; NO_RESPONSE/MALFORMED stay uncertainty. A rejection that
     * predates the durable dispatch is refused as uncorrelated evidence rather than committed.
     */
    private static void requireNetworkEvidence(AuthorizationRecord intent, DownstreamReply evidence) {
        if (evidence.decision() != IssuerDecision.NETWORK_REJECTED) {
            throw new IllegalArgumentException(
                    "network rejection evidence must be an explicit NETWORK_REJECTED decision");
        }
        if (evidence.correlationId() == null || evidence.correlationId().isBlank()
                || !evidence.correlationId().equals(intent.dispatchCorrelationId())) {
            throw new IllegalArgumentException(
                    "network rejection correlation must equal the issued dispatch correlation");
        }
        if (evidence.availableAt() == null) {
            throw new IllegalArgumentException("network rejection requires an availability time");
        }
        if (evidence.availableAt().isBefore(intent.dispatchedAt())) {
            throw new IllegalArgumentException(
                    "network rejection cannot predate the durable dispatch");
        }
    }

    /** Fixed state/event mapping: one stable identity per authorization reference. */
    private static String rejectionEventId(AuthorizationRecord intent) {
        return NETWORK_REJECTED_STATE_AFTER + "-" + intent.id();
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value;
    }
}
