package com.acquiring.simulator.channel;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Shared strict wire helpers for the internal C2 HTTP API payload v2 adapter.
 *
 * <p>This class is deliberately transport-only: it never reads an identity header, never
 * derives a principal from a body and never calls a business service. It enforces the
 * canonical base64, bound, media, profile, compression and duplicate-header rules before
 * any ISO8583 parser can see the bytes.</p>
 */
final class HttpApiV2Wire {

    /** Approved internal C2 route. */
    static final String INTERNAL_PATH = "/internal/sim-iso87a/v1/authorizations";

    /** Approved C2 media type. */
    static final String MEDIA_TYPE = "application/octet-stream";

    /** Approved C2 profile header value. */
    static final String PROFILE = "SIM-ISO87A-v1";

    /** Maximum decoded octet-stream body bytes. */
    static final int MAX_BODY_BYTES = 2048;

    /** Transport guard for one payload v2 event; not a business or C2 contract bound. */
    static final int MAX_EVENT_BYTES = 65536;

    /**
     * Caller-supplied identity assertions that are never accepted as transport identity.
     * The trusted context comes only from the platform authentication mapping outside this
     * adapter; these headers are refused rather than silently ignored.
     */
    private static final Set<String> IDENTITY_HEADERS = Set.of(
            "x-authenticated-sender",
            "x-authenticated-principal",
            "x-simulator-principal",
            "x-simulator-sender",
            "x-remote-user",
            "x-forwarded-user");

    private static final ObjectMapper MAPPER = new ObjectMapper(
            JsonFactory.builder()
                    .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                    .build())
            .configure(DeserializationFeature.FAIL_ON_TRAILING_TOKENS, true);

    private HttpApiV2Wire() {
    }

    static JsonNode readEvent(byte[] raw) {
        Objects.requireNonNull(raw, "raw");
        if (raw.length == 0 || raw.length > MAX_EVENT_BYTES) {
            throw HttpApiV2TransportException.of(
                    HttpApiV2TransportViolation.EVENT_SIZE, "event");
        }
        try {
            JsonNode root = MAPPER.readTree(raw);
            if (root == null || !root.isObject()) {
                throw HttpApiV2TransportException.of(
                        HttpApiV2TransportViolation.EVENT_SCHEMA, "event");
            }
            return root;
        } catch (HttpApiV2TransportException e) {
            throw e;
        } catch (Exception e) {
            throw HttpApiV2TransportException.of(
                    HttpApiV2TransportViolation.EVENT_SCHEMA, "event");
        }
    }

    static Map<String, List<String>> readHeaders(JsonNode node, String field) {
        if (node == null || node.isNull()) {
            return Map.of();
        }
        if (!node.isObject()) {
            throw HttpApiV2TransportException.of(
                    HttpApiV2TransportViolation.EVENT_SCHEMA, field);
        }
        Map<String, List<String>> headers = new LinkedHashMap<>();
        Set<String> seen = new HashSet<>();
        Iterator<Map.Entry<String, JsonNode>> entries = node.fields();
        while (entries.hasNext()) {
            Map.Entry<String, JsonNode> entry = entries.next();
            String name = entry.getKey();
            if (name == null || name.isEmpty()) {
                throw HttpApiV2TransportException.of(
                        HttpApiV2TransportViolation.EVENT_SCHEMA, field);
            }
            if (!seen.add(name.toLowerCase(Locale.ROOT))) {
                throw HttpApiV2TransportException.of(
                        HttpApiV2TransportViolation.DUPLICATE_HEADER, name);
            }
            JsonNode value = entry.getValue();
            if (value == null || !value.isTextual()) {
                throw HttpApiV2TransportException.of(
                        HttpApiV2TransportViolation.EVENT_SCHEMA, name);
            }
            String text = value.asText();
            if (text.indexOf('\r') >= 0 || text.indexOf('\n') >= 0) {
                throw HttpApiV2TransportException.of(
                        HttpApiV2TransportViolation.EVENT_SCHEMA, name);
            }
            headers.put(name, List.of(text));
        }
        return Collections.unmodifiableMap(headers);
    }

