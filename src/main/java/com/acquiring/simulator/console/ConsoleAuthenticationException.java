package com.acquiring.simulator.console;

/**
 * One refused console credential, with a stable reason.
 *
 * <p>Refusal is always fail-closed: the reason names which check rejected the request and
 * no partial identity, claims, token value or header value is carried. The message is a
 * fixed label, so a refusal can be traced without ever logging the credential.</p>
 *
 * <p>The route maps {@link Reason} to the C3 authentication/context-denial row. That
 * mapping is a transport decision and is deliberately kept out of this type.</p>
 */
public final class ConsoleAuthenticationException extends RuntimeException {

    private final Reason reason;

    public ConsoleAuthenticationException(Reason reason) {
        this(reason, null);
    }

    public ConsoleAuthenticationException(Reason reason, Throwable cause) {
        super("console credential refused: " + reason, cause);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }

    /** Every way the console boundary can refuse a credential. */
    public enum Reason {
        /** No {@code Authorization} header, or more than one. */
        MISSING_TOKEN,
        /** Not a bearer credential, empty, or not decodable as a verified claim set. */
        MALFORMED_TOKEN,
        /** JWS algorithm is absent or not in the configured allowlist ({@code none}/HMAC included). */
        UNSUPPORTED_ALGORITHM,
        /** No {@code kid}, or no configured key matches it after a rotation refresh. */
        UNKNOWN_KEY,
        /** A key matched but the compact-JWS signature did not verify. */
        BAD_SIGNATURE,
        /** The key set could not be retrieved or parsed; no cached authority is substituted. */
        KEY_SOURCE_UNAVAILABLE,
        /** Token issued by an issuer other than the configured one. */
        WRONG_ISSUER,
        /** Audience list does not contain the console API resource. */
        WRONG_AUDIENCE,
        /** Authorized party is not the single public console client. */
        WRONG_AUTHORIZED_PARTY,
        /** Subject is not the single configured demo subject. */
        WRONG_SUBJECT,
        /** Token lacks the console submission scope. */
        MISSING_SCOPE,
        /** Token carries the other resource class's scope; classes are disjoint, not narrowed. */
        WRONG_SCOPE,
        /** Expiry is absent or already reached. */
        EXPIRED,
        /** Not-before or issue time is in the future. */
        NOT_YET_VALID,
        /** Token lifetime is absent, zero/negative or exceeds the configured ceiling. */
        TOKEN_LIFETIME_EXCEEDED
    }
}
