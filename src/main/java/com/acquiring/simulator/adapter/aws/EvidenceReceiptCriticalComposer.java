package com.acquiring.simulator.adapter.aws;

import com.acquiring.simulator.channel.BusinessCurrencyProjection;
import com.acquiring.simulator.channel.C4EvidenceAttestation;
import com.acquiring.simulator.channel.C4P6BindingDecision;
import com.acquiring.simulator.channel.C4VerifiedAttestation;
import com.acquiring.simulator.domain.event.CriticalCapacity;
import com.acquiring.simulator.domain.event.EventSourceWrapper;
import com.acquiring.simulator.domain.event.EventSubmission;
import com.acquiring.simulator.domain.event.ProcessorEvidenceReceipt;
import com.acquiring.simulator.domain.model.AuthorizationRecord;
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

/**
 * Atomic normal verified-result receipt composition (observation contract O3/P5, profile P5 step
 * (c)).
 *
 * <p>One {@code TransactWriteItems} call carries the confirmed business record, its explicitly
 * advanced committed journal version, the immutable critical result event, the EVENT HEAD and dedupe
 * metadata (from {@link DynamoEventStore}), the optional critical slot reservation and the
 * normalized processor evidence receipt. The receipt is never written in a separate transaction
 * before or after the business result.</p>
 *
 * <p>Evidence is accepted only from the real verifier/binder chain: a
 * {@link C4VerifiedAttestation} produced by {@code C4EvidenceVerifier}, the exact signed
 * {@link C4EvidenceAttestation} content whose canonical SHA-256 the verified result names, and a
 * {@link C4P6BindingDecision} produced by {@code C4P6SignedFieldBinder}. A caller-supplied
 * {@link DownstreamReply}, a signature/verified boolean or an arbitrary trusted-evidence DTO is not
 * accepted. The signed claims must equal the verified identity, the bound decision and the
 * persisted original record's authorization/acquirer/transaction/terminal/generation/amount/currency
 * projection; any divergence is refused before composition.</p>
 *
 * <p>Receipt identity is {@code (environment, attestingPrincipal, evidenceId)} and the canonical
 * content hash is the signed attestation hash. The receipt put is conditional on a new identity, so
 * a different content hash for an existing identity cancels the whole transaction instead of
 * overwriting the first receipt. An exact redelivery never reaches composition: the event store
 * classifies the stable result identity as {@code EXACT_REPLAY} before any write, so the receipt,
 * committed version, event, HEAD and slot are all preserved.</p>
 *
 * <p>Deliberately absent: HTTP/service caller, late-delivery envelope, duplicate/conflict policy
 * beyond the conditional identity guard, key custody/rotation, TLS/IAM principal mapping, runtime
 * wiring and any downstream call or resend. The durable conflict/quarantine marker is written by
 * {@link SignedEvidenceReceiptGateway} outside this atomic receipt transaction. This class is not
 * referenced by {@code AuthorizationService} or {@code AwsRuntime}.</p>
 */
public final class EvidenceReceiptCriticalComposer implements CriticalWriteComposer {

    /** Receipt row sort key in the journal table. */
    static final String RECEIPT_SORT_KEY = "RECEIPT";
    /** Receipt partition prefix; the identity triple follows. */
    static final String RECEIPT_PARTITION_PREFIX = "EVID#";

    private static final DateTimeFormatter UTC_SECONDS =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").withZone(ZoneOffset.UTC);

    private final String journalTable;
    private final ResultCriticalComposer resultComposer;
    private final ProcessorEvidenceReceipt receipt;

