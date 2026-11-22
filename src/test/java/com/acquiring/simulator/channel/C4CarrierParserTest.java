package com.acquiring.simulator.channel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.acquiring.simulator.channel.C4CarrierCandidate.AttestationState;
import com.acquiring.simulator.channel.C4CarrierCandidate.KeyResolution;
import com.acquiring.simulator.channel.C4CarrierCandidate.SenderBinding;
import com.acquiring.simulator.channel.C4CarrierEnvelope.Mode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Oracles for the strict C4 carrier outer-envelope parser.
 *
 * <p>All twelve approved {@code carrier-vectors.json} fixtures are executed as shape
 * oracles. Acceptance here means only that the outer shape, canonical base64, closed key
 * sets, body hash and sender identity binding hold; the fixtures' signature bytes are
 * explicitly non-cryptographic, no signature is verified and no key is resolved. No HTTP
 * endpoint, ISO parser, storage, authentication or browser exposure is exercised.</p>
 */
class C4CarrierParserTest {

    private static final Path VECTORS = Path.of(
            "docs/ai-sdlc/work-items/WI-ACQ-OBS-001/vectors/carrier-vectors.json");

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String ALPHABET =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";

    @Test
    void matchesAllApprovedCarrierShapeFixtures() throws IOException {
        JsonNode cases = MAPPER.readTree(Files.readString(VECTORS)).get("cases");
        assertThat(cases).as("approved carrier cases").isNotNull();
        assertThat(cases.size()).as("approved case count").isEqualTo(12);

        Set<String> shapeAccepted = Set.of("K01-direct", "K02-relay", "K03-late",
                "K07-key-unavailable");
        Map<String, C4CarrierViolation> rejected = Map.ofEntries(
                Map.entry("K04-missing-sidecar", C4CarrierViolation.MISSING_PROOF),
                Map.entry("K05-response-hash-mismatch", C4CarrierViolation.HASH_BINDING),
                Map.entry("K06-signature-absent", C4CarrierViolation.CARRIER_SCHEMA),
                Map.entry("K08-direct-wrong-sender", C4CarrierViolation.AUTHENTICATED_SENDER),
                Map.entry("K09-relay-hash-mismatch", C4CarrierViolation.RELAY_BINDING),
                Map.entry("K10-oversized-sidecar", C4CarrierViolation.CARRIER_SIZE),
                Map.entry("K11-late-missing-proof", C4CarrierViolation.MISSING_PROOF),
                Map.entry("K12-proof-unavailable", C4CarrierViolation.TRANSPORT_FAILURE));

        int checked = 0;
        for (JsonNode testCase : cases) {
            String id = testCase.get("id").asText();
            C4TransportResponse transport = transportFor(testCase);

            if (reshapeAccepted(id, shapeAccepted)) {
                C4CarrierCandidate candidate = C4CarrierParser.parse(transport);
                assertThat(candidate.attestationState())
                        .as(id + " is never a trusted flag")
                        .isEqualTo(AttestationState.UNVERIFIED_CANDIDATE);
                assertThat(candidate.keyResolution())
                        .as(id + " key resolution deferred")
                        .isEqualTo(KeyResolution.UNRESOLVED);
                assertThat(candidate.responseBodySha256())
                        .as(id + " exact body hash")
                        .isEqualTo(testCase.get("responseBodySha256").asText());
                assertThat(candidate.canonicalAttestationSha256())
                        .as(id + " canonical attestation hash")
                        .isEqualTo(candidate.envelope().attestation().canonicalSha256());
                if ("K02-relay".equals(id)) {
                    assertThat(candidate.senderBinding()).isEqualTo(SenderBinding.RELAY_SENDER_MATCHED);
                    assertThat(candidate.envelope().mode()).isEqualTo(Mode.RELAY);
                    C4RelayAttestation relay = candidate.envelope().relay();
                    assertThat(relay).as(id + " relay object").isNotNull();
                    assertThat(relay.attestationSha256())
                            .isEqualTo(candidate.canonicalAttestationSha256());
                    assertThat(relay.responseSha256())
                            .isEqualTo(candidate.responseBodySha256());
                } else {
                    assertThat(candidate.senderBinding())
                            .isEqualTo(SenderBinding.DIRECT_SENDER_MATCHED);
                    assertThat(candidate.envelope().mode()).isEqualTo(Mode.DIRECT);
                }
                if ("K07-key-unavailable".equals(id)) {
                    assertThat(candidate.envelope().keyId())
                            .as(id + " unresolved key is preserved, not resolved")
                            .isEqualTo("UNKNOWN");
                }
            } else {
                C4CarrierException error = catchThrowableOfType(
                        () -> C4CarrierParser.parse(transport), C4CarrierException.class);
                assertThat(error).as(id + " sanitized rejection").isNotNull();
                assertThat(error.violation()).as(id + " violation").isEqualTo(rejected.get(id));
            }
            checked++;
        }
        assertThat(checked).as("every approved case executed").isEqualTo(12);
    }

