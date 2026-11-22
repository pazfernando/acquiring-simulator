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
 * Strict canonical DE48 response parser: corpus oracle plus schema-only rules.
 *
 * <p>Corpus cases rebuild the body from the authoritative {@code hex} octets in
 * the approved packed vectors and extract DE48 with the accepted structural
 * scanner. The twenty-two {@code ACCEPT} 0110 fixtures are the schema-positive
 * oracle. The four response fixtures labelled {@code SCHEMA_CANONICAL} or
 * {@code SCHEMA_ENUM} are the schema-negative oracle. The remaining fifteen
 * 0110 fixtures belong to the P4 matrix, DE38/instruction binding, attestation
 * or domain layers: they must parse at this canonical layer and their business
 * verdict stays deferred, never claimed as a payment outcome. Focused cases
 * cover canonical rules the corpus does not exercise.</p>
 */
class Iso8583De48ResponseParserTest {

    private static final Path PACKED =
            Path.of("docs/ai-sdlc/work-items/WI-ACQ-OBS-001/vectors/packed-vectors.json");
    private static final ObjectMapper JSON = new ObjectMapper();

    private static final String REPLY_TAIL =
            "actor=ISSUER;stage=ISSUER_REPLIED;result=APPROVED;reason=APPROVED;"
                    + "evidence=EVID-001;at=20261118120001";

    private static final String BASE =
            "v=1;op=A;aid=AUTH-001;cid=CORR-001;tid=TX-001;att=ATT-001;acq=ACQ-A;"
                    + "mid=MERCHANT-A;term=TERMINAL-A;pay=PAY-001;gen=1;fixture=SIM-APP-001;"
                    + "cvm=VERIFIED;crypto=1;risk=PASS;sent=20261118120000;" + REPLY_TAIL;

    private record Expected(Rule rule, String key) {
    }

    private static final Map<String, Expected> SCHEMA_NEGATIVES = Map.ofEntries(
            Map.entry("N-wrong-op", new Expected(Rule.INVALID_LITERAL, "op")),
            Map.entry("N-missing-result",
                    new Expected(Rule.MISSING_REQUIRED_KEY, "result")),
            Map.entry("N-missing-actor",
                    new Expected(Rule.MISSING_REQUIRED_KEY, "actor")),
            Map.entry("N-missing-evidence",
                    new Expected(Rule.MISSING_REQUIRED_KEY, "evidence")));

    /** Labels owned by later layers; a schema parse must neither claim nor reject them. */
    private static final Map<String, String> DEFERRED_LABELS = new LinkedHashMap<>();

    static {
        DEFERRED_LABELS.put("EVIDENCE_MATRIX", "P4 actor/stage/result matrix");
        DEFERRED_LABELS.put("BINDING_INSTRUCTION", "instruction binding");
        DEFERRED_LABELS.put("BINDING_AUTH_REFERENCE", "DE38 trusted-evidence binding");
        DEFERRED_LABELS.put("AUTH_ATTESTATION", "attestation and principal binding");
        DEFERRED_LABELS.put("BUSINESS_CONFLICT_QUARANTINE",
                "conditional business admission/quarantine");
        DEFERRED_LABELS.put("DOMAIN_DE7", "domain DE7/sent consistency");
        DEFERRED_LABELS.put("DOMAIN_DATE", "domain Gregorian date");
    }

    // --- Corpus oracle ---------------------------------------------------------

    static Stream<Arguments> schemaPositiveResponseFixtures() {
        return responseFixtures()
                .filter(vector -> "ACCEPT".equals(vector.get("expected").asText()))
                .map(vector -> Arguments.of(vector.get("id").asText(), de48Of(vector)));
    }

    @ParameterizedTest(name = "{0} satisfies the canonical response schema")
    @MethodSource("schemaPositiveResponseFixtures")
    void acceptsEveryApprovedResponseFixture(String id, String de48) {
        Iso8583De48Response response = Iso8583De48ResponseParser.parse(de48);

        Map<String, String> raw = rawPairs(de48);
        List<String> expectedKeys =
                new ArrayList<>(Iso8583De48ResponseParser.REQUEST_ECHO_KEYS);
        if (raw.containsKey(Iso8583De48ResponseParser.PRIOR_KEY)) {
            expectedKeys.add(Iso8583De48ResponseParser.PRIOR_KEY);
        }
        expectedKeys.addAll(Iso8583De48ResponseParser.RESPONSE_KEYS);

        assertThat(response.values().keySet()).containsExactlyElementsOf(expectedKeys);
        assertThat(response.values()).isEqualTo(raw);
        assertThat(response.has(Iso8583De48ResponseParser.PRIOR_KEY))
                .isEqualTo(raw.containsKey(Iso8583De48ResponseParser.PRIOR_KEY));
    }

