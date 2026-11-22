package com.acquiring.simulator.domain.event;

/**
 * Bounded reason vocabulary. It reuses the processor rejection reasons already owned by the
 * domain and adds only the lifecycle reasons the observation contract names. Free-text reasons
 * are rejected so no error body or stack trace can enter an event.
 */
public enum EventReason {
    VALID,
    SKIPPED_NOT_APPLICABLE,
    NO_RESPONSE,
    MISSING_OR_INVALID_INPUT,
    CALLER_NOT_AUTHORIZED,
    UNKNOWN_MERCHANT,
    UNKNOWN_TERMINAL,
    MERCHANT_ACQUIRER_MISMATCH,
    TERMINAL_MERCHANT_MISMATCH,
    UNKNOWN_CREDENTIAL,
    ROUTE_NOT_PERMITTED,
    RISK_REJECTED,
    INVALID_PRIOR_REFERENCE,
    FOREIGN_PRIOR_REFERENCE,
    INCOMPATIBLE_LINKED_RETRY,
    PAYMENT_DATA_CONFLICT
}
