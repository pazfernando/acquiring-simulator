package com.acquiring.simulator.domain.model;

/** Authorized caller response: business outcome and reference only (P08). */
public record AuthorizationResult(Outcome outcome, String reference) {

    public static AuthorizationResult of(Outcome outcome, String reference) {
        return new AuthorizationResult(outcome, reference);
    }
}