    static Stream<Arguments> schemaNegativeResponseFixtures() {
        return SCHEMA_NEGATIVES.entrySet().stream()
                .map(entry -> Arguments.of(
                        entry.getKey(),
                        de48Of(corpus().get(entry.getKey())),
                        entry.getValue()));
    }

    @ParameterizedTest(name = "{0} is rejected as {2}")
    @MethodSource("schemaNegativeResponseFixtures")
    void rejectsCorpusSchemaNegativesWithTheirCanonicalRule(
            String id, String de48, Expected expected) {
        assertFailsWith(expected.rule(), expected.key(), de48);
    }

    static Stream<Arguments> deferredResponseFixtures() {
        return responseFixtures()
                .filter(vector -> !"ACCEPT".equals(vector.get("expected").asText()))
                .filter(vector -> !SCHEMA_NEGATIVES.containsKey(vector.get("id").asText()))
                .map(vector -> Arguments.of(
                        vector.get("id").asText(),
                        vector.get("expected").asText(),
                        de48Of(vector)));
    }

    @ParameterizedTest(name = "{0} ({1}) is schema-valid and deferred to its owner layer")
    @MethodSource("deferredResponseFixtures")
    void defersLaterLayerResponseFixturesAtTheCanonicalLayer(
            String id, String expectedLabel, String de48) {
        assertThat(DEFERRED_LABELS)
                .as("new expected label without a recorded owner layer: %s", expectedLabel)
                .containsKey(expectedLabel);

        Iso8583De48Response response = Iso8583De48ResponseParser.parse(de48);
        assertThat(response.values()).isEqualTo(rawPairs(de48));
        assertThat(response.value("actor")).isNotNull();
        assertThat(response.value("at")).hasSize(14);
    }

    @Test
    void responseCorpusPartitionsIntoPositivesSchemaNegativesAndDeferred() {
        long total = responseFixtures().count();
        long positives = responseFixtures()
                .filter(vector -> "ACCEPT".equals(vector.get("expected").asText()))
                .count();
        long schemaNegatives = SCHEMA_NEGATIVES.size();
        long deferred = responseFixtures()
                .filter(vector -> !"ACCEPT".equals(vector.get("expected").asText()))
                .filter(vector -> !SCHEMA_NEGATIVES.containsKey(vector.get("id").asText()))
                .count();

        assertThat(total).as("approved 0110 fixtures").isEqualTo(41);
        assertThat(positives).as("22 ACCEPT response fixtures").isEqualTo(22);
        assertThat(schemaNegatives).as("response schema negatives").isEqualTo(4);
        assertThat(deferred).as("response fixtures owned by later layers").isEqualTo(15);
        assertThat(positives + schemaNegatives + deferred).isEqualTo(total);
    }

    @Test
    void exact999ByteResponseRemainsSchemaValidButIsNotAdmissionChecked() {
        Iso8583De48Response response =
                Iso8583De48ResponseParser.parse(de48Of(corpus().get("CAP-response-3")));

        assertThat(response.values()).hasSize(23);
        assertThat(response.value(Iso8583De48ResponseParser.PRIOR_KEY)).hasSize(64);
        assertThat(response.value("evidence")).hasSize(64);
    }

    // --- Canonical form --------------------------------------------------------

    @Test
    void acceptsMinimalResponseWithoutPrior() {
        Iso8583De48Response response = Iso8583De48ResponseParser.parse(BASE);

        List<String> expectedKeys =
                new ArrayList<>(Iso8583De48ResponseParser.REQUEST_ECHO_KEYS);
        expectedKeys.addAll(Iso8583De48ResponseParser.RESPONSE_KEYS);
        assertThat(response.values().keySet()).containsExactlyElementsOf(expectedKeys);
        assertThat(response.has(Iso8583De48ResponseParser.PRIOR_KEY)).isFalse();
    }

