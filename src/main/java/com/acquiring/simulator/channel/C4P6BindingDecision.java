package com.acquiring.simulator.channel;

import java.util.Objects;

/**
 * Result of one pure signed-P5/P6 field binding.
 *
 * <p>Construction means only that every signed attestation field equalled the persisted
 * original {@code 0100} instruction projection and the exact received {@code 0110}
 * response echo, that the exact byte hashes matched the signed hashes, and that the
 * composed P6 domain, P4 and instruction/DE38 phases all accepted the response.</p>
 *
 * <p>It is <strong>not</strong> authentication, issuer truth, key custody, a durable
 * processor receipt, business evidence admission or a financial outcome. {@code result} and
 * {@code reason} here are the <em>signed claims</em> that were bound, not a result that is
 * known or confirmed before the atomic receipt transaction. There is deliberately no
 * {@code confirmed}, {@code admitted}, {@code committed}, {@code receiptDurable},
 * {@code valid} or {@code verified} flag and no timestamp read.</p>
 *
 * @param evidenceId        signed evidence identifier bound to the response echo
 * @param authorizationId   original authorization ID echoed by the response
 * @param transactionId     original transaction ID echoed by the response
 * @param amountMinor       bound DE4 minor-unit amount from the signed record
 * @param currency          bound DE49 currency from the signed record
 * @param de39              bound response DE39, a signed claim only
 * @param result            bound signed result label, a claim only
 * @param reason            bound signed reason label, a claim only
 * @param authorizationReference exactly the bound DE38 value, or {@code null} when both
 *                          the signed record and the response carry none
 * @param scope             exactly which binding chain was established
 */
public record C4P6BindingDecision(
        String evidenceId,
        String authorizationId,
        String transactionId,
        long amountMinor,
        String currency,
        String de39,
        String result,
        String reason,
        String authorizationReference,
        BindingScope scope) {

    /** Exactly what this step established. */
    public enum BindingScope {
        SIGNED_P5_FIELDS_BOUND_TO_PERSISTED_INSTRUCTION_AND_RESPONSE
    }

    public C4P6BindingDecision {
        Objects.requireNonNull(evidenceId, "evidenceId");
        Objects.requireNonNull(authorizationId, "authorizationId");
        Objects.requireNonNull(transactionId, "transactionId");
        Objects.requireNonNull(currency, "currency");
        Objects.requireNonNull(de39, "de39");
        Objects.requireNonNull(result, "result");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(scope, "scope");
    }

    /** @return {@code true} when an exact DE38 authorization reference was bound */
    public boolean hasAuthorizationReference() {
        return authorizationReference != null;
    }

    /** Never renders signed amounts, references or the full field set. */
    @Override
    public String toString() {
        return "C4P6BindingDecision[evidenceId=" + evidenceId
                + ", authorizationId=" + authorizationId
                + ", transactionId=" + transactionId
                + ", scope=" + scope + "]";
    }
}
