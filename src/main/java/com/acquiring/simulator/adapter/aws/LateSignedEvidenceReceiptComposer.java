package com.acquiring.simulator.adapter.aws;

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
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItem;

/**
 * Atomic late verified-evidence receipt composition (observation contract O3/P5; channel contract
 * C4/P6, late-delivery paragraph; R07; OBS-08/OBS-10).
 *
 * <p>This is a thin composition wrapper, not a second late path. It delegates the confirmed business
 * record, its explicitly advanced committed journal version, the late receipt metadata, the
 * immutable critical result event, the EVENT HEAD, dedupe metadata and the optional critical slot to
 * the existing {@link LateEvidenceCriticalComposer}, and appends the normalized processor evidence
 * receipt from the existing {@link EvidenceReceiptCriticalComposer}. Both writes still travel in
 * ONE {@code TransactWriteItems} call: a failed condition commits nothing.</p>
 *
 * <p>Evidence is accepted only from the real verifier/binder chain: a
 * {@link C4VerifiedAttestation} produced by {@code C4EvidenceVerifier}, the exact signed
 * {@link C4EvidenceAttestation} content, and a {@link C4P6BindingDecision} produced by
 * {@code C4P6SignedFieldBinder}. No caller-supplied verified/bound flag, signature boolean or
 * arbitrary trusted-evidence DTO is accepted. The signed claims, the verified identity, the bound
 * decision and the persisted original projection must agree before composition exists.</p>
 *
 * <p>The late distinction stays outside the canonical event: the record carries the delivery
 * receipt id, evidence time, delivery time and deadline, while the canonical event remains the
 * approved {@code BUSINESS_PERSIST}/{@code SUCCEEDED}/{@code VALID} transition at journal version 3.
 * An exact redelivery is classified as {@code EXACT_REPLAY} by the store before any write, so the
 * receipt, committed version, event, HEAD and slot are preserved.</p>
 *
 * <p>Deliberately absent: HTTP/service caller, runtime wiring, key custody, conflict/replay policy
 * beyond the conditional guards, and any downstream call or resend. This class is not referenced by
 * {@code AuthorizationService} or {@code AwsRuntime}, so it is not production-wired.</p>
 */
public final class LateSignedEvidenceReceiptComposer implements CriticalWriteComposer {

    private final LateEvidenceCriticalComposer lateComposer;
    private final ProcessorEvidenceReceipt receipt;
    private final String journalTable;

    private LateSignedEvidenceReceiptComposer(String journalTable, String eventTable,
            AuthorizationRecord intent, C4VerifiedAttestation verified,
            C4EvidenceAttestation signed, C4P6BindingDecision decision, Instant deadline,
            Instant deliveredAt, String deliveryReceiptId, CriticalCapacity capacity) {
        this.journalTable = requireText(journalTable, "journalTable");
        String event = requireText(eventTable, "eventTable");
        AuthorizationRecord record = Objects.requireNonNull(intent, "intent");
        C4VerifiedAttestation verifiedEvidence = Objects.requireNonNull(verified, "verified");
        C4EvidenceAttestation signedEvidence = Objects.requireNonNull(signed, "signed");
        C4P6BindingDecision boundDecision = Objects.requireNonNull(decision, "decision");
        Instant evidenceDeadline = Objects.requireNonNull(deadline, "deadline");
        Instant evidenceDeliveredAt = Objects.requireNonNull(deliveredAt, "deliveredAt");
        String receiptId = requireText(deliveryReceiptId, "deliveryReceiptId");

        this.receipt = EvidenceReceiptCriticalComposer.signedReceipt(record, verifiedEvidence,
                signedEvidence, boundDecision);

        IssuerDecision issuerDecision = issuerDecision(boundDecision.result());
        DownstreamReply reply = new DownstreamReply(signedEvidence.correlationId(), issuerDecision,
                Instant.parse(signedEvidence.availableAt()));
        this.lateComposer = capacity == null
                ? LateEvidenceCriticalComposer.withoutSlotAccounting(this.journalTable, event,
                        record, reply, evidenceDeadline, evidenceDeliveredAt, receiptId)
                : LateEvidenceCriticalComposer.withSlotAccounting(this.journalTable, event,
                        record, reply, evidenceDeadline, evidenceDeliveredAt, receiptId, capacity);
    }

    /** Late signed receipt without slot accounting; no capacity value is implied. */
    public static LateSignedEvidenceReceiptComposer withoutSlotAccounting(String journalTable,
            String eventTable, AuthorizationRecord intent, C4VerifiedAttestation verified,
            C4EvidenceAttestation signed, C4P6BindingDecision decision, Instant deadline,
            Instant deliveredAt, String deliveryReceiptId) {
        return new LateSignedEvidenceReceiptComposer(journalTable, eventTable, intent, verified,
                signed, decision, deadline, deliveredAt, deliveryReceiptId, null);
    }

    /** Late signed receipt that reserves one critical slot against the explicit budget. */
    public static LateSignedEvidenceReceiptComposer withSlotAccounting(String journalTable,
            String eventTable, AuthorizationRecord intent, C4VerifiedAttestation verified,
            C4EvidenceAttestation signed, C4P6BindingDecision decision, Instant deadline,
            Instant deliveredAt, String deliveryReceiptId, CriticalCapacity capacity) {
        return new LateSignedEvidenceReceiptComposer(journalTable, eventTable, intent, verified,
                signed, decision, deadline, deliveredAt, deliveryReceiptId,
                Objects.requireNonNull(capacity, "capacity"));
    }

    /** Fixed late submission; identity, version, times and state come from the approved path. */
    public static EventSubmission submission(AuthorizationRecord intent, DownstreamReply evidence,
            String environment, String instanceId) {
        return LateEvidenceCriticalComposer.submission(intent, evidence, environment, instanceId);
    }

    /** Public read view of the receipt this composition writes. */
    public ProcessorEvidenceReceipt receipt() {
        return receipt;
    }

    /** Evidence timing for admission, already classified by the underlying late composer. */
    public LateEvidenceCriticalComposer.Timing timing() {
        return lateComposer.timing();
    }

    public String resultStateAfter() {
        return lateComposer.resultStateAfter();
    }

    @Override
    public List<TransactWriteItem> compose(EventSubmission submission, long sequence,
            EventSourceWrapper wrapper) {
        List<TransactWriteItem> items = new ArrayList<>(
                lateComposer.compose(submission, sequence, wrapper));
        EvidenceReceiptCriticalComposer.requireReceiptSubmission(submission, receipt);
        items.add(TransactWriteItem.builder()
                .put(EvidenceReceiptCriticalComposer.receiptPut(journalTable, receipt)).build());
        return List.copyOf(items);
    }

    private static IssuerDecision issuerDecision(String result) {
        if (!"APPROVED".equals(result) && !"DECLINED".equals(result)) {
            throw new IllegalArgumentException(
                    "late receipt requires the signed APPROVED or DECLINED result");
        }
        return IssuerDecision.valueOf(result);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value;
    }
}
