package com.acquiring.simulator.console;

import java.time.Instant;

/**
 * Injectable seam representing the future Gateway authorizer for the console surface.
 *
 * <p>A route depends on this interface, never on a concrete verifier, so the real
 * platform authorizer can replace the local realization without touching the route. The
 * route has no other way to obtain a {@link ConsolePrincipal}: there is no anonymous
 * fallback, no "trusted header" path and no default principal.</p>
 */
@FunctionalInterface
public interface ConsoleAuthorizer {

    /**
     * @param authorizationHeader the exact {@code Authorization} value the browser sent,
     *     or {@code null} when the header was absent or duplicated
     * @param now the request time the expiry decision is evaluated at
     * @return the verified identity
     * @throws ConsoleAuthenticationException when the credential must be refused
     */
    ConsolePrincipal authorize(String authorizationHeader, Instant now)
            throws ConsoleAuthenticationException;
}
