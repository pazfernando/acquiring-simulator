package com.acquiring.simulator.adapter.aws;

import com.acquiring.simulator.domain.event.CanonicalEvent;
import com.acquiring.simulator.domain.event.CommittedVersionEvidence;
import com.acquiring.simulator.domain.event.CriticalCapacity;
import com.acquiring.simulator.domain.event.EventActor;
import com.acquiring.simulator.domain.event.EventContext;
import com.acquiring.simulator.domain.event.EventDurability;
import com.acquiring.simulator.domain.event.EventProvenance;
import com.acquiring.simulator.domain.event.EventReason;
import com.acquiring.simulator.domain.event.EventStatus;
import com.acquiring.simulator.domain.event.EventSubmission;
import com.acquiring.simulator.domain.event.EventTask;
import com.acquiring.simulator.domain.event.OperationAManifest;
import com.acquiring.simulator.domain.model.AuthorizationRecord;
import com.acquiring.simulator.domain.port.ClaimGateway;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

/**
 * DynamoDB adapter for {@link ClaimGateway}: exactly one composed claim transaction.
 *
 * <p>It delegates to {@link AtomicClaimGateway} (one {@code TransactWriteItems}) and then reads the
 * persisted committed-version evidence back from the journal row through
 * {@link DynamoAuthorizationRepository#readVersionEvidence(String)}. The committed version reported
 * in {@link Decision#evidence()} is therefore a stored fact, never a value computed from the
 * internal CAS counter or defaulted to 1.
 *
 * <p>No retry, resend or payment dispatch happens here, and the adapter never falls back to
 * {@link DynamoAuthorizationRepository#claim}. A missing or unreadable persisted claim yields
 * {@link Status#UNKNOWN} rather than an assumed commit.
 *
 * <p><strong>Not activated in {@code AwsRuntime}.</strong> Construction requires an explicit event
 * environment, boot instance identity and critical capacity policy; none of those values is
 * invented here, so the production runtime stays on the legacy claim path until they are supplied.
 */
public final class DynamoClaimGateway implements ClaimGateway {

    /** Producer that owns the critical BUSINESS_PERSIST task in manifest A-1. */
    public static final String PRODUCER_ID = OperationAManifest.PRODUCER_PROCESSOR;
    /** Capability version carried by the claim event; manifest version is the approved A-1. */
    public static final String EVENT_VERSION = "1";
    /** First producer-local sequence for the single initial claim event. */
    public static final long CLAIM_PRODUCER_SEQUENCE = 1L;

    private static final DateTimeFormatter UTC_SECONDS =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").withZone(ZoneOffset.UTC);

    private final DynamoEventStore store;
    private final DynamoAuthorizationRepository repository;
    private final String journalTable;
    private final String eventTable;
    private final CriticalCapacity capacity;
    private final String environment;
    private final String instanceId;

    public DynamoClaimGateway(DynamoEventStore store, DynamoAuthorizationRepository repository,
            String journalTable, String eventTable, CriticalCapacity capacity, String environment,
            String instanceId) {
        this.store = Objects.requireNonNull(store, "store");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.journalTable = Objects.requireNonNull(journalTable, "journalTable");
        this.eventTable = Objects.requireNonNull(eventTable, "eventTable");
        this.capacity = Objects.requireNonNull(capacity, "capacity");
        this.environment = requireText(environment, "environment");
        this.instanceId = requireText(instanceId, "instanceId");
    }

    @Override
    public Decision claim(AuthorizationRecord record, String correlationId, Instant now) {
        Objects.requireNonNull(record, "record");
        String correlation = requireText(correlationId, "correlationId");
        Objects.requireNonNull(now, "now");

        AtomicClaimGateway.ClaimCommit commit = AtomicClaimGateway
                .forClaim(store, journalTable, eventTable, record, capacity)
                .commit(submission(record, correlation, now));

        return switch (commit.outcome()) {
            case COMMITTED, RECOVERED -> persisted(Status.CLAIMED, record.id());
            case REPLAY -> persisted(Status.REPLAY, record.id());
            case CONTENT_CONFLICT -> new Decision(Status.CONTENT_CONFLICT, null, commit.detail());
            case NOT_COMMITTED -> new Decision(Status.NOT_COMMITTED, null, commit.detail());
            case UNKNOWN -> new Decision(Status.UNKNOWN, null, commit.detail());
        };
    }

    /** Read the committed-version evidence back from the stored claim row; never synthesise it. */
    private Decision persisted(Status status, String authorizationId) {
        CommittedVersionEvidence evidence;
        try {
            evidence = repository.readVersionEvidence(authorizationId);
        } catch (RuntimeException unreadable) {
            return new Decision(Status.UNKNOWN, null, "committed claim evidence unreadable");
        }
        return new Decision(status, evidence, null);
    }

    /**
     * The claim event is the critical BUSINESS_PERSIST transition. Its stable identity is derived
     * from the claim's authorization reference so a retried attempt of the same claim hits the same
     * dedupe key instead of creating a second business write.
     */
    private EventSubmission submission(AuthorizationRecord claim, String correlationId, Instant now) {
        String at = UTC_SECONDS.format(now);
        String reference = claim.id();
        EventContext context = new EventContext(environment, claim.acquirerRef(),
                claim.merchantRef(), claim.channelPaymentRef());
        CanonicalEvent event = CanonicalEvent.builder()
                .eventId("CLAIM-" + reference)
                .producerId(PRODUCER_ID)
                .instanceId(instanceId)
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
                .correlationId(correlationId)
                .taskExecutionId("TASK-" + reference)
                .taskName(EventTask.BUSINESS_PERSIST)
                .status(EventStatus.SUCCEEDED)
                .occurredAt(at)
                .producerSequence(CLAIM_PRODUCER_SEQUENCE)
                .provenance(EventProvenance.OBSERVED)
                .reason(EventReason.VALID)
                .build();
        return new EventSubmission(context, event, EventDurability.CRITICAL_COMMITTED,
                ClaimCriticalComposer.CLAIM_JOURNAL_VERSION, at, at, at,
                ClaimCriticalComposer.CLAIM_STATE_AFTER);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value;
    }
}
