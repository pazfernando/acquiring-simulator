package com.acquiring.simulator.adapter.aws;

import com.acquiring.simulator.domain.event.CommittedVersionEvidence;
import com.acquiring.simulator.domain.event.LateEvidenceReceipt;
import com.acquiring.simulator.domain.event.ProcessorEvidenceReceipt;
import com.acquiring.simulator.domain.event.SignedEvidenceConflict;
import com.acquiring.simulator.domain.model.*;
import com.acquiring.simulator.domain.port.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import java.util.function.UnaryOperator;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

/** Single-table journal. Strong reads and transactional claim/head prevent double dispatch. */
public final class DynamoAuthorizationRepository implements AuthorizationRepository {
    private final DynamoDbClient db;
    private final String table;
    public DynamoAuthorizationRepository(DynamoDbClient db, String table) {
        this.db = db;
        this.table = Objects.requireNonNull(table);
    }
    public static AttributeValue text(String s) { return AttributeValue.builder().s(s).build(); }
    public static Map<String, AttributeValue> key(String pk, String sk) {
        return Map.of("pk", text(pk), "sk", text(sk));
    }
    /** Authorization record key; shared with the atomic-claim composer so the scheme cannot drift. */
    static Map<String, AttributeValue> recordKey(String authorizationId) {
        return key("AUTH#" + authorizationId, "META");
    }
    /** Business head key; shared with the atomic-claim composer so the scheme cannot drift. */
    static Map<String, AttributeValue> businessHeadKey(String businessKey) {
        return key("BIZ#" + digest(businessKey), "HEAD");
    }
    /** Storage CAS counter for a newly claimed record. */
    static final long INITIAL_VERSION = 0L;
    /**
     * Additive public committed journal version attribute. It is persisted alongside, and never
     * derived from, the internal CAS {@code version}; existing rows are not backfilled.
     */
    static final String COMMITTED_JOURNAL_VERSION_ATTRIBUTE = "committedJournalVersion";

