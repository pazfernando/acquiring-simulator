package com.acquiring.simulator.console.jwt;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigInteger;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.security.Signature;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Locally generated keys and tokens for the K1 decoder tests.
 *
 * <p>No issuer is contacted and no third-party token library is used: the RSA key pairs are made in
 * the test JVM and the compact JWS is assembled and signed with the JDK. This is test material, not
 * a production credential.</p>
 */
final class JwtTestSupport {

    static final ObjectMapper JSON = new ObjectMapper();

    static final String ISSUER = "https://auth.sim.test/realms/sim";
    static final String AUDIENCE = "console-api";
    static final String WS_AUDIENCE = "ws-api";
    static final String SUBJECT = "demo-user";
    static final String CLIENT = "console-demo";
    static final String SCOPE = "console.demo";
    static final String WS_SCOPE = "ws.observe";
    static final Instant NOW = Instant.parse("2026-11-23T12:00:00Z");

    /** Shared key pairs so generation cost is paid once, not per test. */
    static final KeyPair KEY_ONE = rsa();
    static final KeyPair KEY_TWO = rsa();

    private JwtTestSupport() {
    }

    static TrustedIssuerConfiguration configuration() {
        return new TrustedIssuerConfiguration(
                ISSUER,
                AUDIENCE,
                URI.create(ISSUER + "/protocol/openid-connect/certs"),
                SUBJECT,
                CLIENT,
                SCOPE,
                WS_SCOPE,
                WS_AUDIENCE,
                Set.of("RS256"),
                Duration.ofSeconds(900));
    }

    static Map<String, Object> defaultClaims(Instant now) {
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("iss", ISSUER);
        claims.put("aud", List.of(AUDIENCE));
        claims.put("sub", SUBJECT);
        claims.put("azp", CLIENT);
        claims.put("scope", SCOPE);
        claims.put("iat", now.minusSeconds(60).getEpochSecond());
        claims.put("nbf", now.minusSeconds(60).getEpochSecond());
        claims.put("exp", now.plusSeconds(600).getEpochSecond());
        return claims;
    }

    static JsonWebKeySet keySet(String kid, PublicKey key) {
        return new JsonWebKeySet(Map.of(kid, key));
    }

    static String jwksDocument(Map<String, PublicKey> keys) {
        ObjectNode root = JSON.createObjectNode();
        ArrayNode array = root.putArray("keys");
        for (Map.Entry<String, PublicKey> entry : keys.entrySet()) {
            ObjectNode jwk = array.addObject();
            jwk.put("kty", "RSA");
            jwk.put("kid", entry.getKey());
            jwk.put("use", "sig");
            jwk.put("alg", "RS256");
            RSAPublicKey rsa = (RSAPublicKey) entry.getValue();
            jwk.put("n", unsignedBase64Url(rsa.getModulus()));
            jwk.put("e", unsignedBase64Url(rsa.getPublicExponent()));
        }
        return root.toString();
    }

    static String token(KeyPair signingKey, String kid, Map<String, Object> claims) {
        return token(signingKey, kid, claims, "RS256");
    }

    static String token(KeyPair signingKey, String kid, Map<String, Object> claims, String alg) {
        ObjectNode header = JSON.createObjectNode();
        if (alg != null) {
            header.put("alg", alg);
        }
        header.put("typ", "JWT");
        if (kid != null) {
            header.put("kid", kid);
        }
        return tokenWithHeader(signingKey, header, claims);
    }

    static String tokenWithHeader(KeyPair signingKey, ObjectNode header,
            Map<String, Object> claims) {
        String signingInput = encode(JSON.valueToTree(header)) + "."
                + encode(JSON.valueToTree(claims));
        return signingInput + "." + encode(sign(signingKey, signingInput));
    }

    /** Replace the payload segment but keep the old signature, so only the signature can fail. */
    static String withTamperedPayload(String token, Map<String, Object> claims) {
        String[] parts = token.split("\\.", -1);
        return parts[0] + "." + encode(JSON.valueToTree(claims)) + "." + parts[2];
    }

    static ObjectNode headerOf(String token) {
        try {
            return (ObjectNode) JSON.readTree(Base64.getUrlDecoder()
                    .decode(token.split("\\.", -1)[0]));
        } catch (Exception failure) {
            throw new IllegalStateException(failure);
        }
    }

    private static byte[] sign(KeyPair key, String signingInput) {
        try {
            Signature signer = Signature.getInstance("SHA256withRSA");
            signer.initSign(key.getPrivate());
            signer.update(signingInput.getBytes(StandardCharsets.US_ASCII));
            return signer.sign();
        } catch (Exception failure) {
            throw new IllegalStateException(failure);
        }
    }

    private static String encode(JsonNode node) {
        try {
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(JSON.writeValueAsBytes(node));
        } catch (Exception failure) {
            throw new IllegalStateException(failure);
        }
    }

    private static String encode(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private static String unsignedBase64Url(BigInteger value) {
        byte[] bytes = value.toByteArray();
        if (bytes.length > 1 && bytes[0] == 0) {
            bytes = Arrays.copyOfRange(bytes, 1, bytes.length);
        }
        return encode(bytes);
    }

    private static KeyPair rsa() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (Exception failure) {
            throw new IllegalStateException("cannot generate a local test RSA key", failure);
        }
    }

    /**
     * In-memory key source. Every call is counted so a test can prove which source the decoder
     * consulted; this class performs no network I/O at all.
     */
    static final class CountingJwksSource implements JwksSource {

        private JsonWebKeySet currentSet;
        private final JsonWebKeySet refreshSet;
        private boolean failCurrent;
        private boolean failRefresh;
        private int currentCalls;
        private int refreshCalls;

        CountingJwksSource(JsonWebKeySet currentSet) {
            this(currentSet, null);
        }

        CountingJwksSource(JsonWebKeySet currentSet, JsonWebKeySet refreshSet) {
            this.currentSet = currentSet;
            this.refreshSet = refreshSet;
        }

        CountingJwksSource failCurrent() {
            this.failCurrent = true;
            return this;
        }

        CountingJwksSource failRefresh() {
            this.failRefresh = true;
            return this;
        }

        @Override
        public JsonWebKeySet current() {
            currentCalls++;
            if (failCurrent) {
                throw new JwksSourceException("test key source unavailable");
            }
            return currentSet;
        }

        @Override
        public synchronized JsonWebKeySet refresh() {
            refreshCalls++;
            if (failRefresh) {
                throw new JwksSourceException("test key source refresh unavailable");
            }
            if (refreshSet != null) {
                currentSet = refreshSet;
            }
            return currentSet;
        }

        int currentCalls() {
            return currentCalls;
        }

        int refreshCalls() {
            return refreshCalls;
        }
    }
}
