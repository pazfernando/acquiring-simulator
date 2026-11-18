package com.acquiring.simulator.domain.model;

import java.math.BigDecimal;

/** Business-level purchase request. Amount precision is validated in the domain. */
public record PurchaseRequest(
        String merchantRef,
        String terminalRef,
        String channelPaymentReference,
        BigDecimal amount,
        String currency,
        String credentialRef,
        EntryMode entryMode,
        CvmIndicator cvmIndicator,
        Boolean cryptogramPresent,
        RiskIndicator riskIndicator,
        String priorReference) {
}
