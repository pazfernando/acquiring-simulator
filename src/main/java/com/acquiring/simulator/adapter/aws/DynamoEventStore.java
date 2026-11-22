package com.acquiring.simulator.adapter.aws;

import com.acquiring.simulator.domain.event.*;
import com.acquiring.simulator.domain.port.EventStore;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

/**
 * Dedicated functional EVENT table adapter (observation contract O2a/O3).
 *
 * <p>One append is a single {@code TransactWriteItems} with three event-table writes: the immutable
 * event row, its dedupe metadata row pointing at the stored sequence, and a conditional HEAD update
 * that allocates the next twelve-digit source sequence. A lost conditional race is retried against a
 * freshly read HEAD; a failed composed business write aborts everything, so no sequence hole and no
 * event without its transaction can appear.
 *
 * <p>Ambiguous commit resolution: the transaction carries a token derived from environment-bound
 * dedupe identity plus the sequence, and {@link #find} is a strongly consistent lookup by that
 * identity. A caller that cannot establish whether a commit happened must call {@code find} (and
 * treat "unknown" as UNAVAILABLE) rather than resend. Replay never allocates a new sequence and a
 * changed payload for the same identity is reported as {@code CONTENT_CONFLICT} without overwrite.
 *
 * <p>An integrity conflict is also marked durably on the existing dedupe row (observation contract
 * O1a/O2a: "changed immutable content quarantined as integrity conflict"; OBS-14; R16). The marker
 * is technical metadata: it stores the first conflicting content hash, never a second payload, and
 * never advances the source sequence, HEAD, critical slots or business outcome. A repeated conflict
 * therefore writes the same deterministic marker and consumes no sequence or slot.
 *
 * <p>The dedupe row also retains the first authorized {@code collectedAt} for the identity. A
 * strongly consistent replay therefore returns the original collection time, not the redelivery
 * time, and a restart or read cannot reset it (O1a/O2a/O8). That attribute is the only collection
 * metadata added here; the canonical payload is still stored once, on the event row.
 *
 * <p>Out of scope here: slot capacity enforcement, retention/expiry, Streams, query cursors and
 * notification. Capacity is injected only as manifest configuration, never invented as a default.
 */
public final class DynamoEventStore implements EventStore {

    private static final int MAX_HEAD_ATTEMPTS = 8;

    private final DynamoDbClient db;
    private final String table;
    private final EventManifest manifest;
    private final EventValidator validator;
    private final Clock clock;
    private final CriticalWriteComposer composer;
    private final List<EventCommitObserver> commitObservers = new CopyOnWriteArrayList<>();

    public DynamoEventStore(DynamoDbClient db, String table, EventManifest manifest) {
        this(db, table, manifest, new EventValidator(), Clock.systemUTC(), CriticalWriteComposer.NONE);
    }

    public DynamoEventStore(DynamoDbClient db, String table, EventManifest manifest,
            EventValidator validator, Clock clock, CriticalWriteComposer composer) {
        this.db = Objects.requireNonNull(db);
        this.table = Objects.requireNonNull(table);
        this.manifest = Objects.requireNonNull(manifest);
        this.validator = Objects.requireNonNull(validator);
        this.clock = Objects.requireNonNull(clock);
        this.composer = Objects.requireNonNull(composer);
    }

    @Override
    public EventAppendResult append(EventSubmission submission) {
        return append(submission, composer);
    }

    /**
     * Register a best-effort post-commit observer. It is called only after a new event row commits
     * and cannot alter the append outcome; a throwing observer is contained so notification can
     * never fail a payment transition (observation contract O1/O7).
     */
    public void addCommitObserver(EventCommitObserver observer) {
        commitObservers.add(Objects.requireNonNull(observer, "observer"));
    }

    /**
     * Append with a one-shot composed-write contributor, used by the atomic claim path so the
     * business record, business head and slot reservation join THIS transaction. A replay still
     * returns before composition, so a redelivered claim cannot rewrite business rows or reserve a
     * second slot.
     */
    public EventAppendResult append(EventSubmission submission, CriticalWriteComposer oneShot) {
        return appendInternal(submission, Objects.requireNonNull(oneShot, "oneShot"));
    }

