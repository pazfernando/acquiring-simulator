package com.acquiring.simulator.channel;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Strict API Gateway HTTP API payload v2 decoder for one internal octet-stream response.
 *
 * <p>It enforces the payload v2 response shape, the approved C2 media type and profile, no
 * compression, the platform {@code isBase64Encoded=true} flag, canonical padded RFC4648
 * base64 and a 1–2048-byte decoded body. The exact decoded bytes are retained without
 * reserialization. Header values are kept multi-valued for the C4 parser.</p>
 *
 * <p>It does not read a sender, principal or authorizer value from the response and does
 * not verify C4 evidence, parse ISO8583, admit business evidence or emit a channel
 * response. Identity-looking headers are inert here; only the explicit trusted context
 * supplied to {@link C4HttpCarriageBridge} can bind a sender.</p>
 */
public final class HttpApiV2ResponseDecoder {

    /** Approved C2 media type. */
    public static final String MEDIA_TYPE = HttpApiV2Wire.MEDIA_TYPE;

    /** Approved C2 profile header value. */
    public static final String PROFILE = HttpApiV2Wire.PROFILE;

    /** Maximum decoded octet-stream body bytes. */
    public static final int MAX_BODY_BYTES = HttpApiV2Wire.MAX_BODY_BYTES;

    private HttpApiV2ResponseDecoder() {
    }

    /**
     * Decode one payload v2 response event into its exact octet-stream body.
     *
     * @throws HttpApiV2TransportException on the first transport-rule violation
     */
    public static HttpApiV2Response decode(byte[] rawEvent) {
        Objects.requireNonNull(rawEvent, "rawEvent");
        JsonNode event = HttpApiV2Wire.readEvent(rawEvent);

        JsonNode status = event.get("statusCode");
        if (status == null || !status.isInt()) {
            throw HttpApiV2TransportException.of(
                    HttpApiV2TransportViolation.EVENT_SCHEMA, "statusCode");
        }

        Map<String, List<String>> headers =
                HttpApiV2Wire.readHeaders(event.get("headers"), "headers");

        String contentType = HttpApiV2Wire.singleHeader(headers, "Content-Type",
                HttpApiV2TransportViolation.UNSUPPORTED_MEDIA,
                HttpApiV2TransportViolation.DUPLICATE_HEADER);
        if (!MEDIA_TYPE.equalsIgnoreCase(contentType)) {
            throw HttpApiV2TransportException.of(
                    HttpApiV2TransportViolation.UNSUPPORTED_MEDIA, "Content-Type");
        }

        String profile = HttpApiV2Wire.singleHeader(headers, "X-Simulator-Profile",
                HttpApiV2TransportViolation.UNSUPPORTED_PROFILE,
                HttpApiV2TransportViolation.DUPLICATE_HEADER);
        if (!PROFILE.equals(profile)) {
            throw HttpApiV2TransportException.of(
                    HttpApiV2TransportViolation.UNSUPPORTED_PROFILE, "X-Simulator-Profile");
        }

        List<String> contentEncoding = HttpApiV2Wire.headerValues(headers, "Content-Encoding");
        if (contentEncoding.size() > 1) {
            throw HttpApiV2TransportException.of(
                    HttpApiV2TransportViolation.DUPLICATE_HEADER, "Content-Encoding");
        }
        if (contentEncoding.size() == 1
                && !"identity".equalsIgnoreCase(contentEncoding.get(0))) {
            throw HttpApiV2TransportException.of(
                    HttpApiV2TransportViolation.COMPRESSION, "Content-Encoding");
        }

        HttpApiV2Wire.requireBase64Flag(event);
        JsonNode bodyNode = event.get("body");
        if (bodyNode == null || !bodyNode.isTextual()) {
            throw HttpApiV2TransportException.of(
                    HttpApiV2TransportViolation.UNSUPPORTED_ENCODING, "body");
        }
        byte[] body = HttpApiV2Wire.decodeCanonicalBase64(bodyNode.asText());
        HttpApiV2Wire.requireBodySize(body.length);
        if (HttpApiV2Wire.isCompressed(body)) {
            throw HttpApiV2TransportException.of(
                    HttpApiV2TransportViolation.COMPRESSION, "body");
        }

        return new HttpApiV2Response(status.intValue(), headers, body);
    }
}
