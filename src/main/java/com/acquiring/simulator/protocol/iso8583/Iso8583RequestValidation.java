package com.acquiring.simulator.protocol.iso8583;

import java.util.Objects;

/**
 * Validated structural/domain envelope of one SIM-ISO87A-v1 {@code 0100}
 * request after the ordered codec pipeline has accepted every applicable phase.
 *
 * <p>It carries only what the pipeline proved: the trusted server-side admission
 * mode, the structurally scanned message, the canonical DE48 request extension,
 * the domain amount/currency/Gregorian projection, and the NEW-exchange
 * freshness and 826-byte budget decisions. It is deliberately <em>not</em> an
 * admission, authorization, authentication or financial-outcome record, and it
 * exposes no accepted/authorized flag. Registry fixture resolution, mandate and
 * route eligibility, attestation, duplicate/conflict handling, claim, dispatch
 * and response construction remain separate downstream seams.</p>
 *
 * <p>For {@link Iso8583AdmissionMode#AUTHORIZED_HISTORICAL_REPLAY} and
 * {@link Iso8583AdmissionMode#LATE_EVIDENCE} the freshness and budget decisions
 * are {@code NOT_APPLICABLE}: the persisted original request stays
 * authoritative and is not re-encoded or sent again as a new wire claim.</p>
 *
 * @param mode          trusted server-side mode; never decoded from the wire
 * @param message       structurally scanned {@code 0100} message
 * @param extension     accepted canonical DE48 request extension
 * @param domain        validated domain projection
 * @param freshness     freshness decision for this mode
 * @param exchangeBudget NEW-exchange byte-budget decision for this mode
 */
public record Iso8583RequestValidation(
        Iso8583AdmissionMode mode,
        Iso8583Message message,
        Iso8583De48Request extension,
        Iso8583DomainDecision domain,
        Iso8583FreshnessDecision freshness,
        Iso8583ExchangeBudgetDecision exchangeBudget) {

    public Iso8583RequestValidation {
        Objects.requireNonNull(mode, "admission mode is required");
        Objects.requireNonNull(message, "scanned message is required");
        Objects.requireNonNull(extension, "canonical request extension is required");
        Objects.requireNonNull(domain, "domain projection is required");
        Objects.requireNonNull(freshness, "freshness decision is required");
        Objects.requireNonNull(exchangeBudget, "exchange-budget decision is required");
        if (!Iso8583RequestValidationPipeline.REQUEST_MTI.equals(message.mti())) {
            throw new IllegalArgumentException(
                    "request validation envelope only holds MTI "
                            + Iso8583RequestValidationPipeline.REQUEST_MTI);
        }
    }

    /**
     * Exact DE48 octets accepted by the canonical parser. The pipeline never
     * re-encodes, pads, trims or truncates the extension to derive this value.
     *
     * @return structurally accepted DE48 length, 1..999
     */
    public int de48Bytes() {
        return message.field(48).length();
    }
}
