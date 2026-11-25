package com.acquiring.simulator.domain.model;

import java.util.Set;

/**
 * Trusted local synthetic context. It is supplied by the exercise harness, never by the
 * purchase request, so a caller cannot assert its own acquirer or permissions.
 *
 * <p>{@code terminalRef} is optional: it is present when the trusted registry scopes the caller to
 * a specific terminal, and {@code null} when the context is not terminal-specific. The console
 * history cursor binds it when present (D-6 target-specific context).</p>
 */
public record AuthorizedContext(
        String acquirerRef,
        String merchantRef,
        String callerId,
        String terminalRef,
        Set<Route> permittedRoutes) {

    /** Context with no terminal scope, kept for the callers that are not terminal-specific. */
    public AuthorizedContext(String acquirerRef, String merchantRef, String callerId,
            Set<Route> permittedRoutes) {
        this(acquirerRef, merchantRef, callerId, null, permittedRoutes);
    }

    public AuthorizedContext {
        permittedRoutes = permittedRoutes == null ? Set.of() : Set.copyOf(permittedRoutes);
    }
}
