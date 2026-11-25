package com.acquiring.simulator.console;

import java.util.Objects;

/**
 * Identity fields the API Gateway integration event would supply, kept separate from the HTTP
 * request because they are platform context rather than caller headers.
 *
 * <p>This slice has no API Gateway event adapter, so the values are an explicit seam. A missing
 * value is {@code null}, never invented. The denial record still carries the field.</p>
 */
public record ConsoleRequestIdentity(String requestId, String sourceIp) {

    /** No platform-supplied identity. */
    public static final ConsoleRequestIdentity UNKNOWN = new ConsoleRequestIdentity(null, null);

    public ConsoleRequestIdentity {
        // Fields may be null on purpose; no default identity is fabricated.
    }

    /** Defensive label that never renders a credential. */
    @Override
    public String toString() {
        return "ConsoleRequestIdentity[requestId=" + requestId
                + ", sourceIp=" + sourceIp + "]";
    }
}
