package com.acquiring.simulator.console;

import com.acquiring.simulator.domain.model.AuthorizedContext;

/**
 * Seam for the subject-to-mandate registry that supplies the trusted synthetic context.
 *
 * <p>The context is <strong>never</strong> taken from the browser body. Channel contract
 * R19 (as revised by the reviewed access direction) requires the synthetic
 * acquirer/merchant/terminal mandate to be derived from trusted subject/registry data, so
 * a caller cannot assert its own acquirer or permissions. A real implementation reads the
 * current demo-subject mandate and its deployment environment; this slice supplies the
 * seam only and never reads a registry, a table or a network endpoint.</p>
 *
 * <p>Fail-closed rule: a {@code null} result or a thrown failure is refused as
 * {@code CONTEXT_DENIED}. A registry that cannot answer must never yield a default or
 * widened context.</p>
 */
@FunctionalInterface
public interface ConsoleContextRegistry {

    /**
     * @param principal the verified console identity
     * @return the trusted synthetic context for that subject, or {@code null} when the
     *     subject has no current mandate
     */
    AuthorizedContext contextFor(ConsolePrincipal principal);
}
