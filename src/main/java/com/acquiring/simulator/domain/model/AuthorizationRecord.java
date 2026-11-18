package com.acquiring.simulator.domain.model;

import java.time.Instant;

/**
 * Internal persisted authorization record. It carries only allowlisted synthetic fields and
 * normalised evidence metadata, never raw payloads or payment secrets.
 */
public record AuthorizationRecord(
        String id,
        String businessKey,
        String acquirerRef,
        String merchantRef,
        String channelPaymentRef,
        long amountMinor,
        String currency,
        String credentialRef,
        String terminalRef,
        EntryMode entryMode,
        CvmIndicator cvmIndicator,
        boolean cryptogramPresent,
        RiskIndicator riskIndicator,
        Route route,
        String paymentFingerprint,
        int generation,
        AuthorizationState state,
        DecisionSource decisionSource,
        String reasonCategory,
        String dispatchCorrelationId,
        Instant firstReceivedAt,
        Instant dispatchedAt,
        Instant outcomeDeterminedAt,
        Instant duplicateWindowEnd) {

    public AuthorizationRecord withDispatch(String correlationId, Instant at) {
        return new AuthorizationRecord(id, businessKey, acquirerRef, merchantRef, channelPaymentRef,
                amountMinor, currency, credentialRef, terminalRef, entryMode, cvmIndicator,
                cryptogramPresent, riskIndicator, route, paymentFingerprint, generation,
                AuthorizationState.AWAITING, DecisionSource.NONE, reasonCategory, correlationId,
                firstReceivedAt, at, outcomeDeterminedAt, duplicateWindowEnd);
    }

    public AuthorizationRecord withOutcome(
            AuthorizationState newState,
            DecisionSource source,
            String reason,
            Instant determinedAt) {
        return new AuthorizationRecord(id, businessKey, acquirerRef, merchantRef, channelPaymentRef,
                amountMinor, currency, credentialRef, terminalRef, entryMode, cvmIndicator,
                cryptogramPresent, riskIndicator, route, paymentFingerprint, generation,
                newState, source, reason, dispatchCorrelationId, firstReceivedAt, dispatchedAt,
                determinedAt, duplicateWindowEnd);
    }

    public Instant deadline(Instant fallback) {
        return dispatchedAt == null ? fallback : dispatchedAt;
    }
}
