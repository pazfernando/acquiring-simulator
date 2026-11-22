package com.acquiring.simulator.channel;

import com.acquiring.simulator.domain.model.CvmIndicator;
import com.acquiring.simulator.domain.model.EntryMode;
import com.acquiring.simulator.domain.model.RiskIndicator;
import java.math.BigDecimal;

/**
 * Typed C1 POS authorization request derived only from validated channel input.
 *
 * <p>Every component is copied from the closed JSON body after the C1 checks:
 * no server-assigned identifier, attempt/generation, alias, route, instruction
 * field, fixture outcome or registry resolution is present. The amount is
 * carried as exact integer minor units, with {@link #amount()} as the exact
 * two-decimal projection; no floating-point value is involved.</p>
 *
 * <p>The represented acquirer, merchant, terminal, credential fixture and prior
 * authorization reference remain <em>caller-supplied references</em>. Whether
 * they resolve to a registered service mandate or a stored record is decided by
 * the later trusted server seam, never by this object or by the JSON body.</p>
 */
public record ChannelAuthorizationRequest(
        String contractVersion,
        String operation,
        String correlationId,
        String acquirerRef,
        String merchantRef,
        String terminalRef,
        String paymentRef,
        String credentialFixtureRef,
        long amountMinor,
        String currency,
        EntryMode entryMode,
        CvmIndicator cvm,
        boolean cryptogramPresent,
        RiskIndicator risk,
        String priorAuthorizationId) {

    /** Exact business amount derived from {@link #amountMinor}. */
    public BigDecimal amount() {
        return ChannelAmount.toAmount(amountMinor);
    }
}
