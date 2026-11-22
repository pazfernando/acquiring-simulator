package com.acquiring.simulator.presenter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Objects;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Signed, opaque catch-up cursors for the presenter read model (observation contract O6).
 *
 * <p>A cursor binds the validated demo subject, the relay's fixed environment/business context, the
 * transaction, the subscription generation, the fixed snapshot head, the expiry revision and the
 * last covered source sequence, plus a short absolute expiry. The key lives only in relay memory, so
 * a cursor cannot be forged or replayed in another process.</p>
 *
 * <p>Failure classes are distinct and map to the approved HTTP outcomes: a malformed or
 * authentication-failing token is {@code TAMPERED} (400); a valid token for a different scope is
 * {@code SCOPE} (403); a valid, correctly scoped but expired token is {@code EXPIRED} (410). A
 * cursor is never silently reset to the newest event.</p>
 */
public final class PresenterCursorCodec {

    private static final Duration MAX_LIFETIME = Duration.ofMinutes(15);
    private static final ObjectMapper JSON = new ObjectMapper();

    private final Clock clock;
    private final Duration lifetime;
    private final SecretKeySpec key;

    public PresenterCursorCodec(Clock clock, Duration lifetime) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.lifetime = Objects.requireNonNull(lifetime, "lifetime");
        if (lifetime.isNegative() || lifetime.isZero()
                || lifetime.compareTo(MAX_LIFETIME) > 0) {
            throw new IllegalArgumentException("cursor lifetime must be 1s..15min");
        }
        byte[] material = new byte[32];
        new SecureRandom().nextBytes(material);
        this.key = new SecretKeySpec(material, "HmacSHA256");
    }

    /** Encode one scoped cursor state. */
    public String encode(CursorState state) {
        Objects.requireNonNull(state, "state");
        Instant expiresAt = clock.instant().plus(lifetime);
        ObjectNode payload = JSON.createObjectNode();
        payload.put("sub", state.binding().subject());
        payload.put("env", state.binding().environmentId());
        payload.put("acq", state.binding().acquirerRef());
        payload.put("mid", state.binding().merchantRef());
        payload.put("tx", state.binding().transactionId());
        payload.put("gen", state.binding().generation());
        payload.put("h", state.snapshotHead());
        payload.put("r", state.expiryRevision());
        payload.put("last", state.lastSequence());
        payload.put("exp", expiresAt.getEpochSecond());
        byte[] body = payload.toString().getBytes(StandardCharsets.UTF_8);
        byte[] signature = sign(body);
        return base64(body) + "." + base64(signature);
    }

    /** Decode and validate a cursor against the caller's current scope. */
    public CursorState decode(String token, Binding expected) {
        Objects.requireNonNull(expected, "expected");
        if (token == null || token.isBlank() || token.length() > 4096) {
            throw new CursorException(CursorFailure.TAMPERED);
        }
        int dot = token.indexOf('.');
        if (dot <= 0 || dot == token.length() - 1) {
            throw new CursorException(CursorFailure.TAMPERED);
        }
        byte[] body;
        byte[] signature;
        try {
            body = Base64.getUrlDecoder().decode(token.substring(0, dot));
            signature = Base64.getUrlDecoder().decode(token.substring(dot + 1));
        } catch (IllegalArgumentException e) {
            throw new CursorException(CursorFailure.TAMPERED);
        }
        byte[] expectedSignature = sign(body);
        if (!MessageDigest.isEqual(expectedSignature, signature)) {
            throw new CursorException(CursorFailure.TAMPERED);
        }
        JsonNode payload;
        try {
            payload = JSON.readTree(body);
        } catch (Exception e) {
            throw new CursorException(CursorFailure.TAMPERED);
        }
        CursorState state = new CursorState(
                new Binding(
                        text(payload, "sub"),
                        text(payload, "env"),
                        text(payload, "acq"),
                        text(payload, "mid"),
                        text(payload, "tx"),
                        number(payload, "gen")),
                number(payload, "h"),
                number(payload, "r"),
                number(payload, "last"),
                Instant.ofEpochSecond(number(payload, "exp")));
        if (!state.binding().equals(expected)) {
            throw new CursorException(CursorFailure.SCOPE);
        }
        if (!clock.instant().isBefore(state.expiresAt())) {
            throw new CursorException(CursorFailure.EXPIRED);
        }
        return state;
    }

    private byte[] sign(byte[] body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(key);
            return mac.doFinal(body);
        } catch (Exception e) {
            throw new IllegalStateException("cursor signing unavailable", e);
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual()) {
            throw new CursorException(CursorFailure.TAMPERED);
        }
        return value.textValue();
    }

    private static long number(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.canConvertToLong()) {
            throw new CursorException(CursorFailure.TAMPERED);
        }
        return value.longValue();
    }

    private static String base64(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** The validated scope one cursor is bound to. */
    public record Binding(String subject, String environmentId, String acquirerRef,
            String merchantRef, String transactionId, long generation) {
        public Binding {
            Objects.requireNonNull(subject, "subject");
            Objects.requireNonNull(environmentId, "environmentId");
            Objects.requireNonNull(acquirerRef, "acquirerRef");
            Objects.requireNonNull(merchantRef, "merchantRef");
            Objects.requireNonNull(transactionId, "transactionId");
            if (generation < 0) {
                throw new IllegalArgumentException("generation cannot be negative");
            }
        }
    }

    /** Fixed snapshot state of one cursor. */
    public record CursorState(Binding binding, long snapshotHead, long expiryRevision,
            long lastSequence, Instant expiresAt) {
        public CursorState {
            Objects.requireNonNull(binding, "binding");
            Objects.requireNonNull(expiresAt, "expiresAt");
            if (snapshotHead < 0 || expiryRevision < 0 || lastSequence < 0) {
                throw new IllegalArgumentException("cursor counters cannot be negative");
            }
        }
    }

    /** Failure class with its approved HTTP status. */
    public enum CursorFailure {
        TAMPERED(400),
        SCOPE(403),
        EXPIRED(410);

        private final int httpStatus;

        CursorFailure(int httpStatus) {
            this.httpStatus = httpStatus;
        }

        public int httpStatus() {
            return httpStatus;
        }
    }

    /** Typed cursor failure; never carries the token. */
    public static final class CursorException extends RuntimeException {
        private final CursorFailure failure;

        public CursorException(CursorFailure failure) {
            super(failure.name());
            this.failure = failure;
        }

        public CursorFailure failure() {
            return failure;
        }
    }
}
