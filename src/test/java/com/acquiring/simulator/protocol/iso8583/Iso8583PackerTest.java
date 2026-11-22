package com.acquiring.simulator.protocol.iso8583;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.acquiring.simulator.protocol.iso8583.Iso8583PackException.Rule;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Byte-exact oracle tests for {@link Iso8583Packer}.
 *
 * <p>Every approved positive fixture is scanned from its authoritative
 * {@code hex} octets, packed from the decoded structural map and compared byte
 * for byte with the original oracle octets. The external oracle is the approved
 * {@code packed-vectors.json}; a self-generated round trip is never treated as
 * evidence.</p>
 *
 * <p>The five canonical P4 response shapes are also packed byte-exactly. Their
 * expected verdict is {@code EVIDENCE_MATRIX}, so packing them proves structural
 * representability only; it does not reclassify those business-invalid packets
 * as financially accepted. The packer deliberately does not run the P4 matrix,
 * DE48 schema, domain, binding, freshness or 826 admission layers.</p>
 */
class Iso8583PackerTest {

    private static final Path PACKED =
            Path.of("docs/ai-sdlc/work-items/WI-ACQ-OBS-001/vectors/packed-vectors.json");
    private static final ObjectMapper JSON = new ObjectMapper();

    private static final int APPROVED_POSITIVE_FIXTURES = 31;

    /**
     * The five canonical P4 response shapes. They are structurally valid 0110
     * bodies whose business verdict belongs to the deferred evidence matrix.
     */
    private static final List<String> CANONICAL_RESPONSE_VECTORS = List.of(
            "N-00-network",
            "N-05-processor",
            "N-30-issuer",
            "N-91-issuer",
            "N-unknown-code");

    // --- Approved byte oracle ---------------------------------------------------

    static Stream<Arguments> approvedPositiveFixtures() throws IOException {
        return vectors("ACCEPT")
                .map(v -> Arguments.of(v.get("id").asText(), bodyOf(v)));
    }

    @ParameterizedTest(name = "{0} packs back to its approved octets")
    @MethodSource("approvedPositiveFixtures")
    void repacksEveryApprovedPositiveFixtureByteExact(String id, String approvedBody) {
        Iso8583Message scanned = Iso8583Scanner.scan(approvedBody);

        String packed = Iso8583Packer.pack(scanned.mti(), scanned.fields());

        assertThat(packed).as("%s body", id).isEqualTo(approvedBody);
        assertThat(packed.getBytes(StandardCharsets.ISO_8859_1))
                .as("%s octets", id)
                .containsExactly(approvedBody.getBytes(StandardCharsets.ISO_8859_1));
        assertThat(packed.chars().allMatch(c -> c >= 0x20 && c <= 0x7E))
                .as("%s is printable ASCII", id)
                .isTrue();
        assertThat(Iso8583Scanner.scan(packed).fields())
                .as("%s re-scans to the same fields", id)
                .isEqualTo(scanned.fields());
    }

    static Stream<Arguments> fiveCanonicalResponseVectors() throws IOException {
        List<Arguments> arguments = new java.util.ArrayList<>();
        for (String id : CANONICAL_RESPONSE_VECTORS) {
            arguments.add(Arguments.of(id, bodyOf(require(id))));
        }
        return arguments.stream();
    }

    @ParameterizedTest(name = "{0} packs structurally without a financial verdict")
    @MethodSource("fiveCanonicalResponseVectors")
    void repacksFiveCanonicalResponseVectorsStructurallyOnly(
            String id, String approvedBody) throws IOException {
        JsonNode vector = require(id);
        assertThat(vector.get("expected").asText()).as("%s oracle label", id)
                .isEqualTo("EVIDENCE_MATRIX");
        assertThat(vector.get("expectedFirstFailure").asText()).as("%s failure owner", id)
                .isEqualTo("EVIDENCE_MATRIX");

        Iso8583Message scanned = Iso8583Scanner.scan(approvedBody);
        assertThat(scanned.mti()).isEqualTo("0110");

        assertThat(Iso8583Packer.pack(scanned.mti(), scanned.fields()))
                .as("%s structural octets", id)
                .isEqualTo(approvedBody);
    }