    /** Add the public committed version to a business item without touching the CAS attribute. */
    static void putCommittedJournalVersion(Map<String, AttributeValue> item,
            long committedJournalVersion) {
        item.put(COMMITTED_JOURNAL_VERSION_ATTRIBUTE, AttributeValue.builder()
                .n(Long.toString(committedJournalVersion)).build());
    }
    private Map<String, AttributeValue> item(String pk, String sk, Object payload) {
        var item = new HashMap<>(key(pk, sk));
        item.put("payload", text(WireJson.write(payload)));
        return item;
    }
    private Optional<Map<String, AttributeValue>> get(String pk, String sk) {
        var item = db.getItem(GetItemRequest.builder().tableName(table).key(key(pk, sk))
                .consistentRead(true).build()).item();
        return item == null || item.isEmpty() ? Optional.empty() : Optional.of(item);
    }
    @Override public void claim(AuthorizationRecord r) {
        var record = new HashMap<>(recordKey(r.id()));
        record.put("payload", text(WireJson.write(r)));
        record.put("version", AttributeValue.builder().n(Long.toString(INITIAL_VERSION)).build());
        var head = new HashMap<>(businessHeadKey(r.businessKey()));
        head.put("id", text(r.id()));
        head.put("generation", AttributeValue.builder().n(Integer.toString(r.generation())).build());
        var headPut = Put.builder().tableName(table).item(head)
                .conditionExpression(r.generation() == 1 ? "attribute_not_exists(pk)" : "generation = :previous");
        if (r.generation() > 1) headPut.expressionAttributeValues(Map.of(":previous",
                AttributeValue.builder().n(Integer.toString(r.generation() - 1)).build()));
        try {
            db.transactWriteItems(TransactWriteItemsRequest.builder()
                    .clientRequestToken(r.id())
                    .transactItems(
                        TransactWriteItem.builder().put(Put.builder().tableName(table).item(record)
                                .conditionExpression("attribute_not_exists(pk)").build()).build(),
                        TransactWriteItem.builder().put(headPut.build()).build()).build());
        } catch (TransactionCanceledException e) {
            if (e.cancellationReasons().stream().anyMatch(c -> "ConditionalCheckFailed".equals(c.code()))) {
                throw new DuplicateClaimException(r.businessKey(), r.generation(), e);
            }
            throw e; // throttling, authorization and outage are NOT duplicate claims
        }
    }
    @Override public void markDispatched(String id, String correlation, Instant at) {
        mutate(id, r -> {
            if (r.dispatchedAt() != null) throw new IllegalStateException("already dispatched");
            return r.withDispatch(correlation, at);
        });
    }
    @Override public void updateOutcome(String id, String state, String source, String reason, Instant at) {
        mutate(id, r -> {
            if (r.state() == AuthorizationState.APPROVED || r.state() == AuthorizationState.DECLINED
                    || r.state() == AuthorizationState.NETWORK_REJECTED) return r;
            return r.withOutcome(AuthorizationState.valueOf(state), DecisionSource.valueOf(source), reason, at);
        });
    }
    private void mutate(String id, UnaryOperator<AuthorizationRecord> change) {
        for (int attempt = 0; attempt < 8; attempt++) {
            var old = get("AUTH#" + id, "META").orElseThrow(() -> new IllegalArgumentException("unknown reference"));
            var r = WireJson.read(old.get("payload").s(), AuthorizationRecord.class);
            var changed = change.apply(r);
            if (changed.equals(r)) return;
            long version = Long.parseLong(old.get("version").n());
            var next = item("AUTH#" + id, "META", changed);
            next.put("version", AttributeValue.builder().n(Long.toString(version + 1)).build());
            // A replacement must never silently drop the persisted committed journal version.
            // This copy-forward preserves the recorded evidence only; advancing it belongs to the
            // future business-transition slice and is not implied here.
            AttributeValue committed = old.get(COMMITTED_JOURNAL_VERSION_ATTRIBUTE);
            if (committed != null) {
                next.put(COMMITTED_JOURNAL_VERSION_ATTRIBUTE, committed);
            }
            try {
                db.putItem(PutItemRequest.builder().tableName(table).item(next)
                        .conditionExpression("#v = :v").expressionAttributeNames(Map.of("#v", "version"))
                        .expressionAttributeValues(Map.of(":v", AttributeValue.builder().n(Long.toString(version)).build()))
                        .build());
                return;
            } catch (ConditionalCheckFailedException contention) { /* reread, never blind overwrite */ }
        }
        throw new IllegalStateException("journal contention exhausted");
    }
    @Override public Optional<AuthorizationRecord> findById(String id) {
        return get("AUTH#" + id, "META").map(i -> WireJson.read(i.get("payload").s(), AuthorizationRecord.class));
    }
    /**
     * Read both version facts of a claim record. A legacy row (written without the additive
     * attribute) reports {@code committedJournalVersion == null}: the absence is explicit and is
     * never replaced by {@code storageVersion + 1}.
     */
    public CommittedVersionEvidence readVersionEvidence(String id) {
        var item = get("AUTH#" + id, "META")
                .orElseThrow(() -> new IllegalArgumentException("unknown reference"));
        long storageVersion = Long.parseLong(item.get("version").n());
        AttributeValue committed = item.get(COMMITTED_JOURNAL_VERSION_ATTRIBUTE);
        Long committedJournalVersion = committed == null || committed.n() == null
                ? null : Long.parseLong(committed.n());
        return new CommittedVersionEvidence(storageVersion, committedJournalVersion);
    }
    @Override public Optional<AuthorizationRecord> findLatestByBusinessKey(String businessKey) {
        return get("BIZ#" + digest(businessKey), "HEAD").flatMap(i -> findById(i.get("id").s()));
    }
    /**
     * Read the additive late-evidence receipt of a journal row, if it was committed as late
     * evidence. Absence is explicit: a confirmed row that was not committed as late evidence
     * returns empty and is never backfilled or inferred from the confirmed state.
     */
    public Optional<LateEvidenceReceipt> readLateEvidenceReceipt(String id) {
        var item = get("AUTH#" + id, "META")
                .orElseThrow(() -> new IllegalArgumentException("unknown reference"));
        AttributeValue late = item.get(LateEvidenceCriticalComposer.LATE_FLAG_ATTRIBUTE);
        if (late == null || !Boolean.TRUE.equals(late.bool())) {
            return Optional.empty();
        }
        return Optional.of(new LateEvidenceReceipt(
                requiredString(item, LateEvidenceCriticalComposer.RECEIPT_ID_ATTRIBUTE),
                Instant.parse(requiredString(item,
                        LateEvidenceCriticalComposer.EVIDENCE_AT_ATTRIBUTE)),
                Instant.parse(requiredString(item,
                        LateEvidenceCriticalComposer.DELIVERED_AT_ATTRIBUTE)),
                Instant.parse(requiredString(item,
                        LateEvidenceCriticalComposer.DEADLINE_ATTRIBUTE)),
                true));
    }
    /**
     * Read the durable processor receipt for one evidence identity, if the normal verified result
     * was admitted atomically. Absence is explicit and is never inferred from a confirmed state.
     */
    public Optional<ProcessorEvidenceReceipt> readEvidenceReceipt(String environmentId,
            String attestingPrincipal, String evidenceId) {
        Map<String, AttributeValue> key = EvidenceReceiptCriticalComposer.receiptKey(
                environmentId, attestingPrincipal, evidenceId);
        return get(key.get("pk").s(), key.get("sk").s()).map(item ->
                new ProcessorEvidenceReceipt(
                        requiredString(item, "environment"),
                        requiredString(item, "attestingPrincipal"),
                        requiredString(item, "evidenceId"),
                        requiredString(item, "canonicalAttestationSha256"),
                        requiredString(item, "instructionSha256"),
                        requiredString(item, "responseSha256"),
                        requiredString(item, "acquirerRef"),
                        requiredString(item, "authorizationId"),
                        requiredString(item, "transactionId"),
                        requiredString(item, "keyId"),
                        requiredString(item, "attestationType"),
                        requiredString(item, "eventId"),
                        requiredNumber(item, "journalVersion"),
                        requiredString(item, "stateAfter"),
                        Instant.parse(requiredString(item, "evidenceAvailableAt"))));
    }
    /**
     * Journal key of the one quarantine marker for a signed-evidence identity. It is derived from
     * the same immutable triple as the receipt but kept in a distinct partition, so quarantine
     * metadata can never be mistaken for, or overwrite, the original receipt.
     */
    static Map<String, AttributeValue> evidenceConflictKey(String environmentId,
            String attestingPrincipal, String evidenceId) {
        return key(SignedEvidenceConflict.PARTITION_PREFIX + environmentId + "#"
                + attestingPrincipal + "#" + evidenceId, SignedEvidenceConflict.SORT_KEY);
    }
    /**
     * Durably quarantine one signed-evidence conflict. The conditional put keeps the first
     * conflicting witness: a repeated conflict for the same identity is a silent no-op that never
     * advances the business version, source sequence, critical slot or the original receipt.
     * Metadata only — no raw proof, signature, second payload or secret is written.
     */
    public void recordEvidenceConflict(SignedEvidenceConflict conflict) {
        Objects.requireNonNull(conflict, "conflict");
        Map<String, AttributeValue> key = evidenceConflictKey(conflict.environmentId(),
                conflict.attestingPrincipal(), conflict.evidenceId());
        Map<String, AttributeValue> item = new HashMap<>(key);
        item.put("conflictType", text(conflict.type().name()));
        item.put("environment", text(conflict.environmentId()));
        item.put("attestingPrincipal", text(conflict.attestingPrincipal()));
        item.put("evidenceId", text(conflict.evidenceId()));
        item.put("canonicalAttestationSha256",
                text(conflict.canonicalAttestationSha256()));
        if (conflict.establishedCanonicalAttestationSha256() != null) {
            item.put("establishedCanonicalAttestationSha256",
                    text(conflict.establishedCanonicalAttestationSha256()));
        }
        item.put("establishedAuthorizationId", text(conflict.establishedAuthorizationId()));
        item.put("establishedAcquirerRef", text(conflict.establishedAcquirerRef()));
        item.put("reason", text(conflict.reason()));
        try {
            db.putItem(PutItemRequest.builder().tableName(table).item(item)
                    .conditionExpression("attribute_not_exists(pk)").build());
        } catch (ConditionalCheckFailedException alreadyMarkedOrChanged) {
            // The first conflicting witness is retained; never overwrite and never fail the outcome.
        }
    }
    /** Read the durable quarantine marker of an evidence identity, if one was recorded. */
    public Optional<SignedEvidenceConflict> readEvidenceConflict(String environmentId,
            String attestingPrincipal, String evidenceId) {
        Map<String, AttributeValue> key = evidenceConflictKey(
                environmentId, attestingPrincipal, evidenceId);
        return get(key.get("pk").s(), key.get("sk").s()).map(item ->
                new SignedEvidenceConflict(
                        SignedEvidenceConflict.Type.valueOf(
                                requiredString(item, "conflictType")),
                        requiredString(item, "environment"),
                        requiredString(item, "attestingPrincipal"),
                        requiredString(item, "evidenceId"),
                        requiredString(item, "canonicalAttestationSha256"),
                        optionalString(item, "establishedCanonicalAttestationSha256"),
                        requiredString(item, "establishedAuthorizationId"),
                        requiredString(item, "establishedAcquirerRef"),
                        requiredString(item, "reason")));
    }
    /** Numeric attributes are read as numbers; a CAS-style counter is never parsed from a string. */
    private static long requiredNumber(Map<String, AttributeValue> item, String field) {
        AttributeValue value = item.get(field);
        if (value == null || value.n() == null) {
            throw new IllegalStateException("journal row is missing " + field);
        }
        return Long.parseLong(value.n());
    }
    private static String requiredString(Map<String, AttributeValue> item, String field) {
        AttributeValue value = item.get(field);
        if (value == null || value.s() == null) {
            throw new IllegalStateException("journal row is missing " + field);
        }
        return value.s();
    }
    private static String optionalString(Map<String, AttributeValue> item, String field) {
        AttributeValue value = item.get(field);
        return value == null || value.s() == null ? null : value.s();
    }
    @Override public List<EvidenceEvent> eventsFor(String id) {
        var events = new ArrayList<EvidenceEvent>();
        Map<String, AttributeValue> cursor = Map.of();
        do {
            var query = QueryRequest.builder().tableName(table).consistentRead(true)
                    .keyConditionExpression("pk = :pk AND begins_with(sk, :prefix)")
                    .expressionAttributeValues(Map.of(":pk", text("AUTH#" + id), ":prefix", text("EVT#")))
                    ;
            if (cursor != null && !cursor.isEmpty()) query.exclusiveStartKey(cursor);
            var page = db.query(query.build());
            page.items().forEach(i -> events.add(WireJson.read(i.get("payload").s(), EvidenceEvent.class)));
            cursor = page.lastEvaluatedKey();
        } while (cursor != null && !cursor.isEmpty());
        return List.copyOf(events);
    }
    @Override public void appendEvent(EvidenceEvent e) {
        append(item("AUTH#" + e.authorizationId(), "EVT#" + String.format(Locale.ROOT, "%013d", e.recordedAt()) + "#" + e.id(), e));
    }
    @Override public void appendRejection(RejectionAudit r) { append(item("REJ#" + r.id(), "META", r)); }
    private void append(Map<String, AttributeValue> item) {
        db.putItem(PutItemRequest.builder().tableName(table).item(item)
                .conditionExpression("attribute_not_exists(pk)").build());
    }
    private static String digest(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