    private EventAppendResult appendInternal(EventSubmission submission,
            CriticalWriteComposer composer) {
        Objects.requireNonNull(submission, "submission");
        validator.validate(submission.event(), manifest);
        requireCollectorContext(submission);

        String incomingSha = CanonicalEventCodec.sha256(submission.event());
        EventDedupeKey dedupeKey = submission.dedupeKey();

        for (int attempt = 0; attempt < MAX_HEAD_ATTEMPTS; attempt++) {
            Optional<StoredEvent> existing = find(dedupeKey);
            if (existing.isPresent()) {
                return classify(dedupeKey, existing.get(), incomingSha);
            }

            Optional<EventHead> head = head(submission.context());
            long sequence = head.map(h -> h.lastSequence() + 1).orElse(1L);
            EventSourceWrapper wrapper = submission.wrapper(sequence, incomingSha);
            validator.validateWrapper(wrapper, submission.event());

            List<TransactWriteItem> composed = composer.compose(submission, sequence, wrapper);
            if (composed == null) {
                composed = List.of();
            }
            if (submission.deliveryClass() != EventDurability.CRITICAL_COMMITTED
                    && !composed.isEmpty()) {
                throw new IllegalStateException("business composition requires a critical event");
            }

            List<TransactWriteItem> items = new ArrayList<>(3 + composed.size());
            items.add(TransactWriteItem.builder()
                    .put(eventPut(submission, wrapper, incomingSha)).build());
            items.add(TransactWriteItem.builder()
                    .put(dedupePut(submission, dedupeKey, incomingSha, sequence)).build());
            items.add(TransactWriteItem.builder()
                    .put(headPut(submission.context(), head, sequence, submission.collectedAt()))
                    .build());
            items.addAll(composed);

            try {
                db.transactWriteItems(TransactWriteItemsRequest.builder()
                        .clientRequestToken(commitToken(dedupeKey, sequence))
                        .transactItems(items)
                        .build());
                notifyCommitted(submission.context(), sequence);
                return EventAppendResult.stored(sequence, incomingSha);
            } catch (TransactionCanceledException e) {
                if (!eventTableRace(e)) {
                    // A composed business condition (or throttling) failed: the event must not exist.
                    throw e;
                }
                // Event-table CAS lost a race: re-read dedupe/HEAD and retry.
            }
        }
        throw new IllegalStateException("event head contention exhausted");
    }

    @Override
    public Optional<StoredEvent> find(EventDedupeKey dedupeKey) {
        var item = db.getItem(GetItemRequest.builder()
                .tableName(table)
                .key(Map.of("pk", text(EventTableKeys.dedupePartition(dedupeKey.environment())),
                        "sk", text(EventTableKeys.dedupeSortKey(dedupeKey))))
                .consistentRead(true)
                .build()).item();
        if (item == null || item.isEmpty()) {
            return Optional.empty();
        }
        EventContext context = new EventContext(
                string(item, "environment"),
                string(item, "acquirerRef"),
                string(item, "merchantRef"),
                string(item, "transactionId"));
        return Optional.of(new StoredEvent(
                dedupeKey,
                context,
                number(item, "sourceSequence"),
                string(item, "eventSha256"),
                string(item, "storedInstanceId"),
                EventDurability.valueOf(string(item, "deliveryClass")),
                optionalString(item, "stateAfter"),
                optionalNumber(item, "journalVersion"),
                string(item, "status"),
                integrityConflict(item),
                optionalString(item, "collectedAt")));
    }

    /** Absolute HEAD read; absent means no event was ever committed for this partition. */
    @Override
    public Optional<EventHead> head(EventContext context) {
        var item = db.getItem(GetItemRequest.builder()
                .tableName(table)
                .key(Map.of("pk", text(EventTableKeys.eventPartition(context)),
                        "sk", text(EventTableKeys.headSortKey())))
                .consistentRead(true)
                .build()).item();
        if (item == null || item.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new EventHead(
                context,
                number(item, "lastSequence"),
                number(item, "firstRetainedSequence"),
                number(item, "expiryRevision"),
                optionalString(item, "latestKnownBusinessVersion"),
                optionalString(item, "lastCollectedAt")));
    }

    /** Producer/context authorization happens BEFORE dedupe; the collector never reassigns context. */
    private void requireCollectorContext(EventSubmission submission) {        EventContext context = submission.context();
        CanonicalEvent event = submission.event();
        if (!context.acquirerRef().equals(event.acquirerRef())
                || !context.merchantRef().equals(event.merchantRef())
                || !context.transactionId().equals(event.transactionId())) {
            throw new EventValidationException(EventViolation.CONTEXT_MISMATCH, "context",
                    "event context does not match the collector partition");
        }
    }

    private EventAppendResult classify(EventDedupeKey dedupeKey, StoredEvent stored,
            String incomingSha) {
        EventDeduplication.Decision decision =
                EventDeduplication.classify(stored.eventSha256(), incomingSha);
        return switch (decision) {
            case EXACT_REPLAY -> EventAppendResult.replay(stored.sourceSequence(), incomingSha,
                    stored.eventSha256());
            // A stored identity without a hash is treated as an integrity conflict: never overwrite.
            case NEW, CONTENT_CONFLICT -> {
                recordIntegrityConflict(dedupeKey, stored, incomingSha);
                yield EventAppendResult.conflict(stored.sourceSequence(), incomingSha,
                        stored.eventSha256());
            }
        };
    }

