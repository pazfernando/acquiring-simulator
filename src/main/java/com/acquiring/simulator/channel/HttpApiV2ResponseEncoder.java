package com.acquiring.simulator.channel;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Narrow API Gateway HTTP API payload v2 encoder for one internal octet-stream response.
 *
 * <p>The encoder always marks the body base64 and keeps the exact bytes unchanged. It
 * refuses duplicate case-variant header names, multi-valued headers that payload v2 cannot
 * represent, header injection, over-bound total headers and bodies outside 0–2048 bytes.
 * It does not decide status, evidence, payment outcome or identity; those are supplied by
 * the caller.</p>
 *
 * <p>The C2 normal response is produced by {@link C4HttpCarriageBridge#normalResponse},
 * which fixes the media type, profile and exactly one evidence header.</p>
 */
public final class HttpApiV2ResponseEncoder {

    private HttpApiV2ResponseEncoder() {
    }

    /**
     * Encode one payload v2 response event.
     *
     * @throws HttpApiV2TransportException when the supplied transport facts are not
     *     representable within the approved bounds
     */
    public static byte[] encode(int statusCode, Map<String, List<String>> headers, byte[] body) {
        Objects.requireNonNull(headers, "headers");
        Objects.requireNonNull(body, "body");

        if (statusCode < 100 || statusCode > 599) {
            throw HttpApiV2TransportException.of(
                    HttpApiV2TransportViolation.EVENT_SCHEMA, "statusCode");
        }
        if (body.length > HttpApiV2Wire.MAX_BODY_BYTES) {
            throw HttpApiV2TransportException.of(
                    HttpApiV2TransportViolation.BODY_SIZE, "body");
        }
        if (HttpApiV2Wire.totalHeaderBytes(headers) > C4CarrierParser.MAX_TOTAL_HEADER_BYTES) {
            throw HttpApiV2TransportException.of(
                    HttpApiV2TransportViolation.HEADERS_TOO_LARGE, "headers");
        }

        ObjectNode event = HttpApiV2Wire.objectNode();
        event.put("statusCode", statusCode);
        ObjectNode encodedHeaders = event.putObject("headers");
        Set<String> seen = new HashSet<>();
        for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
            String name = entry.getKey();
            List<String> values = entry.getValue();
            if (name == null || name.isEmpty() || values == null || values.size() != 1) {
                throw HttpApiV2TransportException.of(
                        HttpApiV2TransportViolation.DUPLICATE_HEADER, name);
            }
            if (!seen.add(name.toLowerCase(Locale.ROOT))) {
                throw HttpApiV2TransportException.of(
                        HttpApiV2TransportViolation.DUPLICATE_HEADER, name);
            }
            String value = values.get(0);
            if (value == null || value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0) {
                throw HttpApiV2TransportException.of(
                        HttpApiV2TransportViolation.EVENT_SCHEMA, name);
            }
            encodedHeaders.put(name, value);
        }
        event.put("body", Base64.getEncoder().encodeToString(body));
        event.put("isBase64Encoded", true);
        return HttpApiV2Wire.writeJson(event);
    }
}
