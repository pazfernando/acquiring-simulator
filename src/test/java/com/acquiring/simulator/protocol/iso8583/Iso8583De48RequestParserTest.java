package com.acquiring.simulator.protocol.iso8583;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.acquiring.simulator.protocol.iso8583.Iso8583De48Exception.Rule;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Strict canonical DE48 request parser: corpus oracle plus schema-only rules.
 *
 * <p>Corpus cases rebuild the body from the authoritative {@code hex} octets in
 * the approved packed vectors and extract DE48 with the accepted structural
 * scanner. Fixtures labelled {@code SCHEMA_CANONICAL}/{@code SCHEMA_ENUM}/
 * {@code SCHEMA_LENGTH} are used as the schema negative oracle; every other
 * structurally accepted 0100 fixture must parse at this layer even when its
 * business verdict belongs to domain, admission or binding. Focused cases cover
 * canonical rules the corpus does not exercise; they are not presented as
 * fixture outcomes.</p>
 */
class Iso8583De48RequestParserTest {

    private static final Path PACKED =
            Path.of("docs/ai-sdlc/work-items/WI-ACQ-OBS-001/vectors/packed-vectors.json");
    private static final ObjectMapper JSON = new ObjectMapper();

    private static final String BASE =
            "v=1;op=A;aid=AUTH-001;cid=CORR-001;tid=TX-001;att=ATT-001;acq=ACQ-A;"
                    + "mid=MERCHANT-A;term=TERMINAL-A;pay=PAY-001;gen=1;fixture=SIM-APP-001;"
                    + "cvm=VERIFIED;crypto=1;risk=PASS;sent=20261118120000";

    private static final Map<String, Rule> SCHEMA_NEGATIVES = Map.ofEntries(
            Map.entry("N-duplicate-key", Rule.DUPLICATE_KEY),
            Map.entry("N-unknown-key", Rule.UNKNOWN_KEY),
            Map.entry("N-escaped", Rule.INVALID_VALUE_ALPHABET),
            Map.entry("N-ref-129", Rule.VALUE_LENGTH_OUT_OF_RANGE),
            Map.entry("K-empty-key", Rule.EMPTY_FIELD),
            Map.entry("K-empty-value", Rule.EMPTY_FIELD),
            Map.entry("K-out-of-order", Rule.OUT_OF_ORDER_KEY));

    // --- Corpus oracle ---------------------------------------------------------

    static Stream<Arguments> schemaPositiveRequestFixtures() {
        return corpus().values().stream()
                .filter(Iso8583De48RequestParserTest::isStructurallyAcceptedRequest)
                .filter(vector -> !SCHEMA_NEGATIVES.containsKey(vector.get("id").asText()))
                .map(vector -> Arguments.of(vector.get("id").asText(), de48Of(vector)));
    }

    @ParameterizedTest(name = "{0} satisfies the canonical request schema")
    @MethodSource("schemaPositiveRequestFixtures")
    void acceptsEveryOtherwiseAcceptedRequestFixture(String id, String de48) {
        Iso8583De48Request request = Iso8583De48RequestParser.parse(de48);

        List<String> keys = new ArrayList<>(request.values().keySet());
        assertThat(keys).isEqualTo(
                Iso8583De48RequestParser.REQUEST_KEYS.subList(0, keys.size()));
        assertThat(request.values()).isEqualTo(rawPairs(de48));
        assertThat(request.has("prior")).isEqualTo(rawPairs(de48).containsKey("prior"));
    }

    static Stream<Arguments> schemaNegativeRequestFixtures() {
        return SCHEMA_NEGATIVES.keySet().stream()
                .map(id -> Arguments.of(id, de48Of(corpus().get(id)),
                        SCHEMA_NEGATIVES.get(id)));
    }

    @ParameterizedTest(name = "{0} is rejected as {2}")
    @MethodSource("schemaNegativeRequestFixtures")
    void rejectsCorpusSchemaNegativesWithTheirCanonicalRule(
            String id, String de48, Rule expected) {
        assertFailsWith(expected, de48);
    }

    @Test
    void exact999ByteRequestRemainsSchemaValidButIsNotAdmissionChecked() {
        Iso8583De48Request request =
                Iso8583De48RequestParser.parse(de48Of(corpus().get("B-DE48-exact-999")));

        assertThat(request.values()).hasSize(17);
        assertThat(request.value("prior")).hasSize(64);
    }

    @Test
    void eightHundredTwentySevenByteRequestIsSchemaValidBeforeAdmission() {
        Iso8583De48Request request =
                Iso8583De48RequestParser.parse(de48Of(corpus().get("CAP-request-827")));

        assertThat(request.values()).hasSize(17);
    }

    // --- Canonical form --------------------------------------------------------

    @Test
    void acceptsMinimalRequestInExactOrderWithoutPrior() {
        Iso8583De48Request request = Iso8583De48RequestParser.parse(BASE);

        assertThat(request.values().keySet()).containsExactlyElementsOf(
                Iso8583De48RequestParser.REQUEST_KEYS.subList(0, 16));
        assertThat(request.value("sent")).isEqualTo("20261118120000");
        assertThat(request.has("prior")).isFalse();
    }

