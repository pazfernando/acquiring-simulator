package com.acquiring.simulator.console.jwt;

/**
 * Supplies the issuer's JSON Web Key Set.
 *
 * <p>This is a port, not a cache policy. The decoder never holds a single pinned key: when a token
 * names a key id the current set does not contain, the decoder asks {@link #refresh()} once and
 * looks again. A rotated key therefore becomes usable after a refresh, and a retired key stops
 * verifying as soon as the refreshed set no longer contains it.</p>
 *
 * <p>Implementations may perform I/O ({@link HttpJwksSource}); tests inject a source that does
 * not, which is how "no network in unit tests" is demonstrated. An implementation that cannot
 * supply a set must throw, never return a partial or stale-but-unavailable answer that would let
 * the decoder fall back to trust.</p>
 */
public interface JwksSource {

    /**
     * The current key set. Implementations may return a cached set.
     *
     * @throws JwksSourceException when no usable set is available
     */
    JsonWebKeySet current() throws JwksSourceException;

    /**
     * Reload the key set from the issuer and make it current.
     *
     * @throws JwksSourceException when the reload fails
     */
    JsonWebKeySet refresh() throws JwksSourceException;
}
