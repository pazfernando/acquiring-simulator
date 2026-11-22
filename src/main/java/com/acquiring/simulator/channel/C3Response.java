package com.acquiring.simulator.channel;

import java.util.Objects;

/**
 * Typed, immutable C3 response pair: the HTTP status and the closed JSON envelope.
 *
 * <p>The status is a transport selection derived from the precedence branch, never from
 * DE39 and never from a caller-declared outcome. It is an enum so a branch cannot emit a
 * status outside the channel contract's allowlist.</p>
 */
public record C3Response(HttpStatus httpStatus, C3ResponseEnvelope envelope) {

    public C3Response {
        Objects.requireNonNull(httpStatus, "httpStatus");
        Objects.requireNonNull(envelope, "envelope");
    }

    /** HTTP statuses reachable by the approved C3 precedence table. */
    public enum HttpStatus {
        OK(200),
        ACCEPTED(202),
        BAD_REQUEST(400),
        UNAUTHORIZED(401),
        FORBIDDEN(403),
        NOT_FOUND(404),
        CONFLICT(409),
        PAYLOAD_TOO_LARGE(413),
        UNSUPPORTED_MEDIA_TYPE(415),
        SERVICE_UNAVAILABLE(503);

        private final int code;

        HttpStatus(int code) {
            this.code = code;
        }

        /** Numeric status for the HTTP adapter; this type performs no transport I/O. */
        public int code() {
            return code;
        }
    }
}
