package com.acquiring.simulator.domain.model;

import java.util.Set;

/**
 * Trusted local synthetic context. It is supplied by the exercise harness, never by the
 * purchase request, so a caller cannot assert its own acquirer or permissions.
 */
public record AuthorizedContext(
        String acquirerRef,
        String merchantRef,
        String callerId,
        Set<Route> permittedRoutes) {

    public AuthorizedContext {
        permittedRoutes = permittedRoutes == null ? Set.of() : Set.copyOf(permittedRoutes);
    }
}
