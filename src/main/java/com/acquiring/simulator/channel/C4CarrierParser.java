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
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Strict parser and shape model for the C4 outer evidence envelope.
 *
 * <p>Scope is exactly one carried exchange, not the whole C4/HTTP feature: read at most one
 * evidence header from the multi-value transport seam, enforce the RFC4648 padded standard
 * base64 form without whitespace or folding, bound the decoded size before allocating or
 * parsing, reject duplicate/unknown JSON keys, enforce the closed six-key envelope and
 * closed thirty-one-key attestation, verify that {@code responseSha256} binds the exact
 * received octet-stream bytes, verify the DIRECT/RELAY sender identity binding, and verify
 * the relay content hashes. It does not verify an Ed25519 signature, resolve a key or
 * principal, check a role or mandate, parse ISO8583, admit business evidence, emit HTTP or
 * expose anything to a browser.</p>
 *
 * <p>All carrier material is treated as opaque bytes: the body is hashed exactly as
 * received (after transport decoding) with no reserialization, and the instruction hash is
 * never recomputed. Rejections are sanitized {@link C4CarrierException}s that name a rule
 * and at most a field, never a value.</p>
 */
public final class C4CarrierParser {

    /** Single response header that carries the envelope. */
    public static final String HEADER_NAME = "X-Simulator-Evidence";

    /** Maximum encoded header characters: 5464. */
    public static final int MAX_ENCODED_CHARS = 5464;

    /** Maximum decoded envelope bytes: 4096. */
    public static final int MAX_DECODED_BYTES = 4096;

    /** Maximum exact octet-stream body bytes: 2048. */
    public static final int MAX_BODY_BYTES = 2048;

    /** Maximum total response header bytes: 8192. */
    public static final int MAX_TOTAL_HEADER_BYTES = 8192;

    /** Ed25519 signature bytes: exactly 64. */
    public static final int SIGNATURE_BYTES = 64;

    /** Encoded 64-byte signature characters: 88. */
    private static final int MAX_SIGNATURE_CHARS = 88;

    private static final Set<String> ENVELOPE_KEYS = Set.copyOf(C4CarrierEnvelope.KEYS);
    private static final Set<String> ATTESTATION_KEYS = Set.copyOf(C4EvidenceAttestation.KEYS);
    private static final Set<String> RELAY_KEYS = Set.copyOf(C4RelayAttestation.KEYS);

    private static final ObjectMapper MAPPER = new ObjectMapper(
            JsonFactory.builder()
                    .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                    .build())
            .configure(DeserializationFeature.FAIL_ON_TRAILING_TOKENS, true);

    private C4CarrierParser() {
    }

    /**
     * Parse one carried response into an explicitly unverified shape candidate.
     *
     * @throws C4CarrierException when a carrier rule is violated; the exception is the
     *     sanitized rejection, not a channel response
     */
    public static C4CarrierCandidate parse(C4TransportResponse transport) {
        Objects.requireNonNull(transport, "transport");
        if (transport.statusCode() != 200) {
            throw C4CarrierException.of(C4CarrierViolation.TRANSPORT_FAILURE, "statusCode");
        }
        if (transport.totalHeaderBytes() > MAX_TOTAL_HEADER_BYTES) {
            throw C4CarrierException.of(C4CarrierViolation.HEADERS_TOO_LARGE);
        }

        byte[] body = transport.body();
        if (body.length == 0 || body.length > MAX_BODY_BYTES) {
            throw C4CarrierException.of(C4CarrierViolation.BODY_SIZE, "body");
        }

        String encoded = singleEvidenceHeader(transport.headers());
        if (encoded.length() > MAX_ENCODED_CHARS) {
            throw C4CarrierException.of(C4CarrierViolation.CARRIER_SIZE, "header");
        }
        if (!encoded.equals(encoded.strip())) {
            throw C4CarrierException.of(C4CarrierViolation.BASE64_NONCANONICAL, "header");
        }

        byte[] decoded = decodeCanonicalBase64(encoded, MAX_DECODED_BYTES);
        String received = strictUtf8(decoded);
        JsonNode root = readJson(received);

        C4CarrierEnvelope envelope = parseEnvelope(root);
        if (!received.equals(new String(envelope.toCanonicalBytes(), StandardCharsets.US_ASCII))) {
            throw C4CarrierException.of(C4CarrierViolation.CARRIER_SCHEMA, "canonical");
        }

        String bodySha256 = C4CarrierSchema.sha256Hex(body);
        if (!bodySha256.equals(envelope.attestation().responseSha256())) {
            throw C4CarrierException.of(C4CarrierViolation.HASH_BINDING, "responseSha256");
        }

        C4CarrierCandidate.SenderBinding senderBinding = bindSender(transport, envelope);
        bindRelay(envelope, bodySha256);

        return new C4CarrierCandidate(
                envelope,
                bodySha256,
                envelope.attestation().canonicalSha256(),
                senderBinding,
                C4CarrierCandidate.AttestationState.UNVERIFIED_CANDIDATE,
                C4CarrierCandidate.KeyResolution.UNRESOLVED);
    }