    @Test
    void acceptsOptionalPriorOnlyAsTheLastPair() {
        Iso8583De48Request request =
                Iso8583De48RequestParser.parse(BASE + ";prior=AUTH-000");

        assertThat(request.values().keySet()).containsExactlyElementsOf(
                Iso8583De48RequestParser.REQUEST_KEYS);
        assertThat(request.value("prior")).isEqualTo("AUTH-000");
    }

    @Test
    void preservesValueBytesWithoutNormalization() {
        Iso8583De48Request request =
                Iso8583De48RequestParser.parse(with("aid", "AuTh.001:0-1"));

        assertThat(request.value("aid")).isEqualTo("AuTh.001:0-1");
        assertThat(request.value("cvm")).isEqualTo("VERIFIED");
    }

    @Test
    void rejectsTrailingDelimiter() {
        assertFailsWith(Rule.TRAILING_DELIMITER, BASE + ";");
    }

    @Test
    void rejectsEmptyPairBetweenDelimiters() {
        assertFailsWith(Rule.EMPTY_FIELD, BASE.replace(";risk=", ";;risk="));
    }

    @Test
    void rejectsPairWithoutEquals() {
        assertFailsWith(Rule.MALFORMED_FIELD, BASE.replace(";aid=AUTH-001", ";aidAUTH-001"));
    }

    @Test
    void rejectsNestedSecondEquals() {
        assertFailsWith(Rule.MALFORMED_FIELD, with("aid", "A=B"));
    }

    @Test
    void rejectsEmptyKeyAndEmptyValue() {
        assertFailsWith(Rule.EMPTY_FIELD, BASE + ";=x");
        assertFailsWith(Rule.EMPTY_FIELD, with("pay", ""));
    }

    @Test
    void rejectsSpacesInValue() {
        assertFailsWith(Rule.INVALID_VALUE_ALPHABET, with("risk", "PASS "));
    }

    @Test
    void rejectsSpaceBeforeKeyAsUnknownCaseSensitiveKey() {
        assertFailsWith(Rule.UNKNOWN_KEY, BASE.replace("v=1;op=A", "v=1; op=A"));
    }

    @Test
    void rejectsUpperCaseKeyAsUnknownBecauseKeysAreCaseSensitive() {
        assertFailsWith(Rule.UNKNOWN_KEY, BASE.replaceFirst("^v=1;", "V=1;"));
    }

    @Test
    void rejectsPercentEncoding() {
        assertFailsWith(Rule.INVALID_VALUE_ALPHABET, with("pay", "PAY%2D001"));
    }

    @Test
    void rejectsBackslashEscaping() {
        assertFailsWith(Rule.INVALID_VALUE_ALPHABET, with("pay", "PAY\\001"));
    }

    @Test
    void rejectsDuplicateKeyRatherThanMergingIt() {
        assertFailsWith(Rule.DUPLICATE_KEY, BASE + ";v=1");
        assertFailsWith(Rule.DUPLICATE_KEY, with("aid", "AUTH-002") + ";aid=AUTH-001");
    }

    @Test
    void rejectsUnknownKey() {
        assertFailsWith(Rule.UNKNOWN_KEY, BASE + ";x=1");
    }

    @Test
    void rejectsOutOfOrderKeys() {
        String swapped = "op=A;v=1" + BASE.substring(BASE.indexOf(";aid="));
        assertFailsWith(Rule.OUT_OF_ORDER_KEY, swapped);
        assertFailsWith(Rule.OUT_OF_ORDER_KEY,
                BASE.replace(";risk=PASS;sent=", ";prior=X;sent=")
                        .replace(";sent=20261118120000", ";risk=PASS;sent=20261118120000"));
    }

    @Test
    void rejectsMissingRequiredKey() {
        assertFailsWith(Rule.MISSING_REQUIRED_KEY, without("risk"));
        assertFailsWith(Rule.MISSING_REQUIRED_KEY, without("v"));
        assertFailsWith(Rule.MISSING_REQUIRED_KEY, without("sent"));
    }

    @Test
    void rejectsAggregateOver999BytesBeforeAnyPairInspection() {
        assertFailsWith(Rule.DE48_TOO_LARGE, "a".repeat(1000));
    }

    @Test
    void rejectsNullAndEmptyInput() {
        assertFailsWith(Rule.EMPTY_FIELD, "");
        assertThatThrownBy(() -> Iso8583De48RequestParser.parse(null))
                .isInstanceOf(Iso8583De48Exception.class)
                .satisfies(thrown -> assertThat(((Iso8583De48Exception) thrown).rule())
                        .isEqualTo(Rule.EMPTY_FIELD));
    }

    // --- Per-key bounds --------------------------------------------------------