    /**
     * Durably flag the existing dedupe row as an integrity conflict without touching any event,
     * HEAD, capacity or business row. {@code if_not_exists} makes the marker idempotent and keeps
     * the first conflicting witness; the row condition pins the exact stored identity that was read.
     */
    private void recordIntegrityConflict(EventDedupeKey dedupeKey, StoredEvent stored,
            String incomingSha) {
        Map<String, AttributeValue> key = Map.of(
                "pk", text(EventTableKeys.dedupePartition(dedupeKey.environment())),
                "sk", text(EventTableKeys.dedupeSortKey(dedupeKey)));
        try {
            db.updateItem(UpdateItemRequest.builder()
                    .tableName(table)
                    .key(key)
                    .conditionExpression("attribute_exists(pk) AND eventSha256 = :storedSha")
                    .updateExpression("SET integrityStatus = if_not_exists(integrityStatus, :status), "
                            + "integrityConflictSha256 = if_not_exists(integrityConflictSha256, :sha)")
                    .expressionAttributeValues(Map.of(
                            ":storedSha", text(stored.eventSha256()),
                            ":status", text(EventIntegrityConflict.INTEGRITY_CONFLICT),
                            ":sha", text(incomingSha)))
                    .build());
        } catch (ConditionalCheckFailedException alreadyMarkedOrChanged) {
            // The immutable identity no longer matches the row that was read (concurrent expiry or
            // deletion). Never overwrite, never allocate, and never turn a conflict into a write.
        }
    }

    /** Reads the durable quarantine marker; legacy rows without it return {@code null}. */
    private static EventIntegrityConflict integrityConflict(Map<String, AttributeValue> item) {
        String status = optionalString(item, "integrityStatus");
        if (status == null) {
            return null;
        }
        if (!EventIntegrityConflict.INTEGRITY_CONFLICT.equals(status)) {
            throw new IllegalStateException("unknown integrity marker state");
        }
        String conflictingSha = optionalString(item, "integrityConflictSha256");
        if (conflictingSha == null) {
            throw new IllegalStateException("integrity marker is missing its conflict hash");
        }
        return new EventIntegrityConflict(conflictingSha);
    }

    private Put eventPut(EventSubmission submission, EventSourceWrapper wrapper, String sha) {
        CanonicalEvent event = submission.event();
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("pk", text(EventTableKeys.eventPartition(submission.context())));
        item.put("sk", text(EventTableKeys.eventSortKey(wrapper.sourceSequence())));
        item.put("payload", text(CanonicalEventCodec.canonicalJson(event)));
        item.put("eventSha256", text(sha));
        item.put("deliveryClass", text(submission.deliveryClass().name()));
        item.put("sourceSequence", number(wrapper.sourceSequence()));
        item.put("producerId", text(event.producerId()));
        item.put("eventId", text(event.eventId()));
        item.put("storedInstanceId", text(event.instanceId()));
        item.put("collectedAt", text(submission.collectedAt()));
        item.put("storedAt", text(submission.storedAt()));
        if (submission.journalVersion() != null) {
            item.put("journalVersion", number(submission.journalVersion()));
        }
        if (submission.committedAt() != null) {
            item.put("committedAt", text(submission.committedAt()));
        }
        if (submission.stateAfter() != null) {
            item.put("stateAfter", text(submission.stateAfter()));
        }
        return Put.builder().tableName(table).item(item)
                .conditionExpression("attribute_not_exists(pk)")
                .build();
    }

    private Put dedupePut(EventSubmission submission, EventDedupeKey key, String sha,
            long sequence) {
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("pk", text(EventTableKeys.dedupePartition(key.environment())));
        item.put("sk", text(EventTableKeys.dedupeSortKey(key)));
        item.put("status", text(StoredEvent.ACTIVE));
        item.put("eventSha256", text(sha));
        item.put("sourceSequence", number(sequence));
        item.put("producerId", text(key.producerId()));
        item.put("eventId", text(key.eventId()));
        item.put("environment", text(key.environment()));
        item.put("acquirerRef", text(submission.context().acquirerRef()));
        item.put("merchantRef", text(submission.context().merchantRef()));
        item.put("transactionId", text(submission.context().transactionId()));
        item.put("storedInstanceId", text(submission.event().instanceId()));
        item.put("deliveryClass", text(submission.deliveryClass().name()));
        item.put("storedAt", text(submission.storedAt()));
        // First authorized collection time, kept with the identity metadata and never rewritten by a
        // later exact replay/conflict. Critical events carry collectedAt == committedAt (enforced by
        // wrapper admission); best-effort events keep the original collection time across
        // redelivery/restart/read (observation contract O1a/O2a/O8). This is metadata, not a copy of
        // the canonical payload.
        item.put("collectedAt", text(submission.collectedAt()));
        if (submission.journalVersion() != null) {
            item.put("journalVersion", number(submission.journalVersion()));
        }
        if (submission.stateAfter() != null) {
            item.put("stateAfter", text(submission.stateAfter()));
        }
        return Put.builder().tableName(table).item(item)
                .conditionExpression("attribute_not_exists(pk)")
                .build();
    }

