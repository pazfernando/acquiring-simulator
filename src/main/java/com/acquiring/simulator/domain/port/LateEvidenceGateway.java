package com.acquiring.simulator.domain.port;

import com.acquiring.simulator.domain.model.DownstreamReply;
import com.acquiring.simulator.domain.model.LateEvidenceAdmission;
import java.time.Instant;

/**
 * Narrow domain port for internal late verified-evidence admission (observation contract O3/P5,
 * channel contract C4 late-delivery paragraph; R07/R20).
 *
 * <p>The {@link DownstreamReply} parameter is the explicit internal seam: it is assumed to have
 * passed C4 sender/relay/role/key/signature and P6 ISO structure verification <em>before</em> the
 * port. Implementations may perform only the context authorization that needs the durable record
 * and must never accept a caller-set "verified" flag as production authentication.
 *
 * <p>One call is one admission attempt. It performs at most one atomic commit; it never resends,
 * never retries a physical send, never reverses, never migrates a legacy row and never writes a
 * legacy outcome/event. A recorded admission must be persisted knowledge, read back with its
 * committed-version evidence. A refusal or an unknown must leave storage untouched.
 *
 * <p>The port is optional. When no implementation is injected, the legacy {@code submitEvidence}
 * path stays in force unchanged; a non-success outcome must not trigger {@code updateOutcome}, a
 * legacy event append, a retry, a resend or a reversal.
 */
public interface LateEvidenceGateway {

    /**
     * Admit one already-authenticated late-evidence candidate for {@code authorizationId}.
     *
     * @param authorizationId   durable operation reference; the implementation re-reads the record
     * @param evidence          already-verified issuer reply (internal seam, not C4 verification)
     * @param deadline          admission deadline derived from the durable dispatch; never invented
     * @param deliveredAt       delivery time of this candidate; never used to relabel late vs on time
     * @param deliveryReceiptId bounded delivery receipt; duplicate protection is idempotent
     * @return persisted admission outcome; never a synthesized success
     */
    LateEvidenceAdmission submit(String authorizationId, DownstreamReply evidence, Instant deadline,
            Instant deliveredAt, String deliveryReceiptId);
}
