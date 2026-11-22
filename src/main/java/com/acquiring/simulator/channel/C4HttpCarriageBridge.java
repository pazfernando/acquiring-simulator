package com.acquiring.simulator.channel;

import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Exact bridge between the internal C2 payload v2 octet-stream carriage and the existing
 * C4 response parser seam.
 *
 * <p>Two directions are implemented:</p>
 * <ul>
 *   <li>{@link #normalResponse} builds the approved normal 200 response: octet-stream body,
 *       the fixed profile and exactly one padded base64 {@code X-Simulator-Evidence} header
 *       carrying the canonical C4 envelope. It does not sign, verify or invent evidence.</li>
 *   <li>{@link #toTransport} maps a decoded payload v2 response to
 *       {@link C4TransportResponse}, retaining the exact body bytes, multi-value headers,
 *       measured total header bytes and the separately supplied authenticated sender.</li>
 * </ul>
 *
 * <p><strong>Trust boundary.</strong> The {@link C4AuthenticatedTransportContext} argument
 * must be produced by the verified platform authentication mapping at the service boundary
 * (for example an IAM/SigV4 or mutual-TLS principal once that realization is approved). This
 * class never reads a sender from an headers map, a response body, a JWT claim or a query
 * parameter. The repository does not yet contain that production C2 principal mapping; only
 * the separate console JWT authorizer exists. Until it is supplied and approved, this class
 * remains a pure transport primitive and must not be used as evidence of sender
 * authentication.</p>
 *
 * <p>No public late-delivery endpoint, reimbursement/reconciliation route, key custody,
 * signature verification, business admission, storage write, send, identifier allocation or
 * receipt is implemented here.</p>
 */
public final class C4HttpCarriageBridge {

    /** Approved C2 media type. */
    public static final String MEDIA_TYPE = HttpApiV2Wire.MEDIA_TYPE;

    /** Approved C2 profile header value. */
    public static final String PROFILE = HttpApiV2Wire.PROFILE;

    private C4HttpCarriageBridge() {
    }

    /**
     * Build one exact normal 200 payload v2 response carrying the presented C4 envelope.
     *
     * @throws HttpApiV2TransportException when the body or evidence header exceeds its
     *     approved carriage bound
     */
    public static byte[] normalResponse(C4CarrierEnvelope evidence, byte[] body) {
        Objects.requireNonNull(evidence, "evidence");
        Objects.requireNonNull(body, "body");
        HttpApiV2Wire.requireBodySize(body.length);

        byte[] canonical = evidence.toCanonicalBytes();
        if (canonical.length > C4CarrierParser.MAX_DECODED_BYTES) {
            throw HttpApiV2TransportException.of(
                    HttpApiV2TransportViolation.CARRIER_SIZE, "evidence");
        }
        String encodedEvidence = Base64.getEncoder().encodeToString(canonical);
        if (encodedEvidence.length() > C4CarrierParser.MAX_ENCODED_CHARS) {
            throw HttpApiV2TransportException.of(
                    HttpApiV2TransportViolation.CARRIER_SIZE, "evidence");
        }

        Map<String, List<String>> headers = new LinkedHashMap<>();
        headers.put("Content-Type", List.of(MEDIA_TYPE));
        headers.put("X-Simulator-Profile", List.of(PROFILE));
        headers.put(C4CarrierParser.HEADER_NAME, List.of(encodedEvidence));
        if (HttpApiV2Wire.totalHeaderBytes(headers)
                > C4CarrierParser.MAX_TOTAL_HEADER_BYTES) {
            throw HttpApiV2TransportException.of(
                    HttpApiV2TransportViolation.HEADERS_TOO_LARGE, "headers");
        }
        return HttpApiV2ResponseEncoder.encode(200, headers, body);
    }

    /**
     * Map a decoded payload v2 response to the C4 parser's transport seam.
     *
     * @param response decoded response with exact body bytes and multi-value headers
     * @param context  authenticated transport context supplied by the platform boundary,
     *                 never constructed from the response or a caller-asserted header
     */
    public static C4TransportResponse toTransport(
            HttpApiV2Response response, C4AuthenticatedTransportContext context) {
        Objects.requireNonNull(response, "response");
        Objects.requireNonNull(context, "context");
        return new C4TransportResponse(
                response.statusCode(),
                response.headers(),
                response.body(),
                context.authenticatedSender(),
                HttpApiV2Wire.totalHeaderBytes(response.headers()));
    }
}
