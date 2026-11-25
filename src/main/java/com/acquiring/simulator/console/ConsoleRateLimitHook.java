package com.acquiring.simulator.console;

import java.time.Instant;

/**
 * Request-level rate-limit hook. <strong>Seam only — no limit is implemented or chosen.</strong>
 *
 * <p>The approved access direction names edge abuse controls (WAF rate rule, API Gateway
 * route throttling, attempt limiting) but every number in it is a <em>proposal</em>, not an
 * approved limit: rev4 §2.3 states the values explicitly as proposals and the owner
 * decision only requires "attempt rate limiting" without choosing any threshold. This
 * interface therefore carries no default, no window, no count and no clock unit.</p>
 *
 * <p>Ordering contract: the route consults this hook after the credential is verified and
 * before the body is parsed, the context is resolved, or the authorization service is
 * called. A refusal is a 429 with an empty body — never a C3 envelope, because the pinned
 * C3 status allowlist has no "too many requests" row — and it records no claim, no event
 * and no send.</p>
 *
 * <p>{@link #PERMISSIVE} is the only implementation in this slice and refuses nothing; a
 * real limiter must be supplied by the platform configuration review.</p>
 */
@FunctionalInterface
public interface ConsoleRateLimitHook {

    /** No limit is configured in this slice: every verified request is permitted. */
    ConsoleRateLimitHook PERMISSIVE = (principal, now) -> Decision.PERMITTED;

    /**
     * @param principal the already-verified identity
     * @param now the request time; implementations own their own window semantics
     * @return whether this request may continue
     */
    Decision evaluate(ConsolePrincipal principal, Instant now);

    /** Closed decision set; no count or retry hint is exposed. */
    enum Decision {
        PERMITTED,
        REFUSED
    }
}
