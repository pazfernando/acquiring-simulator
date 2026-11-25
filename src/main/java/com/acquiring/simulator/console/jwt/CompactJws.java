package com.acquiring.simulator.console.jwt;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * A syntactically well-formed compact JWS: three base64url segments and two JSON objects.
 *
 * <p>Parsing here proves only shape, not trust. Signature verification and claim policy happen in
 * {@link JwtConsoleTokenDecoder} after this value is produced. The raw token text is not retained:
 * only the decoded header/payload, the signed input and the signature bytes are kept, all inside
 * one decode call and never stored, logged or rendered.</p>
 */
final class CompactJws {

    private static final ObjectMapper JSON = new ObjectMapper();

    /** A refusal that must be reported as {@code MALFORMED_TOKEN}, never as a partial identity. */
    static final class Malformed extends RuntimeException {
        Malformed(String message) {
            super(message);
        }

        Malformed(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private final JsonNode header;
    private final JsonNode payload;
    private final byte[] signingInput;
    private final byte[] signature;

    private CompactJws(JsonNode header, JsonNode payload, byte[] signingInput, byte[] signature) {
        this.header = header;
        this.payload = payload;
        this.signingInput = signingInput;
        this.signature = signature;
    }

    JsonNode header() {
        return header;
    }

    JsonNode payload() {
        return payload;
    }

    byte[] signingInput() {
        return signingInput.clone();
    }

    byte[] signature() {
        return signature.clone();
    }

    static CompactJws parse(String token) {
        if (token == null || token.isEmpty()) {
            throw new Malformed("token is empty");
        }
        String[] parts = token.split("\\.", -1);
        if (parts.length != 3) {
            throw new Malformed("token is not a three-part compact JWS");
        }
        byte[] headerBytes = decode(parts[0]);
        byte[] payloadBytes = decode(parts[1]);
        byte[] signature = decode(parts[2]);
        JsonNode header = object(headerBytes, "header");
        JsonNode payload = object(payloadBytes, "payload");
        byte[] signingInput = (parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII);
        return new CompactJws(header, payload, signingInput, signature);
    }

    private static byte[] decode(String segment) {
        if (segment.isEmpty()) {
            throw new Malformed("token segment is empty");
        }
        try {
            return Base64.getUrlDecoder().decode(segment);
        } catch (IllegalArgumentException invalid) {
            throw new Malformed("token segment is not base64url", invalid);
        }
    }

    private static JsonNode object(byte[] bytes, String part) {
        try {
            JsonNode node = JSON.readTree(bytes);
            if (node == null || !node.isObject()) {
                throw new Malformed("token " + part + " is not a JSON object");
            }
            return node;
        } catch (Malformed malformed) {
            throw malformed;
        } catch (Exception invalid) {
            throw new Malformed("token " + part + " is not valid JSON", invalid);
        }
    }
}
