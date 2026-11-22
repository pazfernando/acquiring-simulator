package com.acquiring.simulator.channel;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Strict parser for the internal C4 late-delivery envelope.
 *
 * <p>Scope is exactly the late-delivery payload, not the whole late handler. It enforces,
 * in order: the 8192-byte raw bound before any parse, no recognised compression container,
 * strict UTF-8 with no CR/LF folding, the exact closed four-key object, {@code version=1},
 * a 1–64 profile-character {@code deliveryReceiptId}, canonical padded RFC4648 standard
 * base64 for a non-empty original body of at most 2048 bytes, the unchanged canonical C4
 * evidence envelope of at most 4096 bytes, byte-exact canonical JSON, and the SHA-256 of
 * the exact decoded body against the signed {@code responseSha256}.</p>
 *
 * <p>It does not verify an Ed25519 signature, resolve a key, principal, role or mandate,
 * authorise the delivery sender, parse ISO8583, admit business evidence, emit HTTP, start
 * a worker or infer that a receipt is already durable. Rejections are sanitized
 * {@link C4LateDeliveryException}s that name a rule and at most a field, never a value.</p>
 */
public final class C4LateDeliveryParser {

    /** Maximum raw late-delivery payload bytes. */
    public static final int MAX_LATE_ENVELOPE_BYTES = 8192;

    /** Maximum decoded original response body bytes. */
    public static final int MAX_RESPONSE_BODY_BYTES = 2048;

    /** Maximum canonical inner C4 evidence envelope bytes. */
    public static final int MAX_EVIDENCE_BYTES = 4096;

    /** Maximum delivery receipt id characters. */
    public static final int MAX_RECEIPT_ID_CHARS = 64;

    private static final Set<String> LATE_KEYS = Set.copyOf(C4LateDeliveryEnvelope.KEYS);

    private static final ObjectMapper MAPPER = new ObjectMapper(
            JsonFactory.builder()
                    .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                    .build())
            .configure(DeserializationFeature.FAIL_ON_TRAILING_TOKENS, true);

    private C4LateDeliveryParser() {
    }

    /**
     * Parse one late-delivery payload into an explicitly unverified candidate.
     *
     * @throws C4LateDeliveryException on a late-delivery rule violation
     * @throws C4CarrierException when the nested C4 evidence envelope itself is malformed
     */
    public static C4LateDeliveryCandidate parse(byte[] raw) {
        Objects.requireNonNull(raw, "raw");
        if (raw.length == 0 || raw.length > MAX_LATE_ENVELOPE_BYTES) {
            throw C4LateDeliveryException.of(C4LateDeliveryViolation.LATE_ENVELOPE_SIZE);
        }
        rejectCompression(raw);

        String text = strictUtf8(raw);
        if (text.indexOf('\r') >= 0 || text.indexOf('\n') >= 0) {
            throw C4LateDeliveryException.of(
                    C4LateDeliveryViolation.LATE_ENVELOPE_ENCODING, "folding");
        }

        JsonNode root = readJson(text);
        requireObject(root, LATE_KEYS, "lateEnvelope");
        int version = integer(root, "version");
        if (version != C4LateDeliveryEnvelope.VERSION) {
            throw C4LateDeliveryException.of(
                    C4LateDeliveryViolation.LATE_ENVELOPE_SCHEMA, "version");
        }
        String receiptId = text(root, "deliveryReceiptId");
        String responseBodyBase64 = text(root, "responseBodyBase64");
        JsonNode evidenceNode = object(root, "evidence");

        byte[] decodedBody = decodeResponseBody(responseBodyBase64);
        C4CarrierEnvelope evidence = C4CarrierParser.parseEnvelope(evidenceNode);
        int evidenceBytes = evidence.toCanonicalBytes().length;
        if (evidenceBytes > MAX_EVIDENCE_BYTES) {
            throw C4LateDeliveryException.of(
                    C4LateDeliveryViolation.EVIDENCE_SIZE, "evidence");
        }

        C4LateDeliveryEnvelope lateEnvelope = new C4LateDeliveryEnvelope(
                version, receiptId, responseBodyBase64, evidence);
        String canonical = C4CarrierCanonicalJson.lateEnvelope(lateEnvelope);
        if (!text.equals(canonical)) {
            throw C4LateDeliveryException.of(
                    C4LateDeliveryViolation.LATE_ENVELOPE_SCHEMA, "canonical");
        }

        String bodySha256 = C4CarrierSchema.sha256Hex(decodedBody);
        if (!bodySha256.equals(evidence.attestation().responseSha256())) {
            throw C4LateDeliveryException.of(
                    C4LateDeliveryViolation.BODY_HASH_BINDING, "responseSha256");
        }

        return new C4LateDeliveryCandidate(
                lateEnvelope,
                bodySha256,
                evidence.attestation().canonicalSha256(),
                C4CarrierSchema.sha256Hex(C4CarrierSchema.asciiBytes(canonical)),
                C4LateDeliveryCandidate.ParseState.UNVERIFIED_CANDIDATE);
    }