    @Test
    void duplicateEvidenceHeaderCannotCollapse() throws IOException {
        JsonNode testCase = caseById("K01-direct");
        String value = evidenceHeader(testCase);
        Map<String, List<String>> headers = new LinkedHashMap<>();
        headers.put("Content-Type", List.of("application/octet-stream"));
        headers.put(C4CarrierParser.HEADER_NAME, List.of(value, value));

        C4CarrierException error = catchThrowableOfType(
                () -> C4CarrierParser.parse(transport(testCase, headers, 200)),
                C4CarrierException.class);

        assertThat(error.violation()).isEqualTo(C4CarrierViolation.DUPLICATE_HEADER);
    }

    @Test
    void caseVariantDuplicateHeaderCannotCollapse() throws IOException {
        JsonNode testCase = caseById("K01-direct");
        String value = evidenceHeader(testCase);
        Map<String, List<String>> headers = new LinkedHashMap<>();
        headers.put("content-type", List.of("application/octet-stream"));
        headers.put("x-simulator-evidence", List.of(value));
        headers.put("X-Simulator-Evidence", List.of(value));

        C4CarrierException error = catchThrowableOfType(
                () -> C4CarrierParser.parse(transport(testCase, headers, 200)),
                C4CarrierException.class);

        assertThat(error.violation()).isEqualTo(C4CarrierViolation.DUPLICATE_HEADER);
    }

    @Test
    void commaCoalescedHeaderIsRejectedAsDuplicate() throws IOException {
        JsonNode testCase = caseById("K01-direct");
        String value = evidenceHeader(testCase);
        Map<String, List<String>> headers = new LinkedHashMap<>();
        headers.put(C4CarrierParser.HEADER_NAME, List.of(value + "," + value));

        C4CarrierException error = catchThrowableOfType(
                () -> C4CarrierParser.parse(transport(testCase, headers, 200)),
                C4CarrierException.class);

        assertThat(error.violation()).isEqualTo(C4CarrierViolation.DUPLICATE_HEADER);
    }

    @Test
    void foldedHeaderIsRejected() throws IOException {
        JsonNode testCase = caseById("K01-direct");
        String value = evidenceHeader(testCase);
        Map<String, List<String>> headers = new LinkedHashMap<>();
        headers.put(C4CarrierParser.HEADER_NAME, List.of(value.substring(0, 8)
                + "\r\n " + value.substring(8)));

        C4CarrierException error = catchThrowableOfType(
                () -> C4CarrierParser.parse(transport(testCase, headers, 200)),
                C4CarrierException.class);

        assertThat(error.violation()).isEqualTo(C4CarrierViolation.FOLDED_HEADER);
    }