    private Put headPut(EventContext context, Optional<EventHead> head, long sequence,
            String collectedAt) {
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("pk", text(EventTableKeys.eventPartition(context)));
        item.put("sk", text(EventTableKeys.headSortKey()));
        item.put("lastSequence", number(sequence));
        item.put("firstRetainedSequence",
                number(head.map(EventHead::firstRetainedSequence).orElse(1L)));
        item.put("expiryRevision", number(head.map(EventHead::expiryRevision).orElse(0L)));
        item.put("lastCollectedAt", text(collectedAt));
        item.put("updatedAt", text(clock.instant().toString()));
        head.flatMap(h -> Optional.ofNullable(h.latestKnownBusinessVersion()))
                .ifPresent(v -> item.put("latestKnownBusinessVersion", text(v)));

        Put.Builder put = Put.builder().tableName(table).item(item);
        if (head.isEmpty()) {
            put.conditionExpression("attribute_not_exists(pk)");
        } else {
            put.conditionExpression("lastSequence = :expected")
                    .expressionAttributeValues(Map.of(":expected",
                            number(head.get().lastSequence())));
        }
        return put.build();
    }

    /**
     * True only when the failed conditions belong to the three event-table writes. A composed
     * business condition failure is never retried here, so the payment path sees the real abort.
     */
    private static boolean eventTableRace(TransactionCanceledException e) {
        List<CancellationReason> reasons = e.cancellationReasons();
        if (reasons == null || reasons.isEmpty()) {
            return false;
        }
        for (int i = 0; i < reasons.size(); i++) {
            String code = reasons.get(i).code();
            if (code == null || "None".equals(code)) {
                continue;
            }
            if (i >= 3 || !"ConditionalCheckFailed".equals(code)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Deterministic transaction token: same table + identity + sequence retries the same
     * transaction once, so an ambiguous commit can be resolved by {@link #find}. The table name is
     * part of the namespace because DynamoDB idempotency is account/region scoped; two event tables
     * must never share a token for different items.
     */
    private String commitToken(EventDedupeKey key, long sequence) {
        String material = table + "|" + key.environment() + "|" + key.producerId() + "|"
                + key.eventId() + "|" + sequence;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String hex = HexFormat.of().formatHex(
                    digest.digest(material.getBytes(StandardCharsets.UTF_8)));
            return hex.substring(0, 32);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    /**
     * Notify registered observers of a genuinely new commit. Observers are best-effort: an exception
     * is contained here and can never change the committed result or the caller's response.
     */
    private void notifyCommitted(EventContext context, long sequence) {
        for (EventCommitObserver observer : commitObservers) {
            try {
                observer.onCommitted(context, sequence);
            } catch (RuntimeException contained) {
                // Notification loss is an explicit gap, never a payment failure.
            }
        }
    }

    private static AttributeValue text(String value) {
        return AttributeValue.builder().s(value).build();
    }    private static AttributeValue number(long value) {
        return AttributeValue.builder().n(Long.toString(value)).build();
    }

    private static String string(Map<String, AttributeValue> item, String field) {
        AttributeValue value = item.get(field);
        if (value == null || value.s() == null) {
            throw new IllegalStateException("event table row is missing " + field);
        }
        return value.s();
    }

    private static String optionalString(Map<String, AttributeValue> item, String field) {
        AttributeValue value = item.get(field);
        return value == null ? null : value.s();
    }

    private static Long optionalNumber(Map<String, AttributeValue> item, String field) {
        AttributeValue value = item.get(field);
        return value == null || value.n() == null ? null : Long.parseLong(value.n());
    }

    private static long number(Map<String, AttributeValue> item, String field) {
        AttributeValue value = item.get(field);
        if (value == null || value.n() == null) {
            throw new IllegalStateException("event table row is missing " + field);
        }
        return Long.parseLong(value.n());
    }
}
