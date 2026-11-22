package com.acquiring.simulator.protocol.iso8583;

import java.util.Objects;

/**
 * Immutable, already-verified snapshot of one persisted original SIM-ISO87A-v1
 * authorization instruction.
 *
 * <p>This type is a trusted-record seam, not a decoder. The caller must supply
 * it from the durable instruction that was actually claimed and dispatched; it
 * must never be rebuilt from a re-encoded replay request, from the received
 * response, or from caller-supplied text. {@code originalWireSha256} is the
 * SHA-256 of the exact original {@code 0100} octets at claim time, so the
 * binding layer can compare it with the trusted evidence reference without
 * hashing any re-encoded bytes.</p>
 *
 * <p>Scope: the complete response echo this slice compares — DE48
 * identity/context/value metadata ({@code aid, tid, cid, att, acq, mid, term,
 * pay, gen, fixture, cvm, crypto, risk, sent, prior}) plus the physical
 * projection {@link Iso8583PhysicalInstruction} (DE3/4/7/11/22/25/32/37/41/42/49)
 * and the original-wire hash. The {@code v} and {@code op} literals are already
 * fixed by the canonical schema and are not repeated here.</p>
 *
 * <p>Deliberately absent and left to separate future seams: environment and
 * producer/relay principals, evidence identifier, attestation signature,
 * {@code responseSha256}, signing principal/key, transport receipt, durable
 * processor receipt and business admission. This record performs no
 * cryptographic verification and makes no trust claim.</p>
 *
 * @param originalWireSha256 SHA-256 of the exact original 0100 octets
 * @param authorizationId    original authorization ID (DE48 {@code aid})
 * @param transactionId      original transaction ID (DE48 {@code tid})
 * @param correlationId      original correlation ID (DE48 {@code cid})
 * @param instructionAttemptId original submission attempt ID (DE48 {@code att})
 * @param acquirerRef        original business acquirer reference (DE48 {@code acq})
 * @param merchantRef        original business merchant reference (DE48 {@code mid})
 * @param terminalRef        original business terminal reference (DE48 {@code term})
 * @param paymentRef         original business payment reference (DE48 {@code pay})
 * @param generation         original generation (DE48 {@code gen})
 * @param fixture            original credential fixture reference (DE48 {@code fixture})
 * @param cvm                original CVM indicator (DE48 {@code cvm})
 * @param crypto             original synthetic cryptogram-present flag (DE48 {@code crypto})
 * @param risk               original risk outcome (DE48 {@code risk})
 * @param sent               original request timestamp (DE48 {@code sent})
 * @param prior              optional authorized prior authorization ID, or {@code null}
 * @param physical           original physical data elements
 */
public record Iso8583OriginalInstruction(
        String originalWireSha256,
        String authorizationId,
        String transactionId,
        String correlationId,
        String instructionAttemptId,
        String acquirerRef,
        String merchantRef,
        String terminalRef,
        String paymentRef,
        int generation,
        String fixture,
        String cvm,
        String crypto,
        String risk,
        String sent,
        String prior,
        Iso8583PhysicalInstruction physical) {

    public Iso8583OriginalInstruction {
        Objects.requireNonNull(originalWireSha256, "originalWireSha256");
        Objects.requireNonNull(authorizationId, "authorizationId");
        Objects.requireNonNull(transactionId, "transactionId");
        Objects.requireNonNull(correlationId, "correlationId");
        Objects.requireNonNull(instructionAttemptId, "instructionAttemptId");
        Objects.requireNonNull(acquirerRef, "acquirerRef");
        Objects.requireNonNull(merchantRef, "merchantRef");
        Objects.requireNonNull(terminalRef, "terminalRef");
        Objects.requireNonNull(paymentRef, "paymentRef");
        Objects.requireNonNull(fixture, "fixture");
        Objects.requireNonNull(cvm, "cvm");
        Objects.requireNonNull(crypto, "crypto");
        Objects.requireNonNull(risk, "risk");
        Objects.requireNonNull(sent, "sent");
        Objects.requireNonNull(physical, "physical");
        if (generation < 1) {
            throw new IllegalArgumentException("generation must be positive: " + generation);
        }
    }

    /** @return {@code true} when the original request supplied a prior reference */
    public boolean hasPrior() {
        return prior != null;
    }
}