    @Test
    void acceptsPriorOnlyBetweenSentAndActor() {
        Iso8583De48Response response = Iso8583De48ResponseParser.parse(
                BASE.replace(";sent=20261118120000;", ";sent=20261118120000;prior=AUTH-000;"));

        assertThat(response.value(Iso8583De48ResponseParser.PRIOR_KEY))
                .isEqualTo("AUTH-000");
        assertThat(response.values().keySet()).containsExactly(
                Stream.concat(
                                Stream.concat(
                                        Iso8583De48ResponseParser.REQUEST_ECHO_KEYS.stream(),
                                        Stream.of(Iso8583De48ResponseParser.PRIOR_KEY)),
                                Iso8583De48ResponseParser.RESPONSE_KEYS.stream())
                        .toArray(String[]::new));
    }

    @Test
    void preservesEchoAndTailBytesWithoutNormalization() {
        Iso8583De48Response response = Iso8583De48ResponseParser.parse(
                with("aid", "AuTh.001:0-1").replace(
                        "evidence=EVID-001", "evidence=Ev-1.A_b:c"));

        assertThat(response.value("aid")).isEqualTo("AuTh.001:0-1");
        assertThat(response.value("evidence")).isEqualTo("Ev-1.A_b:c");
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
        assertFailsWith(Rule.EMPTY_FIELD, with("evidence", ""));
    }

    @Test
    void rejectsSpacesAndPercentEncoding() {
        assertFailsWith(Rule.INVALID_VALUE_ALPHABET, with("evidence", "EVID 001"));
        assertFailsWith(Rule.INVALID_VALUE_ALPHABET, with("evidence", "EVID%2D001"));
    }

    @Test
    void rejectsUnknownAndDuplicateKeys() {
        assertFailsWith(Rule.UNKNOWN_KEY, BASE + ";x=1");
        assertFailsWith(Rule.UNKNOWN_KEY, BASE.replace("actor=ISSUER", "Actor=ISSUER"));
        assertFailsWith(Rule.DUPLICATE_KEY, BASE + ";actor=ISSUER");
        assertFailsWith(Rule.DUPLICATE_KEY,
                BASE.replace("actor=ISSUER;", "actor=NETWORK;actor=ISSUER;"));
    }

    @Test
    void rejectsNullAndEmptyInput() {
        assertFailsWith(Rule.EMPTY_FIELD, "");
        assertThatThrownBy(() -> Iso8583De48ResponseParser.parse(null))
                .isInstanceOf(Iso8583De48Exception.class)
                .satisfies(thrown -> assertThat(((Iso8583De48Exception) thrown).rule())
                        .isEqualTo(Rule.EMPTY_FIELD));
    }

    @Test
    void rejectsAggregateOver999BytesBeforeAnyPairInspection() {
        assertFailsWith(Rule.DE48_TOO_LARGE, "a".repeat(1000));
    }

    // --- Full response order and presence --------------------------------------

    @Test
    void rejectsActorBeforeSent() {
        assertFailsWith(Rule.OUT_OF_ORDER_KEY, "actor",
                BASE.replace(";sent=20261118120000;actor=ISSUER",
                        ";actor=ISSUER;sent=20261118120000"));
    }

    @Test
    void rejectsStageBeforeActor() {
        assertFailsWith(Rule.OUT_OF_ORDER_KEY, "stage",
                BASE.replace("actor=ISSUER;stage=ISSUER_REPLIED",
                        "stage=ISSUER_REPLIED;actor=ISSUER"));
    }

    @Test
    void rejectsReorderedTailKeys() {
        assertFailsWith(Rule.OUT_OF_ORDER_KEY, "reason",
                BASE.replace("result=APPROVED;reason=APPROVED",
                        "reason=APPROVED;result=APPROVED"));
    }

    @Test
    void rejectsPriorAfterTheResponseTail() {
        assertFailsWith(Rule.OUT_OF_ORDER_KEY, Iso8583De48ResponseParser.PRIOR_KEY,
                BASE + ";" + Iso8583De48ResponseParser.PRIOR_KEY + "=AUTH-000");
    }

    @Test
    void rejectsPriorBeforeTheEchoedRequestKeys() {
        assertFailsWith(Rule.OUT_OF_ORDER_KEY, Iso8583De48ResponseParser.PRIOR_KEY,
                Iso8583De48ResponseParser.PRIOR_KEY + "=AUTH-000;" + BASE);
    }

    @Test
    void rejectsEveryMissingResponseKey() {
        for (String key : Iso8583De48ResponseParser.RESPONSE_KEYS) {
            assertFailsWith(Rule.MISSING_REQUIRED_KEY, key, without(key));
        }
    }