    /**
     * Reject an explicitly compressed container before decoding. The late contract carries
     * canonical JSON; a recognised gzip, zlib, bzip2, xz, zip or zstd signature is never
     * silently inflated.
     */
    private static void rejectCompression(byte[] raw) {
        if (raw.length >= 2) {
            int b0 = raw[0] & 0xff;
            int b1 = raw[1] & 0xff;
            if ((b0 == 0x1f && b1 == 0x8b)
                    || (b0 == 0x42 && b1 == 0x5a && raw.length >= 3 && raw[2] == 0x68)
                    || (b0 == 0x78 && (b1 == 0x01 || b1 == 0x5e
                            || b1 == 0x9c || b1 == 0xda))) {
                throw C4LateDeliveryException.of(C4LateDeliveryViolation.COMPRESSION);
            }
        }
        if (raw.length >= 4) {
            int b0 = raw[0] & 0xff;
            int b1 = raw[1] & 0xff;
            int b2 = raw[2] & 0xff;
            int b3 = raw[3] & 0xff;
            if ((b0 == 0xfd && b1 == 0x37 && b2 == 0x7a && b3 == 0x58)
                    || (b0 == 0x50 && b1 == 0x4b && b2 == 0x03 && b3 == 0x04)
                    || (b0 == 0x28 && b1 == 0xb5 && b2 == 0x2f && b3 == 0xfd)) {
                throw C4LateDeliveryException.of(C4LateDeliveryViolation.COMPRESSION);
            }
        }
    }

    private static byte[] decodeResponseBody(String value) {
        if (value == null || value.isEmpty() || value.length() % 4 != 0) {
            throw C4LateDeliveryException.of(
                    C4LateDeliveryViolation.RESPONSE_BODY_ENCODING, "responseBodyBase64");
        }
        int padding = 0;
        if (value.endsWith("==")) {
            padding = 2;
        } else if (value.endsWith("=")) {
            padding = 1;
        }
        long decodedLength = (value.length() / 4L) * 3L - padding;
        if (decodedLength < 1 || decodedLength > MAX_RESPONSE_BODY_BYTES) {
            throw C4LateDeliveryException.of(
                    C4LateDeliveryViolation.RESPONSE_BODY_SIZE, "responseBodyBase64");
        }
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(value);
        } catch (IllegalArgumentException e) {
            throw C4LateDeliveryException.of(
                    C4LateDeliveryViolation.RESPONSE_BODY_ENCODING, "responseBodyBase64");
        }
        if (!Base64.getEncoder().encodeToString(decoded).equals(value)) {
            throw C4LateDeliveryException.of(
                    C4LateDeliveryViolation.RESPONSE_BODY_ENCODING, "responseBodyBase64");
        }
        return decoded;
    }

    private static JsonNode readJson(String json) {
        try {
            JsonNode root = MAPPER.readTree(json);
            if (root == null || !root.isObject()) {
                throw C4LateDeliveryException.of(
                        C4LateDeliveryViolation.LATE_ENVELOPE_SCHEMA);
            }
            return root;
        } catch (C4LateDeliveryException e) {
            throw e;
        } catch (Exception e) {
            throw C4LateDeliveryException.of(
                    C4LateDeliveryViolation.LATE_ENVELOPE_SCHEMA);
        }
    }

    private static String strictUtf8(byte[] bytes) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException e) {
            throw C4LateDeliveryException.of(
                    C4LateDeliveryViolation.LATE_ENVELOPE_ENCODING);
        }
    }

    private static void requireObject(JsonNode node, Set<String> expected, String field) {
        if (node == null || !node.isObject()) {
            throw C4LateDeliveryException.of(
                    C4LateDeliveryViolation.LATE_ENVELOPE_SCHEMA, field);
        }
        Set<String> actual = new LinkedHashSet<>();
        node.fieldNames().forEachRemaining(actual::add);
        if (!actual.equals(expected)) {
            String offending = actual.stream()
                    .filter(name -> !expected.contains(name))
                    .findFirst()
                    .orElse(field);
            throw C4LateDeliveryException.of(
                    C4LateDeliveryViolation.LATE_ENVELOPE_SCHEMA, offending);
        }
    }

    private static JsonNode object(JsonNode parent, String field) {
        JsonNode node = parent.get(field);
        if (node == null || !node.isObject()) {
            throw C4LateDeliveryException.of(
                    C4LateDeliveryViolation.LATE_ENVELOPE_SCHEMA, field);
        }
        return node;
    }

    private static String text(JsonNode parent, String field) {
        JsonNode node = parent.get(field);
        if (node == null || !node.isTextual()) {
            throw C4LateDeliveryException.of(
                    C4LateDeliveryViolation.LATE_ENVELOPE_SCHEMA, field);
        }
        return node.textValue();
    }

    private static int integer(JsonNode parent, String field) {
        JsonNode node = parent.get(field);
        if (node == null || !node.isIntegralNumber() || !node.canConvertToInt()) {
            throw C4LateDeliveryException.of(
                    C4LateDeliveryViolation.LATE_ENVELOPE_SCHEMA, field);
        }
        return node.intValue();
    }
}