    static List<String> headerValues(Map<String, List<String>> headers, String name) {
        List<String> values = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
            if (name.equalsIgnoreCase(entry.getKey())) {
                values.addAll(entry.getValue());
            }
        }
        return values;
    }

    static String singleHeader(Map<String, List<String>> headers, String name,
            HttpApiV2TransportViolation missing,
            HttpApiV2TransportViolation duplicate) {
        List<String> values = headerValues(headers, name);
        if (values.isEmpty()) {
            throw HttpApiV2TransportException.of(missing, name);
        }
        if (values.size() > 1) {
            throw HttpApiV2TransportException.of(duplicate, name);
        }
        String value = values.get(0);
        if (value == null || value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0
                || value.indexOf(',') >= 0) {
            throw HttpApiV2TransportException.of(duplicate, name);
        }
        return value;
    }

    static void refuseIdentityAssertions(Map<String, List<String>> headers) {
        for (String name : headers.keySet()) {
            if (IDENTITY_HEADERS.contains(name.toLowerCase(Locale.ROOT))) {
                throw HttpApiV2TransportException.of(
                        HttpApiV2TransportViolation.IDENTITY_ASSERTION, name);
            }
        }
    }

    /**
     * Strict RFC4648 standard padded base64. The value is decoded and re-encoded so
     * non-zero pad bits, whitespace, URL-safe alphabet and unpadded forms are rejected.
     * The decoded size is checked by the caller after decoding so the correct body/size
     * violation is reported.
     */
    static byte[] decodeCanonicalBase64(String value) {
        if (value == null) {
            throw HttpApiV2TransportException.of(
                    HttpApiV2TransportViolation.BASE64_NONCANONICAL, "base64");
        }
        if (value.isEmpty()) {
            return new byte[0];
        }
        if (value.length() % 4 != 0) {
            throw HttpApiV2TransportException.of(
                    HttpApiV2TransportViolation.BASE64_NONCANONICAL, "base64");
        }
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(value);
        } catch (IllegalArgumentException e) {
            throw HttpApiV2TransportException.of(
                    HttpApiV2TransportViolation.BASE64_NONCANONICAL, "base64");
        }
        if (!Base64.getEncoder().encodeToString(decoded).equals(value)) {
            throw HttpApiV2TransportException.of(
                    HttpApiV2TransportViolation.BASE64_NONCANONICAL, "base64");
        }
        return decoded;
    }

    static boolean isCompressed(byte[] raw) {
        if (raw.length >= 2) {
            int b0 = raw[0] & 0xff;
            int b1 = raw[1] & 0xff;
            if ((b0 == 0x1f && b1 == 0x8b)
                    || (b0 == 0x42 && b1 == 0x5a && raw.length >= 3 && raw[2] == 0x68)
                    || (b0 == 0x78 && (b1 == 0x01 || b1 == 0x5e
                            || b1 == 0x9c || b1 == 0xda))) {
                return true;
            }
        }
        if (raw.length >= 4) {
            int b0 = raw[0] & 0xff;
            int b1 = raw[1] & 0xff;
            int b2 = raw[2] & 0xff;
            int b3 = raw[3] & 0xff;
            return (b0 == 0xfd && b1 == 0x37 && b2 == 0x7a && b3 == 0x58)
                    || (b0 == 0x50 && b1 == 0x4b && b2 == 0x03 && b3 == 0x04)
                    || (b0 == 0x28 && b1 == 0xb5 && b2 == 0x2f && b3 == 0xfd);
        }
        return false;
    }

    static int totalHeaderBytes(Map<String, List<String>> headers) {
        int total = 0;
        for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
            for (String value : entry.getValue()) {
                total += entry.getKey().length() + 2 + value.length() + 2;
            }
        }
        return total;
    }

    static void requireBodySize(int length) {
        if (length < 1 || length > MAX_BODY_BYTES) {
            throw HttpApiV2TransportException.of(
                    HttpApiV2TransportViolation.BODY_SIZE, "body");
        }
    }

    static void requireBase64Flag(JsonNode event) {
        JsonNode flag = event.get("isBase64Encoded");
        if (flag == null || !flag.isBoolean() || !flag.booleanValue()) {
            throw HttpApiV2TransportException.of(
                    HttpApiV2TransportViolation.UNSUPPORTED_ENCODING,
                    "isBase64Encoded");
        }
    }

    static ObjectNode objectNode() {
        return MAPPER.createObjectNode();
    }

    static byte[] writeJson(JsonNode node) {
        try {
            return MAPPER.writeValueAsBytes(node);
        } catch (Exception e) {
            throw new IllegalStateException("cannot encode transport event", e);
        }
    }
}
