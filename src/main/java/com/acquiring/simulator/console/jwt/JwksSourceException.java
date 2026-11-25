package com.acquiring.simulator.console.jwt;

/**
 * One failed key-set retrieval or parse.
 *
 * <p>The message is a fixed label. It never carries a token, an issuer response body, a URL query
 * or key material, so a retrieval failure can be traced without disclosing credential material.</p>
 */
public final class JwksSourceException extends RuntimeException {

    public JwksSourceException(String message) {
        super(message);
    }

    public JwksSourceException(String message, Throwable cause) {
        super(message, cause);
    }
}