    @Test
    void base64WhitespaceAndPaddingAreNonCanonical() throws IOException {
        JsonNode testCase = caseById("K01-direct");
        String value = evidenceHeader(testCase);

        Map<String, List<String>> leading = new LinkedHashMap<>();
        leading.put(C4CarrierParser.HEADER_NAME, List.of(" " + value));
        assertThat(catchThrowableOfType(
                () -> C4CarrierParser.parse(transport(testCase, leading, 200)),
                C4CarrierException.class).violation())
                .isEqualTo(C4CarrierViolation.BASE64_NONCANONICAL);

        Map<String, List<String>> unpadded = new LinkedHashMap<>();
        unpadded.put(C4CarrierParser.HEADER_NAME, List.of(value.substring(0, value.length() - 2)));
        C4CarrierException error = catchThrowableOfType(
                () -> C4CarrierParser.parse(transport(testCase, unpadded, 200)),
                C4CarrierException.class);
        assertThat(error.violation()).isEqualTo(C4CarrierViolation.BASE64_NONCANONICAL);
    }

    @Test
    void nonZeroPadBitsAreNonCanonical() throws IOException {
        JsonNode testCase = caseById("K01-direct");
        String value = evidenceHeader(testCase);
        assertThat(value).endsWith("==");
        int index = value.length() - 3;
        int original = ALPHABET.indexOf(value.charAt(index));
        int flipped = (original & 0xF0) | ((original & 0x0F) == 0 ? 1 : 0);
        String mutated = value.substring(0, index) + ALPHABET.charAt(flipped)
                + value.substring(index + 1);

        Map<String, List<String>> headers = new LinkedHashMap<>();
        headers.put(C4CarrierParser.HEADER_NAME, List.of(mutated));
        C4CarrierException error = catchThrowableOfType(
                () -> C4CarrierParser.parse(transport(testCase, headers, 200)),
                C4CarrierException.class);

        assertThat(error.violation()).isEqualTo(C4CarrierViolation.BASE64_NONCANONICAL);
    }

    @Test
    void boundsAreEnforcedBeforeDecode() throws IOException {
        JsonNode testCase = caseById("K01-direct");

        Map<String, List<String>> oversizedEncoded = new LinkedHashMap<>();
        oversizedEncoded.put(C4CarrierParser.HEADER_NAME, List.of("A".repeat(5468)));
        assertThat(catchThrowableOfType(
                () -> C4CarrierParser.parse(transport(testCase, oversizedEncoded, 200)),
                C4CarrierException.class).violation())
                .isEqualTo(C4CarrierViolation.CARRIER_SIZE);

        Map<String, List<String>> oversizedDecoded = new LinkedHashMap<>();
        oversizedDecoded.put(C4CarrierParser.HEADER_NAME, List.of("A".repeat(5464)));
        assertThat(catchThrowableOfType(
                () -> C4CarrierParser.parse(transport(testCase, oversizedDecoded, 200)),
                C4CarrierException.class).violation())
                .isEqualTo(C4CarrierViolation.CARRIER_SIZE);
    }

    @Test
    void totalHeaderBudgetAndBodyBudgetAreEnforced() throws IOException {
        JsonNode testCase = caseById("K01-direct");
        C4TransportResponse normal = transportFor(testCase);

        C4TransportResponse overHeaders = new C4TransportResponse(
                normal.statusCode(), normal.headers(), normal.body(),
                normal.authenticatedSender(), 8193);
        assertThat(catchThrowableOfType(
                () -> C4CarrierParser.parse(overHeaders), C4CarrierException.class).violation())
                .isEqualTo(C4CarrierViolation.HEADERS_TOO_LARGE);

        C4TransportResponse overBody = new C4TransportResponse(
                normal.statusCode(), normal.headers(), new byte[2049],
                normal.authenticatedSender(), normal.totalHeaderBytes());
        assertThat(catchThrowableOfType(
                () -> C4CarrierParser.parse(overBody), C4CarrierException.class).violation())
                .isEqualTo(C4CarrierViolation.BODY_SIZE);
    }

