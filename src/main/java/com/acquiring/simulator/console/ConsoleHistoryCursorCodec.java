package com.acquiring.simulator.console;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Objects;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Signed opaque cursor codec for the read-only history route (D-6/R-2).
 *
 * <p>The cursor binds the fixed snapshot ({@code H}, {@code R}, {@code F}), the last covered
 * sequence, the verified token issuer, the current principal, the trusted environment, the mandate
 * fingerprint, the target-specific terminal when one exists, the read generation and the pass
 * counters. It reveals no token and no key: it is opaque in the sense that a client cannot mint or
 * alter one without the HMAC key, and every field is re-checked against the current principal and
 * mandate before disclosure.</p>
 *
 * <p>The signing key is an injected collaborator. This slice does not invent an environment-variable
 * name for it; the deployed composition must supply one. The wire form is
 * {@code base64url(payload) + "." + base64url(HMAC-SHA256(payload))} with padding removed.</p>
 *
 * <p>The wire handles in the design fixtures are documentary symbols, not usable cursors; this codec
 * is the actual implementation and its fixtures are generated inside the test process.</p>
 */
public final class ConsoleHistoryCursorCodec {

    /** Version of the payload schema. */
    public static final int VERSION = 1;

    /** Hard bound on a presented cursor, so a huge token is refused before any parsing work. */
    public static final int MAX_CURSOR_LENGTH = 4096;

    private static final String ALGORITHM = "HmacSHA256";
    private static final int MINIMUM_KEY_BYTES = 32;

    private final String keyId;
    private final byte[] key;
    private final ObjectMapper json = new ObjectMapper();

    public ConsoleHistoryCursorCodec(String keyId, byte[] key) {
        if (keyId == null || keyId.isBlank()) {
            throw new IllegalArgumentException("cursor keyId is required");
        }
        Objects.requireNonNull(key, "key");
        if (key.length < MINIMUM_KEY_BYTES) {
            throw new IllegalArgumentException(
                    "cursor key must be at least " + MINIMUM_KEY_BYTES + " bytes");
        }
        this.keyId = keyId;
        this.key = key.clone();
    }

    /** Mint a cursor for the binding, valid for {@code ttlSeconds} from {@code issuedAt}. */
    public String mint(Binding binding, Instant issuedAt, int ttlSeconds) {
        Objects.requireNonNull(binding, "binding");
        Objects.requireNonNull(issuedAt, "issuedAt");
        if (ttlSeconds < 1) {
            throw new IllegalArgumentException("cursor ttl must be positive");
        }
        Instant expiresAt = issuedAt.plusSeconds(ttlSeconds);
        ObjectNode payload = payload(binding, issuedAt, expiresAt);
        byte[] payloadBytes;
        try {
            payloadBytes = json.writeValueAsBytes(payload);
        } catch (IOException impossible) {
            throw new IllegalStateException("cursor payload cannot be encoded", impossible);
        }
        return encode(payloadBytes);
    }

    /**
     * Verify the signature and decode the binding.
     *
     * <p>The refusal classification follows C-3.3: a signature that does not verify, or a signed
     * payload that is structurally malformed, is {@link CursorException.Kind#INVALID} ({@code 400}
     * {@code INVALID_CURSOR}); a signature-valid payload whose schema version is retired, or which
     * lacks a member the current representation requires, is {@link CursorException.Kind#RETIRED}
     * ({@code 410 RESYNC_REQUIRED}); a signed expiry already reached is
     * {@link CursorException.Kind#EXPIRED}. Structural malformation is judged before retirement so
     * the {@code 400} malformed-payload step precedes the {@code 410} retired-schema step.</p>
     *
     * @throws CursorException {@link CursorException.Kind#INVALID} for unknown/tampered/malformed
     *     input, {@link CursorException.Kind#RETIRED} for a signature-valid retired schema or
     *     representation, {@link CursorException.Kind#EXPIRED} when the signed expiry has been
     *     reached
     */
    public Binding open(String cursor, Instant now) {
        Objects.requireNonNull(now, "now");
        if (cursor == null || cursor.isEmpty() || cursor.length() > MAX_CURSOR_LENGTH) {
            throw CursorException.invalid();
        }
        int dot = cursor.indexOf('.');
        if (dot <= 0 || dot != cursor.lastIndexOf('.') || dot == cursor.length() - 1) {
            throw CursorException.invalid();
        }
        byte[] payloadBytes;
        byte[] signature;
        try {
            payloadBytes = Base64.getUrlDecoder().decode(cursor.substring(0, dot));
            signature = Base64.getUrlDecoder().decode(cursor.substring(dot + 1));
        } catch (IllegalArgumentException malformed) {
            throw CursorException.invalid();
        }
        if (!MessageDigest.isEqual(sign(payloadBytes), signature)) {
            throw CursorException.invalid();
        }
        Binding binding = parse(payloadBytes);
        if (now.isBefore(binding.issuedAt())) {
            // A cursor from the future is not evidence of anything; refuse rather than clamp.
            throw CursorException.invalid();
        }
        if (!now.isBefore(binding.expiresAt())) {
            throw new CursorException(CursorException.Kind.EXPIRED);
        }
        return binding;
    }