    @Test
    void oracleInventoryIsTheApprovedThirtyOnePlusFiveResponses() throws IOException {
        assertThat(count("ACCEPT")).isEqualTo(APPROVED_POSITIVE_FIXTURES);
        assertThat(CANONICAL_RESPONSE_VECTORS).hasSize(5);
        for (String id : CANONICAL_RESPONSE_VECTORS) {
            assertThat(require(id).get("expected").asText()).isEqualTo("EVIDENCE_MATRIX");
        }
    }

    // --- Bitmap, ordering and framing ------------------------------------------

    @Test
    void emitsTheUppercasePrimaryBitmapAndAscendingFields() throws IOException {
        String approved = bodyOf(require("V01-approved-request"));

        String packed = Iso8583Packer.pack(
                "0100", Iso8583Scanner.scan(approved).fields());

        assertThat(packed.substring(0, 4)).isEqualTo("0100");
        assertThat(packed.substring(4, 20)).isEqualTo("3220048108C18000");
        assertThat(packed).isEqualTo(approved);
    }

    @Test
    void mapIterationOrderDoesNotAffectTheBytes() throws IOException {
        String approved = bodyOf(require("V01-approved-request"));
        Map<Integer, String> decoded =
                Iso8583Scanner.scan(approved).fields();

        Map<Integer, String> descending = new LinkedHashMap<>();
        new TreeMap<>(decoded).descendingMap().forEach(descending::put);

        assertThat(Iso8583Packer.pack("0100", descending)).isEqualTo(approved);
    }

    @Test
    void lengthPrefixesAreZeroPaddedAndExcludeThePrefix() throws IOException {
        Iso8583Message scanned =
                Iso8583Scanner.scan(bodyOf(require("V01-approved-request")));
        String packed = Iso8583Packer.pack(scanned.mti(), scanned.fields());

        String de32 = scanned.field(32);
        String de48 = scanned.field(48);
        assertThat(de32).isEqualTo("1");
        assertThat(de48).hasSize(188);

        assertThat(packed).contains("01" + de32 + scanned.field(37));
        assertThat(packed).contains("188" + de48);
    }

    @Test
    void packsStructuralDe48At999WithoutImposingAdmission() throws IOException {
        assertThat(Iso8583Packer.STRUCTURAL_DE48_MAX_BYTES).isEqualTo(999);

        JsonNode capacityRequest = require("CAP-request-826");
        String requestBody = bodyOf(capacityRequest);
        Iso8583Message request = Iso8583Scanner.scan(requestBody);
        assertThat(request.field(48)).hasSize(826);
        assertThat(Iso8583Packer.pack(request.mti(), request.fields())).isEqualTo(requestBody);

        JsonNode capacityResponse = require("CAP-response-3");
        String responseBody = bodyOf(capacityResponse);
        Iso8583Message response = Iso8583Scanner.scan(responseBody);
        assertThat(response.field(48)).hasSize(999);
        assertThat(Iso8583Packer.pack(response.mti(), response.fields())).isEqualTo(responseBody);
    }

    // --- Presence ---------------------------------------------------------------

    @Test
    void rejectsMissingRequiredFieldsInAscendingOrder() throws IOException {
        Map<Integer, String> missingThreeAndFour = requestFields();
        missingThreeAndFour.remove(3);
        missingThreeAndFour.remove(4);
        assertPackFailure("0100", missingThreeAndFour, Rule.MISSING_REQUIRED_FIELD, 3);

        Map<Integer, String> missingFour = requestFields();
        missingFour.remove(4);
        assertPackFailure("0100", missingFour, Rule.MISSING_REQUIRED_FIELD, 4);

        Map<Integer, String> missingDe39 = responseFields();
        missingDe39.remove(39);
        assertPackFailure("0110", missingDe39, Rule.MISSING_REQUIRED_FIELD, 39);
    }

    @Test
    void rejectsFieldsForbiddenOnARequest() throws IOException {
        Map<Integer, String> withDe38 = requestFields();
        withDe38.put(38, "AB1234");
        assertPackFailure("0100", withDe38, Rule.FORBIDDEN_FIELD_PRESENT, 38);

        Map<Integer, String> withDe39 = requestFields();
        withDe39.put(39, "00");
        assertPackFailure("0100", withDe39, Rule.FORBIDDEN_FIELD_PRESENT, 39);
    }