    @Test
    void unknownEnvelopeKeyIsRejected() throws IOException {
        JsonNode testCase = caseById("K01-direct");
        ObjectNode envelope = envelopeNode(testCase);
        envelope.put("VALID", true);

        C4CarrierException error = catchThrowableOfType(
                () -> C4CarrierParser.parse(withEnvelope(testCase, envelope)),
                C4CarrierException.class);

        assertThat(error.violation()).isEqualTo(C4CarrierViolation.CARRIER_SCHEMA);
        assertThat(error.field()).isEqualTo("VALID");
    }

    @Test
    void unknownAttestationKeyIsRejected() throws IOException {
        JsonNode testCase = caseById("K01-direct");
        ObjectNode envelope = envelopeNode(testCase);
        ((ObjectNode) envelope.get("attestation")).put("P5_ATTESTATION_VALID", true);

        C4CarrierException error = catchThrowableOfType(
                () -> C4CarrierParser.parse(withEnvelope(testCase, envelope)),
                C4CarrierException.class);

        assertThat(error.violation()).isEqualTo(C4CarrierViolation.CARRIER_SCHEMA);
        assertThat(error.field()).isEqualTo("P5_ATTESTATION_VALID");
    }

    @Test
    void duplicateJsonKeyIsRejected() throws IOException {
        JsonNode testCase = caseById("K01-direct");
        String canonical = new String(
                Base64.getDecoder().decode(evidenceHeader(testCase)), StandardCharsets.US_ASCII);
        String duplicated = "{\"version\":1," + canonical.substring(1);

        C4CarrierException error = catchThrowableOfType(
                () -> C4CarrierParser.parse(withRawEnvelope(testCase, duplicated)),
                C4CarrierException.class);

        assertThat(error.violation()).isEqualTo(C4CarrierViolation.CARRIER_SCHEMA);
    }

    @Test
    void nonCanonicalEnvelopeBytesAreRejected() throws IOException {
        JsonNode testCase = caseById("K01-direct");
        String pretty = MAPPER.writerWithDefaultPrettyPrinter()
                .writeValueAsString(envelopeNode(testCase));

        C4CarrierException error = catchThrowableOfType(
                () -> C4CarrierParser.parse(withRawEnvelope(testCase, pretty)),
                C4CarrierException.class);

        assertThat(error.violation()).isEqualTo(C4CarrierViolation.CARRIER_SCHEMA);
        assertThat(error.field()).isEqualTo("canonical");
    }

    @Test
    void outerKeyMustMatchSignedAttestationKey() throws IOException {
        JsonNode testCase = caseById("K01-direct");
        ObjectNode envelope = envelopeNode(testCase);
        envelope.put("keyId", "SPEC-KEY-OTHER");

        C4CarrierException error = catchThrowableOfType(
                () -> C4CarrierParser.parse(withEnvelope(testCase, envelope)),
                C4CarrierException.class);

        assertThat(error.violation()).isEqualTo(C4CarrierViolation.KEY_BINDING);
    }

    @Test
    void directModeWithRelayObjectIsRejected() throws IOException {
        JsonNode testCase = caseById("K02-relay");
        ObjectNode envelope = envelopeNode(testCase);
        envelope.put("mode", "DIRECT");

        C4CarrierException error = catchThrowableOfType(
                () -> C4CarrierParser.parse(withEnvelope(testCase, envelope)),
                C4CarrierException.class);

        assertThat(error.violation()).isEqualTo(C4CarrierViolation.CARRIER_SCHEMA);
        assertThat(error.field()).isEqualTo("relay");
    }