    @Test
    void acceptsAndRejectsReferenceBounds() {
        assertThat(Iso8583De48RequestParser.parse(with("aid", "A".repeat(64)))
                .value("aid")).hasSize(64);
        assertFailsWith(Rule.VALUE_LENGTH_OUT_OF_RANGE, with("aid", "A".repeat(65)));

        assertThat(Iso8583De48RequestParser.parse(with("acq", "A".repeat(128)))
                .value("acq")).hasSize(128);
        assertFailsWith(Rule.VALUE_LENGTH_OUT_OF_RANGE, with("acq", "A".repeat(129)));

        assertThat(Iso8583De48RequestParser.parse(BASE + ";prior=" + "A".repeat(64))
                .value("prior")).hasSize(64);
        assertFailsWith(Rule.VALUE_LENGTH_OUT_OF_RANGE,
                BASE + ";prior=" + "A".repeat(65));
    }

    @Test
    void sentMustBeExactlyFourteenOctets() {
        assertFailsWith(Rule.VALUE_LENGTH_OUT_OF_RANGE, with("sent", "2026111812000"));
        assertFailsWith(Rule.VALUE_LENGTH_OUT_OF_RANGE, with("sent", "202611181200000"));
        assertFailsWith(Rule.INVALID_DIGITS, with("sent", "2026111812000A"));
    }

    // --- Literals, enums and number form --------------------------------------

    @Test
    void versionAndOperationAreExactLiterals() {
        assertFailsWith(Rule.INVALID_LITERAL, with("v", "2"));
        assertFailsWith(Rule.INVALID_LITERAL, with("op", "B"));
    }

    @Test
    void cvmCryptoAndRiskAreClosedEnums() {
        assertThat(Iso8583De48RequestParser.parse(with("cvm", "NOT_REQUIRED"))
                .value("cvm")).isEqualTo("NOT_REQUIRED");
        assertThat(Iso8583De48RequestParser.parse(with("crypto", "0"))
                .value("crypto")).isEqualTo("0");
        assertThat(Iso8583De48RequestParser.parse(with("risk", "REJECT"))
                .value("risk")).isEqualTo("REJECT");

        assertFailsWith(Rule.INVALID_ENUM, with("cvm", "REQUIRED"));
        assertFailsWith(Rule.INVALID_ENUM, with("crypto", "2"));
        assertFailsWith(Rule.INVALID_ENUM, with("risk", "pass"));
    }

    @Test
    void generationRejectsZeroLeadingZerosAndOverflow() {
        assertThat(Iso8583De48RequestParser.parse(with("gen", "2147483647"))
                .value("gen")).isEqualTo("2147483647");

        assertFailsWith(Rule.INVALID_NUMBER, with("gen", "0"));
        assertFailsWith(Rule.INVALID_NUMBER, with("gen", "01"));
        assertFailsWith(Rule.INVALID_NUMBER, with("gen", "0000000001"));
        assertFailsWith(Rule.INVALID_NUMBER, with("gen", "2147483648"));
        assertFailsWith(Rule.VALUE_LENGTH_OUT_OF_RANGE, with("gen", "12345678901"));
        assertFailsWith(Rule.INVALID_DIGITS, with("gen", "1A"));
    }

    // --- Helpers ---------------------------------------------------------------

    private static void assertFailsWith(Rule expected, String input) {
        assertThatThrownBy(() -> Iso8583De48RequestParser.parse(input))
                .isInstanceOf(Iso8583De48Exception.class)
                .satisfies(thrown -> assertThat(((Iso8583De48Exception) thrown).rule())
                        .isEqualTo(expected));
    }

    private static String with(String key, String value) {
        return Stream.of(BASE.split(";", -1))
                .map(pair -> pair.startsWith(key + "=") ? key + "=" + value : pair)
                .collect(Collectors.joining(";"));
    }

    private static String without(String key) {
        return Stream.of(BASE.split(";", -1))
                .filter(pair -> !pair.startsWith(key + "="))
                .collect(Collectors.joining(";"));
    }

    private static Map<String, String> rawPairs(String de48) {
        Map<String, String> pairs = new LinkedHashMap<>();
        for (String segment : de48.split(";", -1)) {
            int equals = segment.indexOf('=');
            pairs.put(segment.substring(0, equals), segment.substring(equals + 1));
        }
        return pairs;
    }

    private static boolean isStructurallyAcceptedRequest(JsonNode vector) {
        try {
            return Iso8583Scanner.scan(bodyOf(vector)).mti().equals("0100");
        } catch (RuntimeException notStructurallyAccepted) {
            return false;
        }
    }

    private static String de48Of(JsonNode vector) {
        return Iso8583Scanner.scan(bodyOf(vector)).field(48);
    }

    private static String bodyOf(JsonNode vector) {
        byte[] octets = HexFormat.of().parseHex(vector.get("hex").asText());
        return new String(octets, StandardCharsets.ISO_8859_1);
    }

    private static Map<String, JsonNode> corpus() {
        try {
            JsonNode root = JSON.readTree(new File(PACKED.toString()));
            Map<String, JsonNode> byId = new LinkedHashMap<>();
            for (JsonNode vector : root.get("vectors")) {
                byId.put(vector.get("id").asText(), vector);
            }
            return byId;
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read approved packed vectors", e);
        }
    }
}
