package com.acquiring.simulator.protocol.iso8583;

/**
 * Outcome of the approved NEW-request freshness check.
 *
 * <p>This is not admission, trust or a financial outcome. It only says whether
 * a new first dispatch's {@code sent} is inside the approved 300-second window,
 * is outside it, or that the window does not apply because the operation is an
 * authorized replay or late evidence.</p>
 */
public enum Iso8583FreshnessDecision {

    /** {@code sent} is within the approved distance of server receipt. */
    FRESH,

    /** {@code sent} is outside the approved distance of server receipt. */
    STALE,

    /** Not a new first dispatch; the persisted request is authoritative. */
    NOT_APPLICABLE;

    /**
     * @return the approved first-failure label for a stale value, otherwise
     *         {@code null}
     */
    public String failureRule() {
        return this == STALE ? Iso8583FreshnessValidator.FAILURE_RULE : null;
    }
}