    private EvidenceReceiptCriticalComposer(String journalTable, String eventTable,
            AuthorizationRecord intent, C4VerifiedAttestation verified,
            C4EvidenceAttestation signed, C4P6BindingDecision decision, CriticalCapacity capacity) {
        String journal = requireText(journalTable, "journalTable");
        String event = requireText(eventTable, "eventTable");
        this.journalTable = journal;
        AuthorizationRecord record = Objects.requireNonNull(intent, "intent");
        C4VerifiedAttestation verifiedEvidence = Objects.requireNonNull(verified, "verified");
        C4EvidenceAttestation signedEvidence = Objects.requireNonNull(signed, "signed");
        C4P6BindingDecision boundDecision = Objects.requireNonNull(decision, "decision");

        this.receipt = signedReceipt(record, verifiedEvidence, signedEvidence, boundDecision);

        Instant availableAt = Instant.parse(signedEvidence.availableAt());
        DownstreamReply reply = new DownstreamReply(signedEvidence.correlationId(),
                issuerDecision(boundDecision.result()), availableAt);
        this.resultComposer = capacity == null
                ? ResultCriticalComposer.withoutSlotAccounting(journal, event, record, reply)
                : ResultCriticalComposer.withSlotAccounting(journal, event, record, reply, capacity);
    }

    /**
     * The one normalized processor receipt projection for admitted signed evidence. The late
     * receipt wrapper reuses it so both paths derive identity, hashes, event identity, committed
     * version and state from the same verified/bound chain.
     */
    static ProcessorEvidenceReceipt signedReceipt(AuthorizationRecord intent,
            C4VerifiedAttestation verified, C4EvidenceAttestation signed,
            C4P6BindingDecision decision) {
        requireConsistentEvidence(intent, verified, signed, decision);
        IssuerDecision issuerDecision = issuerDecision(decision.result());
        Instant availableAt = Instant.parse(signed.availableAt());
        return new ProcessorEvidenceReceipt(
                verified.environmentId(),
                verified.signingPrincipal(),
                verified.evidenceId(),
                verified.canonicalAttestationSha256(),
                verified.instructionSha256(),
                verified.responseBodySha256(),
                verified.acquirerRef(),
                verified.authorizationId(),
                verified.transactionId(),
                verified.keyId(),
                verified.attestationType().name(),
                confirmedState(issuerDecision) + "-" + intent.id(),
                ResultCriticalComposer.RESULT_JOURNAL_VERSION,
                confirmedState(issuerDecision),
                availableAt);
    }

    /** Receipt composition without slot accounting; no capacity value is implied. */
    public static EvidenceReceiptCriticalComposer withoutSlotAccounting(String journalTable,
            String eventTable, AuthorizationRecord intent, C4VerifiedAttestation verified,
            C4EvidenceAttestation signed, C4P6BindingDecision decision) {
        return new EvidenceReceiptCriticalComposer(journalTable, eventTable, intent, verified,
                signed, decision, null);
    }

    /** Receipt composition that reserves one critical slot against the explicit budget. */
    public static EvidenceReceiptCriticalComposer withSlotAccounting(String journalTable,
            String eventTable, AuthorizationRecord intent, C4VerifiedAttestation verified,
            C4EvidenceAttestation signed, C4P6BindingDecision decision, CriticalCapacity capacity) {
        return new EvidenceReceiptCriticalComposer(journalTable, eventTable, intent, verified,
                signed, decision, Objects.requireNonNull(capacity, "capacity"));
    }

    /** Journal key of one receipt identity; shared with the read seam so the scheme cannot drift. */
    static Map<String, AttributeValue> receiptKey(String environmentId, String attestingPrincipal,
            String evidenceId) {
        return Map.of(
                "pk", DynamoAuthorizationRepository.text(RECEIPT_PARTITION_PREFIX
                        + environmentId + "#" + attestingPrincipal + "#" + evidenceId),
                "sk", DynamoAuthorizationRepository.text(RECEIPT_SORT_KEY));
    }

    /** Public read view of the receipt this composition writes. */
    public ProcessorEvidenceReceipt receipt() {
        return receipt;
    }

