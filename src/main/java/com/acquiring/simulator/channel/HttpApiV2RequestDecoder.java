package com.acquiring.simulator.channel;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Strict API Gateway HTTP API payload v2 decoder for the approved internal C2 route.
 *
 * <p>Decoding is pure transport work. It requires payload {@code version=2.0}, the exact
 * {@code POST /internal/sim-iso87a/v1/authorizations} route, a caller-supplied identity
 * assertion to be absent, {@code application/octet-stream}, {@code X-Simulator-Profile:
 * SIM-ISO87A-v1}, no compression, the platform {@code isBase64Encoded=true} flag and
 * canonical padded RFC4648 base64 of a 1–2048-byte body. The exact decoded bytes are
 * retained without text conversion or reserialization.</p>
 *
 * <p>It never reads {@code requestContext.authorizer}, a JWT claim, an {@code X-*} identity
 * header or any body field as identity. It creates no {@link C4AuthenticatedTransportContext}
 * and calls no business, ISO8583, storage or downstream service. Refusals happen before any
 * such call, so a route can map them to a sanitized 400/413/415 without admitting work.</p>
 */
public final class HttpApiV2RequestDecoder {

    /** Approved internal C2 route. */
    public static final String INTERNAL_PATH = HttpApiV2Wire.INTERNAL_PATH;

    /** Approved C2 media type. */
    public static final String MEDIA_TYPE = HttpApiV2Wire.MEDIA_TYPE;

    /** Approved C2 profile header value. */
    public static final String PROFILE = HttpApiV2Wire.PROFILE;

    /** Maximum decoded octet-stream body bytes. */
    public static final int MAX_BODY_BYTES = HttpApiV2Wire.MAX_BODY_BYTES;

    private HttpApiV2RequestDecoder() {
    }

    /**
     * Decode one payload v2 event into the exact octet-stream request.
     *
     * @throws HttpApiV2TransportException on the first transport-rule violation; the
     *     exception is value-free and is not a channel response
     */
    public static HttpApiV2Request decode(byte[] rawEvent) {
        Objects.requireNonNull(rawEvent, "rawEvent");
        JsonNode event = HttpApiV2Wire.readEvent(rawEvent);

        requireText(event, "version", "2.0");
        JsonNode path = event.get("rawPath");
        if (path == null || !path.isTextual() || !INTERNAL_PATH.equals(path.asText())) {
            throw HttpApiV2TransportException.of(
                    HttpApiV2TransportViolation.ROUTE, "rawPath");
        }
        JsonNode method = event.at("/requestContext/http/method");
        if (!method.isTextual() || !"POST".equals(method.asText())) {
            throw HttpApiV2TransportException.of(
                    HttpApiV2TransportViolation.ROUTE, "method");
        }

        Map<String, List<String>> headers =
                HttpApiV2Wire.readHeaders(event.get("headers"), "headers");
        HttpApiV2Wire.refuseIdentityAssertions(headers);

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

        requireContentLength(headers, body.length);
        return new HttpApiV2Request(MEDIA_TYPE, PROFILE, body);
    }

    private static void requireText(JsonNode event, String name, String expected) {
        JsonNode node = event.get(name);
        if (node == null || !node.isTextual() || !expected.equals(node.asText())) {
            throw HttpApiV2TransportException.of(
                    HttpApiV2TransportViolation.EVENT_SCHEMA, name);
        }
    }

    private static void requireContentLength(Map<String, List<String>> headers, int actual) {
        List<String> values = HttpApiV2Wire.headerValues(headers, "Content-Length");
        if (values.isEmpty()) {
            return;
        }
        if (values.size() > 1) {
            throw HttpApiV2TransportException.of(
                    HttpApiV2TransportViolation.DUPLICATE_HEADER, "Content-Length");
        }
        String value = values.get(0);
        if (value == null || value.isEmpty() || !value.matches("[0-9]{1,10}")) {
            throw HttpApiV2TransportException.of(
                    HttpApiV2TransportViolation.CONTENT_LENGTH_MISMATCH, "Content-Length");
        }
        int declared;
        try {
            declared = Integer.parseInt(value);
        } catch (NumberFormatException overflow) {
            throw HttpApiV2TransportException.of(
                    HttpApiV2TransportViolation.CONTENT_LENGTH_MISMATCH, "Content-Length");
        }
        if (declared != actual) {
            throw HttpApiV2TransportException.of(
                    HttpApiV2TransportViolation.CONTENT_LENGTH_MISMATCH, "Content-Length");
        }
    }
}