    @Test
    void rejectsMissingEchoedRequestKey() {
        assertFailsWith(Rule.MISSING_REQUIRED_KEY, "risk", without("risk"));
        assertFailsWith(Rule.MISSING_REQUIRED_KEY, "v", without("v"));
        assertFailsWith(Rule.MISSING_REQUIRED_KEY, "sent", without("sent"));
    }

    // --- Per-field bounds, literals, enums and digit forms ---------------------

    @Test
    void evidenceIsBoundedToSixtyFourOctets() {
        assertThat(Iso8583De48ResponseParser.parse(with("evidence", "E".repeat(64)))
                .value("evidence")).hasSize(64);
        assertFailsWith(Rule.VALUE_LENGTH_OUT_OF_RANGE,
                with("evidence", "E".repeat(65)));
    }

    @Test
    void echoedReferenceBoundsRemainEnforced() {
        assertThat(Iso8583De48ResponseParser.parse(with("aid", "A".repeat(64)))
                .value("aid")).hasSize(64);
        assertFailsWith(Rule.VALUE_LENGTH_OUT_OF_RANGE, with("aid", "A".repeat(65)));

        assertThat(Iso8583De48ResponseParser.parse(with("acq", "A".repeat(128)))
                .value("acq")).hasSize(128);
        assertFailsWith(Rule.VALUE_LENGTH_OUT_OF_RANGE, with("acq", "A".repeat(129)));

        String withPrior = BASE.replace(
                ";sent=20261118120000;",
                ";sent=20261118120000;prior=" + "A".repeat(64) + ";");
        assertThat(Iso8583De48ResponseParser.parse(withPrior)
                .value(Iso8583De48ResponseParser.PRIOR_KEY)).hasSize(64);
        assertFailsWith(Rule.VALUE_LENGTH_OUT_OF_RANGE,
                withPrior.replace("A".repeat(64), "A".repeat(65)));
    }

    @Test
    void sentAndAtAreExactlyFourteenDigits() {
        assertThat(Iso8583De48ResponseParser.parse(with("sent", "20261118120000"))
                .value("sent")).hasSize(14);
        assertThat(Iso8583De48ResponseParser.parse(with("at", "20261118120001"))
                .value("at")).hasSize(14);

        assertFailsWith(Rule.VALUE_LENGTH_OUT_OF_RANGE, with("sent", "2026111812000"));
        assertFailsWith(Rule.VALUE_LENGTH_OUT_OF_RANGE, with("at", "202611181200001"));
        assertFailsWith(Rule.INVALID_DIGITS, with("sent", "2026111812000A"));
        assertFailsWith(Rule.INVALID_DIGITS, with("at", "2026111812000A"));
    }

    @Test
    void versionAndOperationRemainExactLiterals() {
        assertFailsWith(Rule.INVALID_LITERAL, with("v", "2"));
        assertFailsWith(Rule.INVALID_LITERAL, with("op", "B"));
        assertFailsWith(Rule.INVALID_LITERAL, with("op", "C"));
    }

    @Test
    void requestEnumsRemainClosed() {
        assertFailsWith(Rule.INVALID_ENUM, with("cvm", "REQUIRED"));
        assertFailsWith(Rule.INVALID_ENUM, with("crypto", "2"));
        assertFailsWith(Rule.INVALID_ENUM, with("risk", "pass"));
    }

    @Test
    void actorIsAClosedEnumWithoutNormalization() {
        for (String actor : List.of("ISSUER", "NETWORK", "PROCESSOR")) {
            assertThat(Iso8583De48ResponseParser.parse(with("actor", actor))
                    .value("actor")).isEqualTo(actor);
        }
        assertFailsWith(Rule.INVALID_ENUM, with("actor", "issuer"));
        assertFailsWith(Rule.INVALID_ENUM, with("actor", "ACQUIRER"));
    }

    @Test
    void stageIsAClosedEnum() {
        for (String stage : List.of(
                "ISSUER_REPLIED", "BEFORE_ISSUER", "POSSIBLE_ISSUER", "BEFORE_NETWORK")) {
            assertThat(Iso8583De48ResponseParser.parse(with("stage", stage))
                    .value("stage")).isEqualTo(stage);
        }
        assertFailsWith(Rule.INVALID_ENUM, with("stage", "issuer_replied"));
        assertFailsWith(Rule.INVALID_ENUM, with("stage", "AFTER_ISSUER"));
    }

