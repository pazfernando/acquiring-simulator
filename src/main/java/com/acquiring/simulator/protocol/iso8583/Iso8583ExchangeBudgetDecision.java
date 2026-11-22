package com.acquiring.simulator.protocol.iso8583;

/**
 * Outcome of the approved NEW-exchange admission-budget check.
 *
 * <p>This is not admission, trust or a financial outcome. It only says whether
 * a NEW request leaves room for the worst approved response suffix, whether it
 * exceeds that budget, or that the budget does not apply because the operation
 * is an authorized historical replay or late evidence.</p>
 */
public enum Iso8583ExchangeBudgetDecision {

    /** A NEW request whose DE48 fits the derived 826-byte admission budget. */
    ACCEPTED,

    /** A structurally valid NEW request whose DE48 exceeds the budget. */
    REJECTED,

    /** Not a new exchange; the persisted original request is authoritative. */
    NOT_APPLICABLE;

    /**
     * @return the approved first-failure label for an over-budget request,
     *         otherwise {@code null}
     */
    public String failureRule() {
        return this == REJECTED ? Iso8583ExchangeBudgetValidator.FAILURE_RULE : null;
    }
}