    @Test
    void encodesConditionalDe38OnlyWhenSuppliedAndNeverMintsOne() throws IOException {
        String withoutReference = bodyOf(require("V02-approved-response"));
        String withReference = bodyOf(require("V10-actual-DE38"));

        Map<Integer, String> fields = responseFields();
        String packedWithout = Iso8583Packer.pack("0110", fields);
        assertThat(packedWithout).isEqualTo(withoutReference);
        assertThat(Iso8583Scanner.scan(packedWithout).field(38)).isNull();

        fields.put(38, "AB1234");
        String packedWith = Iso8583Packer.pack("0110", fields);
        assertThat(packedWith).isEqualTo(withReference);
        assertThat(Iso8583Scanner.scan(packedWith).field(38)).isEqualTo("AB1234");
    }

    // --- Enabled-field membership ----------------------------------------------

    @Test
    void rejectsUnknownDisabledAndSecondaryBitmapFields() throws IOException {
        for (int de : List.of(1, 2, 35, 36, 45, 52, 55, 64)) {
            Map<Integer, String> fields = requestFields();
            fields.put(de, "X");
            assertPackFailure("0100", fields, Rule.UNKNOWN_FIELD, de);
        }
    }

    @Test
    void rejectsANullDataElementKey() {
        Map<Integer, String> fields = new LinkedHashMap<>();
        fields.put(null, "X");
        assertPackFailure("0100", fields, Rule.UNKNOWN_FIELD, -1);
    }

    @Test
    void rejectsANullFieldMap() {
        assertThatThrownBy(() -> Iso8583Packer.pack("0100", null))
                .isInstanceOfSatisfying(Iso8583PackException.class, e -> {
                    assertThat(e.rule()).isEqualTo(Rule.MISSING_VALUE);
                    assertThat(e.dataElement()).isEqualTo(-1);
                });
    }

    // --- Width, alphabet and length --------------------------------------------

    @Test
    void rejectsIncorrectFixedWidths() throws IOException {
        assertFieldFailure(3, "00000", Rule.INVALID_WIDTH);
        assertFieldFailure(3, "0000000", Rule.INVALID_WIDTH);
        assertFieldFailure(4, "00000000100", Rule.INVALID_WIDTH);
        assertFieldFailure(7, "111812000", Rule.INVALID_WIDTH);
        assertFieldFailure(11, "0000001", Rule.INVALID_WIDTH);
        assertFieldFailure(22, "05", Rule.INVALID_WIDTH);
        assertFieldFailure(25, "0", Rule.INVALID_WIDTH);
        assertFieldFailure(37, "00000000000", Rule.INVALID_WIDTH);
        assertFieldFailure(41, "TERM001", Rule.INVALID_WIDTH);
        assertFieldFailure(42, "MERCH", Rule.INVALID_WIDTH);
        assertFieldFailure(49, "84", Rule.INVALID_WIDTH);
    }

    @Test
    void rejectsWrongAlphabetsWithoutNormalizing() throws IOException {
        assertFieldFailure(3, "00A000", Rule.INVALID_NUMERIC);
        assertFieldFailure(4, "00000000100A", Rule.INVALID_NUMERIC);
        assertFieldFailure(32, "12A", Rule.INVALID_NUMERIC);
        assertFieldFailure(37, "abc000000001", Rule.INVALID_UPPER_ALNUM);
        assertFieldFailure(41, "TERM 001", Rule.INVALID_UPPER_ALNUM);
        assertFieldFailure(42, "MERCH-000000001", Rule.INVALID_UPPER_ALNUM);
        assertFieldFailure(48, "v=1;\u0001", Rule.NON_PRINTABLE_ASCII);
        assertFieldFailure(48, "v=1;\u00E9", Rule.NON_PRINTABLE_ASCII);
    }

    @Test
    void rejectsANullFieldValue() throws IOException {
        Map<Integer, String> fields = requestFields();
        fields.put(3, null);
        assertPackFailure("0100", fields, Rule.MISSING_VALUE, 3);
    }

    @Test
    void rejectsDe32AtTwelveDigitsAndDe48Over999() throws IOException {
        Map<Integer, String> aliasTooLong = requestFields();
        aliasTooLong.put(32, "123456789012");
        assertPackFailure("0100", aliasTooLong, Rule.LENGTH_OUT_OF_RANGE, 32);

        Map<Integer, String> de48TooLong = requestFields();
        de48TooLong.put(48, "x".repeat(1000));
        assertPackFailure("0100", de48TooLong, Rule.LENGTH_OUT_OF_RANGE, 48);

        Map<Integer, String> de48Empty = requestFields();
        de48Empty.put(48, "");
        assertPackFailure("0100", de48Empty, Rule.LENGTH_OUT_OF_RANGE, 48);
    }

