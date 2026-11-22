package com.acquiring.simulator.channel;

import static com.acquiring.simulator.channel.C4EvidenceFixtures.ISSUER;
import static com.acquiring.simulator.channel.C4EvidenceFixtures.ISSUER_KEY;
import static com.acquiring.simulator.channel.C4EvidenceFixtures.body;
import static com.acquiring.simulator.channel.C4EvidenceFixtures.bodySha256;
import static com.acquiring.simulator.channel.C4EvidenceFixtures.context;
import static com.acquiring.simulator.channel.C4EvidenceFixtures.direct;
import static com.acquiring.simulator.channel.C4EvidenceFixtures.keyPair;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * P2b8 bounded internal HTTP transport slice (channel contract C2/C4, payload v2/octet
 * stream).
 *
 * <p>These tests exercise only the pure payload v2 decoder/encoder and the exact C4
 * carriage bridge. They do not authenticate a sender, construct a production trusted
 * context, call ISO8583, storage or a business service, allocate identifiers, send, create
 * receipts or expose a public late-delivery endpoint. Ephemeral Ed25519 material is used
 * only to shape an evidence envelope; no private key is written or logged.</p>
 */
class HttpApiV2InternalRouteTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void retainsExactOctetStreamBytesAcrossPayloadV2RequestDecoding() {
        byte[] exact = new byte[HttpApiV2RequestDecoder.MAX_BODY_BYTES];
        for (int i = 0; i < exact.length; i++) {
            exact[i] = (byte) (i % 256);
        }

        HttpApiV2Request request = HttpApiV2RequestDecoder.decode(
                bytes(requestEvent(exact)));