    private String encode(byte[] payloadBytes) {
        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        return encoder.encodeToString(payloadBytes) + "." + encoder.encodeToString(sign(payloadBytes));
    }

    private ObjectNode payload(Binding binding, Instant issuedAt, Instant expiresAt) {
        ObjectNode node = json.createObjectNode();
        node.put("version", VERSION);
        node.put("keyId", keyId);
        node.put("issuer", binding.issuer());
        node.put("subject", binding.subject());
        node.put("authorizedParty", binding.authorizedParty());
        node.put("audience", binding.audience());
        node.put("environment", binding.environment());
        node.put("mandateFingerprint", binding.mandateFingerprint());
        node.put("generation", binding.generation());
        node.put("acquirerRef", binding.acquirerRef());
        node.put("merchantRef", binding.merchantRef());
        node.put("terminalRef", binding.terminalRef());
        node.put("transactionId", binding.transactionId());
        node.put("snapshotHead", binding.snapshotHead());
        node.put("expiryRevision", binding.expiryRevision());
        node.put("firstRetainedSequence", binding.firstRetainedSequence());
        node.put("lastSequence", binding.lastSequence());
        node.put("passedEvents", binding.passedEvents());
        node.put("passedBytes", binding.passedBytes());
        node.put("issuedAt", issuedAt.getEpochSecond());
        node.put("expiresAt", expiresAt.getEpochSecond());
        return node;
    }

    private Binding parse(byte[] payloadBytes) {
        JsonNode node;
        try {
            node = json.readTree(payloadBytes);
        } catch (IOException malformed) {
            throw CursorException.invalid();
        }
        if (node == null || !node.isObject()) {
            // A signed payload that is not a JSON object is malformed, not a retired generation.
            throw CursorException.invalid();
        }

        // C-3.3 has the malformed-payload 400 before the retired-schema 410, so a wrong JSON type
        // is refused as malformed before any schema-generation decision is taken.
        JsonNode version = node.get("version");
        if (version != null && !version.isNull() && !version.isIntegralNumber()) {
            throw CursorException.invalid();
        }
        JsonNode keyIdNode = node.get("keyId");
        if (keyIdNode != null && !keyIdNode.isNull() && !keyIdNode.isTextual()) {
            throw CursorException.invalid();
        }

        // C-1.5: a signature-valid payload whose version is not the current one — or that carries
        // no version marker at all — is a retired schema, not a tampered handle.
        if (version == null || version.isNull() || version.asLong() != VERSION) {
            throw CursorException.retired();
        }

        String presentedKeyId = text(node, "keyId");
        if (presentedKeyId == null) {
            // A member the current representation requires is absent: retired, per C-1.5.
            throw CursorException.retired();
        }
        if (!keyId.equals(presentedKeyId)) {
            // Contradictory handle: signed with the current key but naming another generation.
            throw CursorException.invalid();
        }

        try {
            Binding binding = new Binding(
                    requiredText(node, "issuer"),
                    requiredText(node, "subject"),
                    requiredText(node, "authorizedParty"),
                    requiredText(node, "audience"),
                    requiredText(node, "environment"),
                    requiredText(node, "mandateFingerprint"),
                    requiredText(node, "generation"),
                    requiredText(node, "acquirerRef"),
                    requiredText(node, "merchantRef"),
                    requiredNullableText(node, "terminalRef"),
                    requiredText(node, "transactionId"),
                    requiredNumber(node, "snapshotHead"),
                    requiredNumber(node, "expiryRevision"),
                    requiredNumber(node, "firstRetainedSequence"),
                    requiredNumber(node, "lastSequence"),
                    requiredNumber(node, "passedEvents"),
                    requiredNumber(node, "passedBytes"),
                    Instant.ofEpochSecond(requiredNumber(node, "issuedAt")),
                    Instant.ofEpochSecond(requiredNumber(node, "expiresAt")));
            return binding;
        } catch (CursorException refused) {
            throw refused;
        } catch (RuntimeException malformed) {
            throw CursorException.invalid();
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private static String requiredText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            // C-1.5: a member required by the representation now in force is absent, not tampered.
            throw CursorException.retired();
        }
        if (!value.isTextual() || value.asText().isBlank()) {
            throw CursorException.invalid();
        }
        return value.asText();
    }

