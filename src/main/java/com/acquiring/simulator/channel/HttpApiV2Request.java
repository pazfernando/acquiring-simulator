package com.acquiring.simulator.channel;

import java.util.Objects;

/**
 * One admitted C2 internal-route request body after strict payload v2 decoding.
 *
 * <p>This record contains only transport facts: the approved media type, the approved
 * profile and the exact decoded octet-stream bytes. It deliberately carries no sender,
 * principal, JWT claim, environment or header map, because none of those may be asserted
 * by the caller. The trusted context is supplied separately by the platform boundary.</p>
 */
public record HttpApiV2Request(String mediaType, String profile, byte[] body) {

    public HttpApiV2Request {
        Objects.requireNonNull(mediaType, "mediaType");
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(body, "body");
        body = body.clone();
    }

    @Override
    public byte[] body() {
        return body.clone();
    }

    /** Never renders the body bytes or a base64 form. */
    @Override
    public String toString() {
        return "HttpApiV2Request[mediaType=" + mediaType
                + ", profile=" + profile
                + ", bodyLength=" + body.length + "]";
    }
}
