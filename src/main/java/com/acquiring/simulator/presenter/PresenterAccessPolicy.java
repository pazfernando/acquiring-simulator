package com.acquiring.simulator.presenter;

import java.util.Locale;
import java.util.Objects;

/**
 * Strict loopback Host/Origin policy for the presenter relay (channel contract O4/C6).
 *
 * <p>The relay is reachable only through {@code 127.0.0.1} or {@code localhost} on the exact port it
 * listens on. There is no wildcard CORS, no DNS-rebinding-friendly Host acceptance and no cross-site
 * POST: unsafe requests must carry a same-origin {@code Origin} and the relay request header. The
 * header alone is not authentication; the HttpOnly session cookie remains the authority.</p>
 */
public final class PresenterAccessPolicy {

    /** Required custom header on every state-changing relay request (CSRF defence). */
    public static final String REQUEST_HEADER = "X-Presenter-Request";

    private final int port;

    public PresenterAccessPolicy(int port) {
        if (port < 0 || port > 65535) {
            throw new IllegalArgumentException("port must be 0..65535");
        }
        this.port = port;
    }

    /** True when the Host header names this loopback listener and nothing else. */
    public boolean hostAllowed(String hostHeader) {
        if (hostHeader == null || hostHeader.isBlank()) {
            return false;
        }
        String value = hostHeader.trim().toLowerCase(Locale.ROOT);
        String host;
        int candidatePort;
        int colon = value.lastIndexOf(':');
        if (colon > 0 && value.indexOf(']') < 0) {
            host = value.substring(0, colon);
            try {
                candidatePort = Integer.parseInt(value.substring(colon + 1));
            } catch (NumberFormatException e) {
                return false;
            }
        } else {
            host = value;
            candidatePort = 80;
        }
        return ("127.0.0.1".equals(host) || "localhost".equals(host))
                && candidatePort == port;
    }

    /** Same-origin check for an unsafe request; a missing Origin is refused. */
    public boolean originAllowed(String origin) {
        if (origin == null || origin.isBlank()) {
            return false;
        }
        String value = origin.trim().toLowerCase(Locale.ROOT);
        return value.equals("http://127.0.0.1:" + port)
                || value.equals("http://localhost:" + port);
    }

    /** The relay accepts a state change only with same-origin and the explicit request header. */
    public boolean sameOriginStateChange(String origin, String requestHeader) {
        return originAllowed(origin) && "1".equals(requestHeader);
    }

    /** WebSocket handshake policy uses the same origin rule (a browser always sends Origin). */
    public boolean webSocketAllowed(String hostHeader, String origin) {
        return hostAllowed(hostHeader) && originAllowed(origin);
    }

    @Override
    public String toString() {
        return "PresenterAccessPolicy[port=" + port + "]";
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof PresenterAccessPolicy that && port == that.port;
    }

    @Override
    public int hashCode() {
        return Objects.hash(port);
    }
}
