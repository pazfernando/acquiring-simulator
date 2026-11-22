package com.acquiring.simulator.channel;

import com.acquiring.simulator.domain.model.AuthorizedContext;
import com.acquiring.simulator.domain.model.CvmIndicator;
import com.acquiring.simulator.domain.model.EntryMode;
import com.acquiring.simulator.domain.model.RiskIndicator;
import com.acquiring.simulator.domain.model.Route;
import java.util.Objects;

/**
 * Trusted, immutable server-side resolution of one NEW Operation A instruction.
 *
 * <p>This is an input seam, not a resolver. The caller must supply every value
 * from its already-completed server work: current authorization context,
 * registered aliases, durable identifiers, durable STAN/RRN allocation, server
 * send timestamp and the resolved fixture/CVM/route/risk. The record performs
 * no registry lookup, mandate check, identifier allocation, alias allocation,
 * counter increment, hashing, outcome selection or authentication claim. It only
 * refuses missing envelope fields and impossible numeric bounds.</p>
 *
 * <p><strong>Value ownership.</strong> The mapper takes {@code aid}, {@code tid},
 * {@code att}, {@code gen}, DE32/DE41/DE42 aliases, STAN, RRN and {@code sent}
 * exclusively from this record. It takes {@code cid} exclusively from the
 * validated channel request. The business refs, fixture, entry mode, CVM,
 * cryptogram flag, risk, amount and currency are restated here as the resolved
 * values and must equal the validated channel request; the mapper rejects a
 * mismatch instead of silently preferring either side.</p>
 *
 * <p>Deliberately absent: issuer decision, route outcome, response evidence,
 * dispatch receipt, claim state, original-wire hash and any client-overridable
 * copy of a server value. Those remain separate seams.</p>
 *
 * @param authorizedContext     current authorized acquirer/merchant context and
 *                              its permitted routes
 * @param authorizationId       server-assigned {@code aid}, 1..64
 * @param transactionId         server-assigned {@code tid}, 1..64
 * @param instructionAttemptId  server-assigned submission {@code att}, 1..64
 * @param generation            server-assigned {@code gen}, positive decimal
 * @param acquirerRef           full original business acquirer reference
 * @param merchantRef           full original business merchant reference
 * @param terminalRef           full original business terminal reference
 * @param paymentRef            full original business payment reference
 * @param acquirerAlias         registered DE32 decimal alias, 1..11 digits
 * @param merchantAlias         registered DE42 alias, exactly 15 upper alnum
 * @param terminalAlias         registered DE41 alias, exactly 8 upper alnum
 * @param stan                  durable DE11 system trace audit number, 6 digits
 * @param rrn                   durable DE37 retrieval reference, 12 upper alnum
 * @param sent                  server request timestamp, {@code YYYYMMDDhhmmss}
 * @param fixture               resolved registry credential fixture reference
 * @param entryMode             resolved POS entry mode
 * @param cvm                   resolved CVM indicator
 * @param cryptogramPresent     resolved synthetic cryptogram-present flag
 * @param risk                  resolved risk outcome
 * @param amountMinor           resolved exact minor-unit amount
 * @param currency              resolved currency, exactly {@code USD}
 * @param route                 resolved single permitted route (not wire-encoded)
 */
public record ServerInstructionResolution(
        AuthorizedContext authorizedContext,
        String authorizationId,
        String transactionId,
        String instructionAttemptId,
        int generation,
        String acquirerRef,
        String merchantRef,
        String terminalRef,
        String paymentRef,
        String acquirerAlias,
        String merchantAlias,
        String terminalAlias,
        String stan,
        String rrn,
        String sent,
        String fixture,
        EntryMode entryMode,
        CvmIndicator cvm,
        boolean cryptogramPresent,
        RiskIndicator risk,
        long amountMinor,
        String currency,
        Route route) {

    public ServerInstructionResolution {
        Objects.requireNonNull(authorizedContext, "authorizedContext");
        Objects.requireNonNull(authorizationId, "authorizationId");
        Objects.requireNonNull(transactionId, "transactionId");
        Objects.requireNonNull(instructionAttemptId, "instructionAttemptId");
        Objects.requireNonNull(acquirerRef, "acquirerRef");
        Objects.requireNonNull(merchantRef, "merchantRef");
        Objects.requireNonNull(terminalRef, "terminalRef");
        Objects.requireNonNull(paymentRef, "paymentRef");
        Objects.requireNonNull(acquirerAlias, "acquirerAlias");
        Objects.requireNonNull(merchantAlias, "merchantAlias");
        Objects.requireNonNull(terminalAlias, "terminalAlias");
        Objects.requireNonNull(stan, "stan");
        Objects.requireNonNull(rrn, "rrn");
        Objects.requireNonNull(sent, "sent");
        Objects.requireNonNull(fixture, "fixture");
        Objects.requireNonNull(entryMode, "entryMode");
        Objects.requireNonNull(cvm, "cvm");
        Objects.requireNonNull(risk, "risk");
        Objects.requireNonNull(currency, "currency");
        Objects.requireNonNull(route, "route");
        if (generation < 1) {
            throw new IllegalArgumentException("generation must be positive: " + generation);
        }
        if (amountMinor < 0) {
            throw new IllegalArgumentException("amountMinor must not be negative");
        }
    }
}
