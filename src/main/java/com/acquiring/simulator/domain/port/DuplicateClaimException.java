package com.acquiring.simulator.domain.port;

/** Raised when another request already claimed the same (business key, generation). */
public class DuplicateClaimException extends RuntimeException {

    public DuplicateClaimException(String businessKey, int generation, Throwable cause) {
        super("claim already exists for business key " + businessKey + " generation " + generation, cause);
    }
}