    /** A member that must be present but may be JSON {@code null} (C-4.3, e.g. terminalRef). */
    private static String requiredNullableText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null) {
            throw CursorException.retired();
        }
        if (value.isNull()) {
            return null;
        }
        if (!value.isTextual() || value.asText().isBlank()) {
            throw CursorException.invalid();
        }
        return value.asText();
    }

    private static long requiredNumber(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            throw CursorException.retired();
        }
        if (!value.isIntegralNumber() || !value.canConvertToLong()) {
            throw CursorException.invalid();
        }
        return value.asLong();
    }

    private byte[] sign(byte[] payloadBytes) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(key, ALGORITHM));
            return mac.doFinal(payloadBytes);
        } catch (GeneralSecurityException failure) {
            throw new IllegalStateException("HMAC-SHA256 is unavailable", failure);
        }
    }

    /** A stable, non-reversible fingerprint of the trusted mandate. */
    public static String mandateFingerprint(
            String callerId, String acquirerRef, String merchantRef, java.util.Set<String> routes) {
        StringBuilder material = new StringBuilder();
        material.append(callerId).append('\u0000')
                .append(acquirerRef).append('\u0000')
                .append(merchantRef).append('\u0000');
        routes.stream().sorted().forEach(route -> material.append(route).append(','));
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(
                    digest.digest(material.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    /**
     * One signed cursor payload. {@code terminalRef} is {@code null} when the trusted context is not
     * terminal-specific; otherwise it binds the target terminal. {@code issuer} is always bound so a
     * cursor minted under another issuer cannot be presented as this identity.
     */
    public record Binding(
            String issuer,
            String subject,
            String authorizedParty,
            String audience,
            String environment,
            String mandateFingerprint,
            String generation,
            String acquirerRef,
            String merchantRef,
            String terminalRef,
            String transactionId,
            long snapshotHead,
            long expiryRevision,
            long firstRetainedSequence,
            long lastSequence,
            long passedEvents,
            long passedBytes,
            Instant issuedAt,
            Instant expiresAt) {

        public Binding {
            Objects.requireNonNull(issuer, "issuer");
            Objects.requireNonNull(subject, "subject");
            Objects.requireNonNull(authorizedParty, "authorizedParty");
            Objects.requireNonNull(audience, "audience");
            Objects.requireNonNull(environment, "environment");
            Objects.requireNonNull(mandateFingerprint, "mandateFingerprint");
            Objects.requireNonNull(generation, "generation");
            Objects.requireNonNull(acquirerRef, "acquirerRef");
            Objects.requireNonNull(merchantRef, "merchantRef");
            Objects.requireNonNull(transactionId, "transactionId");
            Objects.requireNonNull(issuedAt, "issuedAt");
            Objects.requireNonNull(expiresAt, "expiresAt");
            if (snapshotHead < 0 || expiryRevision < 0 || firstRetainedSequence < 0
                    || lastSequence < 0 || passedEvents < 0 || passedBytes < 0) {
                throw new IllegalArgumentException("cursor numbers cannot be negative");
            }
        }

        /** Never renders signature material; this type holds decoded binding fields only. */
        @Override
        public String toString() {
            return "Binding[issuer=" + issuer
                    + ", subject=" + subject
                    + ", environment=" + environment
                    + ", transactionId=" + transactionId
                    + ", terminalRef=" + terminalRef
                    + ", snapshotHead=" + snapshotHead
                    + ", expiryRevision=" + expiryRevision
                    + ", firstRetainedSequence=" + firstRetainedSequence
                    + ", lastSequence=" + lastSequence + "]";
        }
    }

    /** A refused cursor, classified as unknown/tampered, retired schema, or expired. */
    public static final class CursorException extends RuntimeException {

        private final Kind kind;

        private CursorException(Kind kind) {
            super("history cursor refused: " + kind);
            this.kind = kind;
        }

        private static CursorException invalid() {
            return new CursorException(Kind.INVALID);
        }

        private static CursorException retired() {
            return new CursorException(Kind.RETIRED);
        }

        public Kind kind() {
            return kind;
        }

        public enum Kind {
            /** Unknown, tampered or structurally malformed: {@code 400 INVALID_CURSOR}. */
            INVALID,
            /** Signature verified but the schema/representation is retired: {@code 410}. */
            RETIRED,
            /** Signed expiry reached: {@code 410}. */
            EXPIRED
        }
    }
}