    @Override
    public List<TransactWriteItem> compose(EventSubmission submission, long sequence,
            EventSourceWrapper wrapper) {
        requireReceiptSubmission(submission, receipt);

        List<TransactWriteItem> items = new ArrayList<>(resultComposer.compose(
                submission, sequence, wrapper));
        items.add(TransactWriteItem.builder().put(receiptPut(journalTable, receipt)).build());
        return List.copyOf(items);
    }

    /**
     * The collector context and event wrapper must describe exactly this receipt, so a submission
     * labelled with another environment, event identity, version or state cannot carry the receipt.
     */
    static void requireReceiptSubmission(EventSubmission submission,
            ProcessorEvidenceReceipt receipt) {
        if (!submission.context().environment().equals(receipt.environmentId())) {
            throw new IllegalArgumentException(
                    "receipt environment must equal the verified evidence environment");
        }
        if (!submission.event().eventId().equals(receipt.eventId())) {
            throw new IllegalArgumentException(
                    "receipt event identity must equal the confirmed result event identity");
        }
        Long journalVersion = submission.journalVersion();
        if (journalVersion == null || journalVersion != receipt.journalVersion()) {
            throw new IllegalArgumentException(
                    "receipt business committed version must equal "
                            + receipt.journalVersion());
        }
        if (!receipt.stateAfter().equals(submission.stateAfter())) {
            throw new IllegalArgumentException(
                    "receipt stateAfter must equal " + receipt.stateAfter());
        }
    }

    /** Conditional insert: one canonical content hash per identity, never an overwrite. */
    static Put receiptPut(String journalTable, ProcessorEvidenceReceipt receipt) {
        Map<String, AttributeValue> item = new HashMap<>(receiptKey(
                receipt.environmentId(), receipt.attestingPrincipal(), receipt.evidenceId()));
        item.put("environment", DynamoAuthorizationRepository.text(receipt.environmentId()));
        item.put("attestingPrincipal",
                DynamoAuthorizationRepository.text(receipt.attestingPrincipal()));
        item.put("evidenceId", DynamoAuthorizationRepository.text(receipt.evidenceId()));
        item.put("identitySha256", DynamoAuthorizationRepository.text(receipt.identitySha256()));
        item.put("canonicalAttestationSha256",
                DynamoAuthorizationRepository.text(receipt.canonicalAttestationSha256()));
        item.put("instructionSha256",
                DynamoAuthorizationRepository.text(receipt.instructionSha256()));
        item.put("responseSha256", DynamoAuthorizationRepository.text(receipt.responseSha256()));
        item.put("acquirerRef", DynamoAuthorizationRepository.text(receipt.acquirerRef()));
        item.put("authorizationId",
                DynamoAuthorizationRepository.text(receipt.authorizationId()));
        item.put("transactionId", DynamoAuthorizationRepository.text(receipt.transactionId()));
        item.put("keyId", DynamoAuthorizationRepository.text(receipt.keyId()));
        item.put("attestationType",
                DynamoAuthorizationRepository.text(receipt.attestationType()));
        item.put("eventId", DynamoAuthorizationRepository.text(receipt.eventId()));
        item.put("journalVersion", AttributeValue.builder()
                .n(Long.toString(receipt.journalVersion())).build());
        item.put("stateAfter", DynamoAuthorizationRepository.text(receipt.stateAfter()));
        item.put("evidenceAvailableAt",
                DynamoAuthorizationRepository.text(
                        UTC_SECONDS.format(receipt.evidenceAvailableAt())));
        return Put.builder().tableName(journalTable).item(item)
                .conditionExpression("attribute_not_exists(pk)")
                .build();
    }