    /**
     * Return the single evidence header value. Duplicates are collected across every
     * case-variant header name and rejected; a comma-joined value is treated as a
     * transport-coalesced duplicate rather than split.
     */
    private static String singleEvidenceHeader(Map<String, List<String>> headers) {
        List<String> values = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
            if (HEADER_NAME.equalsIgnoreCase(entry.getKey())) {
                for (String value : entry.getValue()) {
                    if (value == null) {
                        throw C4CarrierException.of(
                                C4CarrierViolation.FOLDED_HEADER, "header");
                    }
                    values.add(value);
                }
            }
        }
        if (values.isEmpty()) {
            throw C4CarrierException.of(C4CarrierViolation.MISSING_PROOF);
        }
        if (values.size() > 1) {
            throw C4CarrierException.of(C4CarrierViolation.DUPLICATE_HEADER);
        }
        String value = values.get(0);
        if (value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0) {
            throw C4CarrierException.of(C4CarrierViolation.FOLDED_HEADER);
        }
        if (value.indexOf(',') >= 0) {
            throw C4CarrierException.of(C4CarrierViolation.DUPLICATE_HEADER);
        }
        return value;
    }

    static C4CarrierEnvelope parseEnvelope(JsonNode root) {
        requireObject(root, ENVELOPE_KEYS, "envelope");
        int version = integer(root, "version");
        if (version != C4CarrierEnvelope.VERSION) {
            throw C4CarrierException.of(C4CarrierViolation.CARRIER_SCHEMA, "version");
        }
        C4CarrierEnvelope.Mode mode;
        String modeText = text(root, "mode");
        try {
            mode = C4CarrierEnvelope.Mode.valueOf(modeText);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw C4CarrierException.of(C4CarrierViolation.CARRIER_SCHEMA, "mode");
        }
        String keyId = text(root, "keyId");
        String signatureText = text(root, "signature");
        byte[] signature = decodeSignature(signatureText);
        C4EvidenceAttestation attestation = parseAttestation(object(root, "attestation"));
        C4RelayAttestation relay = parseRelay(root.get("relay"));
        return new C4CarrierEnvelope(version, mode, attestation, keyId, signature, relay);
    }

    private static C4RelayAttestation parseRelay(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        requireObject(node, RELAY_KEYS, "relay");
        return new C4RelayAttestation(
                text(node, "principal"),
                text(node, "receiptId"),
                text(node, "deliveredAt"),
                text(node, "keyId"),
                text(node, "responseSha256"),
                text(node, "attestationSha256"),
                decodeSignature(text(node, "signature")));
    }

    private static C4EvidenceAttestation parseAttestation(JsonNode node) {
        requireObject(node, ATTESTATION_KEYS, "attestation");
        return new C4EvidenceAttestation(
                integer(node, "schemaVersion"),
                text(node, "environmentId"),
                text(node, "producerPrincipal"),
                text(node, "evidenceId"),
                text(node, "instructionSha256"),
                text(node, "profile"),
                text(node, "authorizationId"),
                text(node, "transactionId"),
                text(node, "correlationId"),
                text(node, "instructionAttemptId"),
                text(node, "acquirerRef"),
                text(node, "merchantRef"),
                text(node, "terminalRef"),
                text(node, "paymentRef"),
                integer(node, "generation"),
                integer(node, "amountMinor"),
                text(node, "currency"),
                text(node, "STAN"),
                text(node, "RRN"),
                text(node, "DE7"),
                text(node, "sent"),
                text(node, "actor"),
                text(node, "stage"),
                text(node, "DE39"),
                text(node, "result"),
                text(node, "reason"),
                nullableText(node, "authorizationReference"),
                text(node, "availableAt"),
                text(node, "signingPrincipal"),
                text(node, "keyId"),
                text(node, "responseSha256"));
    }

    private static C4CarrierCandidate.SenderBinding bindSender(
            C4TransportResponse transport, C4CarrierEnvelope envelope) {
        String sender = transport.authenticatedSender();
        if (sender == null || sender.isEmpty()) {
            throw C4CarrierException.of(C4CarrierViolation.AUTHENTICATED_SENDER);
        }
        if (envelope.mode() == C4CarrierEnvelope.Mode.DIRECT) {
            if (!sender.equals(envelope.attestation().signingPrincipal())) {
                throw C4CarrierException.of(C4CarrierViolation.AUTHENTICATED_SENDER);
            }
            return C4CarrierCandidate.SenderBinding.DIRECT_SENDER_MATCHED;
        }
        if (!sender.equals(envelope.relay().principal())) {
            throw C4CarrierException.of(C4CarrierViolation.AUTHENTICATED_SENDER);
        }
        return C4CarrierCandidate.SenderBinding.RELAY_SENDER_MATCHED;
    }

    private static void bindRelay(C4CarrierEnvelope envelope, String bodySha256) {
        C4RelayAttestation relay = envelope.relay();
        if (relay == null) {
            return;
        }
        if (!relay.responseSha256().equals(bodySha256)
                || !relay.responseSha256().equals(envelope.attestation().responseSha256())) {
            throw C4CarrierException.of(C4CarrierViolation.RELAY_BINDING, "responseSha256");
        }
        if (!relay.attestationSha256().equals(envelope.attestation().canonicalSha256())) {
            throw C4CarrierException.of(
                    C4CarrierViolation.RELAY_BINDING, "attestationSha256");
        }
    }

    private static byte[] decodeSignature(String value) {
        if (value == null || value.length() > MAX_SIGNATURE_CHARS) {
            throw C4CarrierException.of(C4CarrierViolation.CARRIER_SCHEMA, "signature");
        }
        byte[] bytes = decodeCanonicalBase64(value, SIGNATURE_BYTES);
        if (bytes.length != SIGNATURE_BYTES) {
            throw C4CarrierException.of(C4CarrierViolation.CARRIER_SCHEMA, "signature");
        }
        return bytes;
    }

    /**
     * Strict RFC4648 standard padded base64. The decoded length is computed from the
     * encoded length and padding before any decode allocation; the value is then decoded
     * and re-encoded so non-zero pad bits and any non-canonical form are rejected.
     */
    private static byte[] decodeCanonicalBase64(String value, int maxDecodedBytes) {
        if (value == null || value.isEmpty() || value.length() % 4 != 0) {
            throw C4CarrierException.of(C4CarrierViolation.BASE64_NONCANONICAL, "base64");
        }
        int padding = 0;
        if (value.endsWith("==")) {
            padding = 2;
        } else if (value.endsWith("=")) {
            padding = 1;
        }
        long decodedLength = (value.length() / 4L) * 3L - padding;
        if (decodedLength < 0 || decodedLength > maxDecodedBytes) {
            throw C4CarrierException.of(C4CarrierViolation.CARRIER_SIZE, "base64");
        }
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(value);
        } catch (IllegalArgumentException e) {
            throw C4CarrierException.of(C4CarrierViolation.BASE64_NONCANONICAL, "base64");
        }
        if (!Base64.getEncoder().encodeToString(decoded).equals(value)) {
            throw C4CarrierException.of(C4CarrierViolation.BASE64_NONCANONICAL, "base64");
        }
        return decoded;
    }

    private static JsonNode readJson(String json) {
        try {
            JsonNode root = MAPPER.readTree(json);
            if (root == null || !root.isObject()) {
                throw C4CarrierException.of(C4CarrierViolation.CARRIER_SCHEMA);
            }
            return root;
        } catch (C4CarrierException e) {
            throw e;
        } catch (Exception e) {
            throw C4CarrierException.of(C4CarrierViolation.CARRIER_SCHEMA);
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
            throw C4CarrierException.of(C4CarrierViolation.CARRIER_SCHEMA);
        }
    }

    private static void requireObject(JsonNode node, Set<String> expected, String field) {
        if (node == null || !node.isObject()) {
            throw C4CarrierException.of(C4CarrierViolation.CARRIER_SCHEMA, field);
        }
        Set<String> actual = new LinkedHashSet<>();
        node.fieldNames().forEachRemaining(actual::add);
        if (!actual.equals(expected)) {
            String offending = actual.stream()
                    .filter(name -> !expected.contains(name))
                    .findFirst()
                    .orElse(field);
            throw C4CarrierException.of(C4CarrierViolation.CARRIER_SCHEMA, offending);
        }
    }

    private static JsonNode object(JsonNode parent, String field) {
        JsonNode node = parent.get(field);
        if (node == null || !node.isObject()) {
            throw C4CarrierException.of(C4CarrierViolation.CARRIER_SCHEMA, field);
        }
        return node;
    }

    private static String text(JsonNode parent, String field) {
        JsonNode node = parent.get(field);
        if (node == null || !node.isTextual()) {
            throw C4CarrierException.of(C4CarrierViolation.CARRIER_SCHEMA, field);
        }
        return node.textValue();
    }

    private static String nullableText(JsonNode parent, String field) {
        JsonNode node = parent.get(field);
        if (node == null) {
            throw C4CarrierException.of(C4CarrierViolation.CARRIER_SCHEMA, field);
        }
        if (node.isNull()) {
            return null;
        }
        if (!node.isTextual()) {
            throw C4CarrierException.of(C4CarrierViolation.CARRIER_SCHEMA, field);
        }
        return node.textValue();
    }

    private static int integer(JsonNode parent, String field) {
        JsonNode node = parent.get(field);
        if (node == null || !node.isIntegralNumber() || !node.canConvertToInt()) {
            throw C4CarrierException.of(C4CarrierViolation.CARRIER_SCHEMA, field);
        }
        return node.intValue();
    }
}
