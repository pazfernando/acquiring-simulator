package com.acquiring.simulator.channel;

import java.util.Optional;

/**
 * Read-only configured key registry for DIRECT and RELAY C4 evidence verification.
 *
 * <p>The lookup is keyed by the triple {@code (environmentId, principal, keyId)}. The
 * registry supplies the public key, P5 role and acquirer mandate; it never supplies a
 * private key, a client-asserted identity or a signature. Key custody, rotation, remote
 * distribution, IAM/TLS principal mapping and production provisioning remain outside this
 * local construction seam.</p>
 */
@FunctionalInterface
public interface C4KeyRegistry {

    /**
     * Resolve the configured public key and allowed role/mandate for exactly this triple.
     * An empty result is an unknown key and must fail closed.
     */
    Optional<C4TrustedPrincipalKey> resolve(
            String environmentId, String principal, String keyId);

    /**
     * Explicit scoped permission for a diagnostic delivery principal to forward an
     * unchanged signed proof into the internal late-delivery handler.
     *
     * <p>This is deliberately separate from {@link #resolve}: it does not resolve a key,
     * does not confer a {@link C4PrincipalRole}, does not permit signing or minting an
     * outcome and does not grant payment, receipt or durability authority. The default is
     * deny so an unconfigured registry fails closed. Only the trusted internal delivery
     * adapter may supply an implementation that returns true, and only for the exact
     * {@code (environmentId, principal, acquirerRef)} scope it configured.</p>
     */
    default boolean diagnosticForwardPermitted(
            String environmentId, String principal, String acquirerRef) {
        return false;
    }
}
