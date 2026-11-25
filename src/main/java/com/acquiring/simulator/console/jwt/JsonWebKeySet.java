package com.acquiring.simulator.console.jwt;

import java.security.PublicKey;
import java.util.Map;

/**
 * A verified-key lookup keyed by the JWS {@code kid}.
 *
 * <p>Only public verification keys are held; no private key, token or credential is present. Keys
 * are immutable, so replacing the set on rotation is the only way its content changes.</p>
 */
public record JsonWebKeySet(Map<String, PublicKey> keysByKid) {

    public JsonWebKeySet {
        keysByKid = keysByKid == null ? Map.of() : Map.copyOf(keysByKid);
    }

    /** The key for one id, or {@code null} when this set does not contain it. */
    public PublicKey key(String kid) {
        return kid == null ? null : keysByKid.get(kid);
    }

    public int size() {
        return keysByKid.size();
    }
}