    @Test
    void signatureMustBeExactly64Bytes() throws IOException {
        JsonNode testCase = caseById("K01-direct");
        ObjectNode envelope = envelopeNode(testCase);
        envelope.put("signature", Base64.getEncoder().encodeToString(new byte[63]));

        C4CarrierException error = catchThrowableOfType(
                () -> C4CarrierParser.parse(withEnvelope(testCase, envelope)),
                C4CarrierException.class);

        assertThat(error.violation()).isEqualTo(C4CarrierViolation.CARRIER_SCHEMA);
        assertThat(error.field()).isEqualTo("signature");
    }

    @Test
    void candidateHasNoTrustFlagAndDoesNotLeakMaterial() {
        assertThat(C4CarrierCandidate.class.getDeclaredMethods())
                .extracting(java.lang.reflect.Method::getName)
                .doesNotContain("isValid", "isVerified", "isTrusted", "valid", "verified",
                        "trusted");
    }

    @Test
    void acceptedCandidateRenderingOmitsCarrierMaterial() throws IOException {
        JsonNode testCase = caseById("K01-direct");
        C4CarrierCandidate candidate = C4CarrierParser.parse(transportFor(testCase));
        String rendered = candidate.toString();

        assertThat(rendered)
                .doesNotContain(evidenceHeader(testCase))
                .doesNotContain(testCase.get("responseBodyHex").asText())
                .doesNotContain(candidate.envelope().attestation().instructionSha256());
        assertThat(rendered).contains("UNVERIFIED_CANDIDATE");
    }

    private static boolean reshapeAccepted(String id, Set<String> shapeAccepted) {
        return shapeAccepted.contains(id);
    }

    private static JsonNode caseById(String id) throws IOException {
        for (JsonNode testCase : MAPPER.readTree(Files.readString(VECTORS)).get("cases")) {
            if (id.equals(testCase.get("id").asText())) {
                return testCase;
            }
        }
        throw new IllegalArgumentException("unknown carrier case " + id);
    }

    private static String evidenceHeader(JsonNode testCase) {
        return testCase.get("headers").get(C4CarrierParser.HEADER_NAME).asText();
    }

    private static C4TransportResponse transportFor(JsonNode testCase) {
        Map<String, List<String>> headers = new LinkedHashMap<>();
        testCase.get("headers").fields().forEachRemaining(field ->
                headers.put(field.getKey(), List.of(field.getValue().asText())));
        return transport(testCase, headers, testCase.get("responseStatus").asInt());
    }

    private static C4TransportResponse transport(
            JsonNode testCase, Map<String, List<String>> headers, int status) {
        byte[] body = testCase.get("responseBodyHex").asText().isEmpty()
                ? new byte[0]
                : HexFormat.of().parseHex(testCase.get("responseBodyHex").asText());
        int total = headers.entrySet().stream()
                .mapToInt(entry -> entry.getKey().length() + 2
                        + entry.getValue().get(0).length() + 2)
                .sum();
        String sender = testCase.has("authenticatedSender")
                ? testCase.get("authenticatedSender").asText()
                : null;
        return new C4TransportResponse(status, headers, body, sender, total);
    }

    private static ObjectNode envelopeNode(JsonNode testCase) throws IOException {
        byte[] decoded = Base64.getDecoder().decode(evidenceHeader(testCase));
        return (ObjectNode) MAPPER.readTree(decoded);
    }

    private static C4TransportResponse withEnvelope(JsonNode testCase, JsonNode envelope)
            throws IOException {
        return withRawEnvelope(testCase, MAPPER.writeValueAsString(envelope));
    }

    private static C4TransportResponse withRawEnvelope(JsonNode testCase, String envelope) {
        Map<String, List<String>> headers = new LinkedHashMap<>();
        headers.put("Content-Type", List.of("application/octet-stream"));
        headers.put(C4CarrierParser.HEADER_NAME, List.of(
                Base64.getEncoder().encodeToString(
                        envelope.getBytes(StandardCharsets.US_ASCII))));
        return transport(testCase, headers, 200);
    }
}