    // --- MTI --------------------------------------------------------------------

    @Test
    void rejectsUnsupportedMtis() throws IOException {
        Map<Integer, String> fields = requestFields();
        for (String mti : List.of("0800", "010", "01A0", "0110")) {
            assertThatThrownBy(() -> Iso8583Packer.pack(mti, fields))
                    .as("MTI %s", mti)
                    .isInstanceOfSatisfying(Iso8583PackException.class, e -> {
                        if (mti.equals("0110")) {
                            assertThat(e.rule()).isEqualTo(Rule.MISSING_REQUIRED_FIELD);
                        } else {
                            assertThat(e.rule()).isEqualTo(Rule.UNSUPPORTED_MTI);
                        }
                        assertThat(e.dataElement()).isEqualTo(mti.equals("0110") ? 39 : -1);
                    });
        }
        assertThatThrownBy(() -> Iso8583Packer.pack(null, fields))
                .isInstanceOfSatisfying(Iso8583PackException.class,
                        e -> assertThat(e.rule()).isEqualTo(Rule.UNSUPPORTED_MTI));
    }

    // --- Layer boundary ---------------------------------------------------------

    @Test
    void packingIsBodyOnlyAndDoesNotRunTheDe48Schema() throws IOException {
        Map<Integer, String> fields = requestFields();
        fields.put(48, "b=2;a=1");

        String packed = Iso8583Packer.pack("0100", fields);
        Iso8583Message scanned = Iso8583Scanner.scan(packed);
        assertThat(scanned.field(48)).isEqualTo("b=2;a=1");

        assertThatThrownBy(() -> Iso8583De48RequestParser.parse(scanned.field(48)))
                .as("the canonical DE48 schema remains a separate downstream layer")
                .isInstanceOf(Iso8583De48Exception.class);
    }

    // --- Helpers ----------------------------------------------------------------

    private static void assertFieldFailure(
            int dataElement, String value, Rule expected) throws IOException {
        Map<Integer, String> fields = requestFields();
        fields.put(dataElement, value);
        assertPackFailure("0100", fields, expected, dataElement);
    }

    private static void assertPackFailure(
            String mti, Map<Integer, String> fields, Rule rule, int dataElement) {
        assertThatThrownBy(() -> Iso8583Packer.pack(mti, fields))
                .isInstanceOfSatisfying(Iso8583PackException.class, e -> {
                    assertThat(e.rule()).isEqualTo(rule);
                    assertThat(e.dataElement()).isEqualTo(dataElement);
                    assertThat(e.getMessage()).doesNotContain("=", ";");
                });
    }

    private static Map<Integer, String> requestFields() throws IOException {
        return new LinkedHashMap<>(
                Iso8583Scanner.scan(bodyOf(require("V01-approved-request"))).fields());
    }

    private static Map<Integer, String> responseFields() throws IOException {
        return new LinkedHashMap<>(
                Iso8583Scanner.scan(bodyOf(require("V02-approved-response"))).fields());
    }

    private static String bodyOf(JsonNode vector) {
        return new String(
                HexFormat.of().parseHex(vector.get("hex").asText()),
                StandardCharsets.ISO_8859_1);
    }

    private static JsonNode require(String id) throws IOException {
        JsonNode node = packedById().get(id);
        assertThat(node).as("approved fixture %s", id).isNotNull();
        return node;
    }

    private static long count(String expected) throws IOException {
        return vectors(expected).count();
    }

    private static Stream<JsonNode> vectors(String expected) throws IOException {
        JsonNode vectors = JSON.readTree(new File(PACKED.toString())).get("vectors");
        Stream<JsonNode> stream = java.util.stream.StreamSupport.stream(
                vectors.spliterator(), false);
        return expected == null
                ? stream
                : stream.filter(v -> expected.equals(v.get("expected").asText()));
    }

    private static Map<String, JsonNode> packedById() {
        try {
            JsonNode root = JSON.readTree(new File(PACKED.toString()));
            Map<String, JsonNode> byId = new LinkedHashMap<>();
            for (JsonNode node : root.get("vectors")) {
                byId.put(node.get("id").asText(), node);
            }
            return byId;
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read approved corpus", e);
        }
    }
}