        assertThat(request.body()).containsExactly(exact);
        assertThat(request.mediaType()).isEqualTo(HttpApiV2RequestDecoder.MEDIA_TYPE);
        assertThat(request.profile()).isEqualTo(HttpApiV2RequestDecoder.PROFILE);
        assertThat(request.toString())
                .doesNotContain(Base64.getEncoder().encodeToString(exact));
    }

    @Test
    void refusesNonCanonicalBase64BoundsAndUnsupportedEncoding() {
        ObjectNode notBase64 = requestEvent(new byte[] {0x01});
        notBase64.put("isBase64Encoded", false);
        notBase64.put("body", "AQ==");
        assertRequestViolation(notBase64,
                HttpApiV2TransportViolation.UNSUPPORTED_ENCODING);

        ObjectNode nonCanonical = requestEvent(new byte[] {0x00});
        nonCanonical.put("body", "AB==");
        assertRequestViolation(nonCanonical,
                HttpApiV2TransportViolation.BASE64_NONCANONICAL);

        ObjectNode tooLarge = requestEvent(new byte[2049]);
        assertRequestViolation(tooLarge, HttpApiV2TransportViolation.BODY_SIZE);

        ObjectNode empty = requestEvent(new byte[0]);
        assertRequestViolation(empty, HttpApiV2TransportViolation.BODY_SIZE);
    }

    @Test
    void refusesWrongMediaProfileCompressionAndRouteBeforeBusinessAdmission() {
        ObjectNode wrongMedia = requestEvent(new byte[] {0x01});
        ((ObjectNode) wrongMedia.get("headers"))
                .put("Content-Type", "application/json");
        assertRequestViolation(wrongMedia,
                HttpApiV2TransportViolation.UNSUPPORTED_MEDIA);

        ObjectNode wrongProfile = requestEvent(new byte[] {0x01});
        ((ObjectNode) wrongProfile.get("headers"))
                .put("X-Simulator-Profile", "SIM-ISO87A-v2");
        assertRequestViolation(wrongProfile,
                HttpApiV2TransportViolation.UNSUPPORTED_PROFILE);

        ObjectNode compressed = requestEvent(new byte[] {0x01});
        ((ObjectNode) compressed.get("headers"))
                .put("Content-Encoding", "gzip");
        assertRequestViolation(compressed,
                HttpApiV2TransportViolation.COMPRESSION);

        ObjectNode compressedMagic = requestEvent(
                new byte[] {0x1f, (byte) 0x8b, 0x08, 0x00});
        assertRequestViolation(compressedMagic,
                HttpApiV2TransportViolation.COMPRESSION);

        ObjectNode wrongPath = requestEvent(new byte[] {0x01});
        wrongPath.put("rawPath", "/authorizations");
        assertRequestViolation(wrongPath, HttpApiV2TransportViolation.ROUTE);

        ObjectNode wrongMethod = requestEvent(new byte[] {0x01});
        ((ObjectNode) wrongMethod.at("/requestContext/http")).put("method", "GET");
        assertRequestViolation(wrongMethod, HttpApiV2TransportViolation.ROUTE);

        ObjectNode wrongLength = requestEvent(new byte[] {0x01, 0x02});
        ((ObjectNode) wrongLength.get("headers")).put("Content-Length", "1");
        assertRequestViolation(wrongLength,
                HttpApiV2TransportViolation.CONTENT_LENGTH_MISMATCH);
    }

    @Test
    void forgedIdentityHeadersCannotCreateTrustedSender() throws Exception {
        ObjectNode forgedRequest = requestEvent(new byte[] {0x01});
        ((ObjectNode) forgedRequest.get("headers"))
                .put("X-Authenticated-Sender", "ATTACKER");
        assertRequestViolation(forgedRequest,
                HttpApiV2TransportViolation.IDENTITY_ASSERTION);

        C4CarrierEnvelope evidence = evidence();
        ObjectNode forgedResponse = responseEvent(
                C4HttpCarriageBridge.normalResponse(evidence, body()));
        ((ObjectNode) forgedResponse.get("headers"))
                .put("X-Authenticated-Sender", "ATTACKER");

        HttpApiV2Response decoded = HttpApiV2ResponseDecoder.decode(bytes(forgedResponse));
        assertThat(decoded.toString()).doesNotContain("ATTACKER");

        C4TransportResponse trusted = C4HttpCarriageBridge.toTransport(
                decoded, context(ISSUER));
        assertThat(trusted.authenticatedSender()).isEqualTo(ISSUER);
        C4CarrierCandidate candidate = C4CarrierParser.parse(trusted);
        assertThat(candidate.senderBinding())
                .isEqualTo(C4CarrierCandidate.SenderBinding.DIRECT_SENDER_MATCHED);

        C4CarrierException refused = catchThrowableOfType(
                () -> C4CarrierParser.parse(C4HttpCarriageBridge.toTransport(
                        decoded, context("ATTACKER"))),
                C4CarrierException.class);
        assertThat(refused).isNotNull();
        assertThat(refused.violation())
                .isEqualTo(C4CarrierViolation.AUTHENTICATED_SENDER);
    }

    @Test
    void c4CarriageKeepsExactBodyAndExactlyOneCanonicalEvidenceHeader() throws Exception {
        C4CarrierEnvelope evidence = evidence();
        byte[] responseBytes = C4HttpCarriageBridge.normalResponse(evidence, body());

        HttpApiV2Response decoded = HttpApiV2ResponseDecoder.decode(responseBytes);
        assertThat(decoded.body()).containsExactly(body());
        List<String> header = HttpApiV2Wire.headerValues(
                decoded.headers(), C4CarrierParser.HEADER_NAME);
        assertThat(header).hasSize(1);
        assertThat(Base64.getDecoder().decode(header.get(0)))
                .containsExactly(evidence.toCanonicalBytes());

        C4CarrierCandidate candidate = C4CarrierParser.parse(
                C4HttpCarriageBridge.toTransport(decoded, context(ISSUER)));
        assertThat(candidate.responseBodySha256()).isEqualTo(bodySha256());
        assertThat(candidate.senderBinding())
                .isEqualTo(C4CarrierCandidate.SenderBinding.DIRECT_SENDER_MATCHED);

        ObjectNode duplicate = responseEvent(responseBytes);
        ((ObjectNode) duplicate.get("headers"))
                .put("x-simulator-evidence", header.get(0));
        HttpApiV2TransportException duplicateRefusal = catchThrowableOfType(
                () -> HttpApiV2ResponseDecoder.decode(bytes(duplicate)),
                HttpApiV2TransportException.class);
        assertThat(duplicateRefusal).isNotNull();
        assertThat(duplicateRefusal.violation())
                .isEqualTo(HttpApiV2TransportViolation.DUPLICATE_HEADER);

        Map<String, List<String>> duplicateHeaders = new LinkedHashMap<>();
        duplicateHeaders.put("Content-Type", List.of(C4HttpCarriageBridge.MEDIA_TYPE));
        duplicateHeaders.put("X-Simulator-Profile", List.of(C4HttpCarriageBridge.PROFILE));
        duplicateHeaders.put(C4CarrierParser.HEADER_NAME, List.of(header.get(0)));
        duplicateHeaders.put("x-simulator-evidence", List.of(header.get(0)));
        HttpApiV2TransportException encoderRefusal = catchThrowableOfType(
                () -> HttpApiV2ResponseEncoder.encode(200, duplicateHeaders, body()),
                HttpApiV2TransportException.class);
        assertThat(encoderRefusal).isNotNull();
        assertThat(encoderRefusal.violation())
                .isEqualTo(HttpApiV2TransportViolation.DUPLICATE_HEADER);
    }

    private static C4CarrierEnvelope evidence() throws GeneralSecurityException {
        KeyPair issuer = keyPair();
        return direct(C4EvidenceFixtures.attestation(
                ISSUER, ISSUER_KEY, "ISSUER", "ISSUER_REPLIED", "00",
                "APPROVED", "APPROVED", bodySha256()), issuer.getPrivate());
    }

    private static ObjectNode requestEvent(byte[] body) {
        ObjectNode event = baseRequestEvent();
        event.put("isBase64Encoded", true);
        event.put("body", Base64.getEncoder().encodeToString(body));
        ((ObjectNode) event.get("headers")).put("Content-Length",
                Integer.toString(body.length));
        return event;
    }

    private static ObjectNode baseRequestEvent() {
        ObjectNode event = JSON.createObjectNode();
        event.put("version", "2.0");
        event.put("rawPath", HttpApiV2RequestDecoder.INTERNAL_PATH);
        event.putObject("requestContext").putObject("http").put("method", "POST");
        ObjectNode headers = event.putObject("headers");
        headers.put("Content-Type", HttpApiV2RequestDecoder.MEDIA_TYPE);
        headers.put("X-Simulator-Profile", HttpApiV2RequestDecoder.PROFILE);
        return event;
    }

    private static ObjectNode responseEvent(byte[] raw) throws Exception {
        JsonNode node = JSON.readTree(raw);
        assertThat(node.isObject()).isTrue();
        return (ObjectNode) node;
    }

    private static byte[] bytes(ObjectNode event) {
        return event.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static void assertRequestViolation(
            ObjectNode event, HttpApiV2TransportViolation expected) {
        HttpApiV2TransportException error = catchThrowableOfType(
                () -> HttpApiV2RequestDecoder.decode(bytes(event)),
                HttpApiV2TransportException.class);
        assertThat(error).isNotNull();
        assertThat(error.violation()).isEqualTo(expected);
    }
}
