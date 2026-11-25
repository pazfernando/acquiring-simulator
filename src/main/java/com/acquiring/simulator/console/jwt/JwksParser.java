package com.acquiring.simulator.console.jwt;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * Parses a JSON Web Key Set document into RSA verification keys.
 *
 * <p>Only {@code kty=RSA} signature keys are accepted. A key is skipped when it is not an RSA key,
 * when {@code use} is present and is not {@code sig}, or when it lacks {@code kid}/{@code n}/{@code e}.
 * A malformed document, a duplicate {@code kid} or a bad base64url value is a hard failure rather
 * than an empty set: silently returning "no keys" would turn a configuration defect into an
 * opaque authentication refusal.</p>
 *
 * <p>No {@code alg}, {@code x5u}, {@code x5c} or embedded {@code jwk} header is trusted for
 * key selection; the token's algorithm allowlist and the {@code kid} lookup are the only inputs.</p>
 */
final class JwksParser {

    private static final ObjectMapper JSON = new ObjectMapper();

    private JwksParser() {
    }

    static JsonWebKeySet parse(String document) {
        if (document == null || document.isBlank()) {
            throw new JwksSourceException("jwks document is empty");
        }
        JsonNode root;
        try {
            root = JSON.readTree(document);
        } catch (Exception malformed) {
            throw new JwksSourceException("jwks document is not valid JSON", malformed);
        }
        if (root == null || !root.isObject()) {
            throw new JwksSourceException("jwks document is not a JSON object");
        }
        JsonNode keys = root.get("keys");
        if (keys == null || !keys.isArray()) {
            throw new JwksSourceException("jwks document has no keys array");
        }

        Map<String, PublicKey> parsed = new HashMap<>();
        for (JsonNode key : keys) {
            if (!key.isObject() || !"RSA".equals(text(key, "kty"))) {
                continue;
            }
            String use = text(key, "use");
            if (use != null && !"sig".equals(use)) {
                continue;
            }
            String kid = text(key, "kid");
            String modulus = text(key, "n");
            String exponent = text(key, "e");
            if (kid == null || kid.isBlank() || modulus == null || exponent == null) {
                continue;
            }
            PublicKey publicKey = rsaKey(modulus, exponent);
            if (parsed.putIfAbsent(kid, publicKey) != null) {
                throw new JwksSourceException("jwks document repeats a key id");
            }
        }
        return new JsonWebKeySet(parsed);
    }

    private static PublicKey rsaKey(String modulus, String exponent) {
        try {
            BigInteger n = new BigInteger(1, decode(modulus));
            BigInteger e = new BigInteger(1, decode(exponent));
            if (n.bitLength() < 2048) {
                throw new JwksSourceException("jwks RSA key is shorter than 2048 bits");
            }
            return KeyFactory.getInstance("RSA").generatePublic(new RSAPublicKeySpec(n, e));
        } catch (JwksSourceException refused) {
            throw refused;
        } catch (Exception invalid) {
            throw new JwksSourceException("jwks RSA key is unusable", invalid);
        }
    }

    private static byte[] decode(String value) {
        try {
            return Base64.getUrlDecoder().decode(value);
        } catch (IllegalArgumentException invalid) {
            throw new JwksSourceException("jwks key value is not base64url", invalid);
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value != null && value.isTextual() ? value.asText() : null;
    }
}