    /**
     * Signed claims, verified identity and bound decision must describe one and the same evidence,
     * and that evidence must describe the persisted original record. Refusal happens before any
     * transaction exists.
     */
    private static void requireConsistentEvidence(AuthorizationRecord intent,
            C4VerifiedAttestation verified, C4EvidenceAttestation signed,
            C4P6BindingDecision decision) {
        requireEqual("canonicalAttestationSha256",
                verified.canonicalAttestationSha256(), signed.canonicalSha256());
        requireEqual("evidenceId", verified.evidenceId(), signed.evidenceId());
        requireEqual("evidenceId", verified.evidenceId(), decision.evidenceId());
        requireEqual("environmentId", verified.environmentId(), signed.environmentId());
        requireEqual("producerPrincipal", verified.producerPrincipal(), signed.producerPrincipal());
        requireEqual("signingPrincipal", verified.signingPrincipal(), signed.signingPrincipal());
        requireEqual("keyId", verified.keyId(), signed.keyId());
        requireEqual("instructionSha256", verified.instructionSha256(), signed.instructionSha256());
        requireEqual("responseSha256", verified.responseBodySha256(), signed.responseSha256());
        requireEqual("authorizationId", verified.authorizationId(), signed.authorizationId());
        requireEqual("authorizationId", verified.authorizationId(), decision.authorizationId());
        requireEqual("transactionId", verified.transactionId(), signed.transactionId());
        requireEqual("transactionId", verified.transactionId(), decision.transactionId());
        requireEqual("acquirerRef", verified.acquirerRef(), signed.acquirerRef());

        // The persisted original record is the authority the evidence must describe.
        requireEqual("authorizationId", decision.authorizationId(), intent.id());
        requireEqual("transactionId", decision.transactionId(), intent.channelPaymentRef());
        requireEqual("acquirerRef", verified.acquirerRef(), intent.acquirerRef());
        requireEqual("merchantRef", signed.merchantRef(), intent.merchantRef());
        requireEqual("terminalRef", signed.terminalRef(), intent.terminalRef());
        requireEqual("generation", Integer.toString(signed.generation()),
                Integer.toString(intent.generation()));
        requireEqual("amountMinor", Long.toString(decision.amountMinor()),
                Long.toString(intent.amountMinor()));
        // The persisted record may carry USD or the approved ISO 840; the signed evidence carries
        // the approved DE49 840. Compare the ISO projection of the persisted currency, never a
        // rewritten field, so the equality itself stays unchanged.
        requireEqual("currency", decision.currency(),
                BusinessCurrencyProjection.isoNumeric(intent.currency()));
        requireEqual("correlationId", signed.correlationId(), intent.dispatchCorrelationId());

        // The bound signed result tuple and the exact DE38 reference belong to the decision.
        requireEqual("DE39", decision.de39(), signed.DE39());
        requireEqual("result", decision.result(), signed.result());
        requireEqual("reason", decision.reason(), signed.reason());
        if (!Objects.equals(decision.authorizationReference(), signed.authorizationReference())) {
            throw new IllegalArgumentException(
                    "decision authorizationReference does not match the signed attestation");
        }
        if (decision.scope() != C4P6BindingDecision.BindingScope
                .SIGNED_P5_FIELDS_BOUND_TO_PERSISTED_INSTRUCTION_AND_RESPONSE) {
            throw new IllegalArgumentException("decision is not the approved signed-P5 binding");
        }
    }

    private static IssuerDecision issuerDecision(String result) {
        if (!"APPROVED".equals(result) && !"DECLINED".equals(result)) {
            throw new IllegalArgumentException(
                    "receipt requires the signed APPROVED or DECLINED result");
        }
        return IssuerDecision.valueOf(result);
    }

    private static String confirmedState(IssuerDecision decision) {
        return decision == IssuerDecision.APPROVED
                ? ResultCriticalComposer.APPROVED_STATE_AFTER
                : ResultCriticalComposer.DECLINED_STATE_AFTER;
    }

    private static void requireEqual(String field, String first, String second) {
        if (!Objects.equals(first, second)) {
            throw new IllegalArgumentException(
                    "receipt evidence " + field + " does not match");
        }
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value;
    }
}