    @Test
    void resultIsAClosedEnum() {
        for (String result : List.of(
                "APPROVED", "DECLINED", "PROCESSOR_REJECTED", "NOT_CONFIRMED")) {
            assertThat(Iso8583De48ResponseParser.parse(with("result", result))
                    .value("result")).isEqualTo(result);
        }
        assertFailsWith(Rule.INVALID_ENUM, with("result", "PROCESSOR_REJECT"));
        assertFailsWith(Rule.INVALID_ENUM, with("result", "UNKNOWN"));
    }

    @Test
    void reasonIsAClosedEnum() {
        for (String reason : List.of(
                "APPROVED", "DELIBERATE_DECLINE", "FORMAT", "DISALLOWED",
                "UNAVAILABLE", "UNCERTAIN")) {
            assertThat(Iso8583De48ResponseParser.parse(with("reason", reason))
                    .value("reason")).isEqualTo(reason);
        }
        assertFailsWith(Rule.INVALID_ENUM, with("reason", "DELIBERATE"));
        assertFailsWith(Rule.INVALID_ENUM, with("reason", "TIMEOUT"));
    }

    @Test
    void generationFormRemainsEnforced() {
        assertThat(Iso8583De48ResponseParser.parse(with("gen", "2147483647"))
                .value("gen")).isEqualTo("2147483647");

        assertFailsWith(Rule.INVALID_NUMBER, with("gen", "0"));
        assertFailsWith(Rule.INVALID_NUMBER, with("gen", "01"));
        assertFailsWith(Rule.INVALID_NUMBER, with("gen", "2147483648"));
        assertFailsWith(Rule.VALUE_LENGTH_OUT_OF_RANGE, with("gen", "12345678901"));
        assertFailsWith(Rule.INVALID_DIGITS, with("gen", "1A"));
    }

    // --- Explicitly deferred business/domain decisions -------------------------

    @Test
    void doesNotApplyTheP4ActorStageResultMatrix() {
        for (String id : List.of(
                "N-00-network", "N-05-processor", "N-30-issuer", "N-91-issuer",
                "N-unknown-code", "N-forged-actor")) {
            Iso8583De48Response response =
                    Iso8583De48ResponseParser.parse(de48Of(corpus().get(id)));
            assertThat(response.value("actor")).isNotNull();
            assertThat(response.value("result")).isNotNull();
        }

        // A contradictory combination is schema-valid; P4 ownership stays later.
        Iso8583De48Response contradictory = Iso8583De48ResponseParser.parse(
                with("actor", "NETWORK").replace("stage=ISSUER_REPLIED",
                        "stage=BEFORE_NETWORK"));
        assertThat(contradictory.value("actor")).isEqualTo("NETWORK");
        assertThat(contradictory.value("stage")).isEqualTo("BEFORE_NETWORK");
    }

    @Test
    void doesNotValidateGregorianDatesDe7OrActualEvidenceTime() {
        for (String id : List.of("N-response-DE7", "N-response-invalid-date")) {
            Iso8583De48Response response =
                    Iso8583De48ResponseParser.parse(de48Of(corpus().get(id)));
            assertThat(response.value("sent")).hasSize(14);
        }

        // Fourteen syntactic digits only: no calendar, ordering or actual-time claim.
        Iso8583De48Response beforeSent =
                Iso8583De48ResponseParser.parse(with("at", "20200101000000"));
        assertThat(beforeSent.value("at")).isEqualTo("20200101000000");
    }

    // --- Helpers ---------------------------------------------------------------

    private static void assertFailsWith(Rule expected, String input) {
        assertThatThrownBy(() -> Iso8583De48ResponseParser.parse(input))
                .isInstanceOf(Iso8583De48Exception.class)
                .satisfies(thrown -> assertThat(((Iso8583De48Exception) thrown).rule())
                        .isEqualTo(expected));
    }

    private static void assertFailsWith(Rule expected, String expectedKey, String input) {
        assertThatThrownBy(() -> Iso8583De48ResponseParser.parse(input))
                .isInstanceOf(Iso8583De48Exception.class)
                .satisfies(thrown -> {
                    assertThat(((Iso8583De48Exception) thrown).rule()).isEqualTo(expected);
                    assertThat(((Iso8583De48Exception) thrown).key()).isEqualTo(expectedKey);
                });
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

    private static Stream<JsonNode> responseFixtures() {
        return corpus().values().stream()
                .filter(Iso8583De48ResponseParserTest::isStructurallyAcceptedResponse);
    }

    private static boolean isStructurallyAcceptedResponse(JsonNode vector) {
        try {
            return Iso8583Scanner.scan(bodyOf(vector)).mti().equals("0110");
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
