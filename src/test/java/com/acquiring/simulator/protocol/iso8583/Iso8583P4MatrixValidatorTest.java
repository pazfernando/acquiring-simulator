package com.acquiring.simulator.protocol.iso8583;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.acquiring.simulator.protocol.iso8583.Iso8583P4MatrixException.Rule;
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
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * P4 actor/dispatch-stage/DE39/result/reason matrix over the approved corpus.
 *
 * <p>Corpus cases rebuild the 0110 body from the authoritative {@code hex}
 * octets, scan it structurally, read the physical DE39 and parse DE48 with the
 * strict response parser. The five fixtures labelled {@code EVIDENCE_MATRIX}
 * are the P4-negative oracle; every other later-layer label stays outside this
 * validator. All eleven approved tuples are exercised through the canonical
 * DE48 parser. The tests assert syntactic matrix eligibility only: no issuer
 * truth, evidence trust, DE38 binding, instruction binding or financial
 * admission is claimed.</p>
 */
class Iso8583P4MatrixValidatorTest {

    private static final Path PACKED =
            Path.of("docs/ai-sdlc/work-items/WI-ACQ-OBS-001/vectors/packed-vectors.json");
    private static final ObjectMapper JSON = new ObjectMapper();

    private static final String RESPONSE_TEMPLATE =
            "v=1;op=A;aid=AUTH-001;cid=CORR-001;tid=TX-001;att=ATT-001;acq=ACQ-A;"
                    + "mid=MERCHANT-A;term=TERMINAL-A;pay=PAY-001;gen=1;fixture=SIM-APP-001;"
                    + "cvm=VERIFIED;crypto=1;risk=PASS;sent=20261118120000;"
                    + "actor=%s;stage=%s;result=%s;reason=%s;evidence=EVID-001;"
                    + "at=20261118120001";

    private record Expected(Rule rule, String component) {
    }

    /** The five approved P4-negative fixtures and their exact first-failure rule. */
    private static final Map<String, Expected> P4_NEGATIVES = Map.of(
            "N-00-network", new Expected(Rule.ACTOR_STAGE_NOT_PERMITTED, "stage"),
            "N-05-processor", new Expected(Rule.ACTOR_STAGE_NOT_PERMITTED, "stage"),
            "N-30-issuer", new Expected(Rule.DE39_NOT_PERMITTED_FOR_STAGE, "de39"),
            "N-91-issuer", new Expected(Rule.DE39_NOT_PERMITTED_FOR_STAGE, "de39"),
            "N-unknown-code", new Expected(Rule.DE39_UNSUPPORTED, "de39"));

    /** Later-layer fixtures whose matrix tuple is syntactically eligible. */
    /** 0110 fixtures rejected earlier by the canonical DE48 schema. */
    private static final Set<String> SCHEMA_NEGATIVE_IDS = Set.of(
            "N-wrong-op", "N-missing-result", "N-missing-actor", "N-missing-evidence");

    private static final List<String> BINDING_OR_TRUST_FIXTURES = List.of(
            "N-forged-actor",
            "N-unresolvable-evidence",
            "N-unsolicited-DE38",
            "N-missing-DE38-asserted",
            "N-wrong-correlation",
            "N-wrong-context",
            "N-mismatch-amount");

    private static final List<Arguments> ALLOWED_TUPLES = List.of(
            Arguments.of("ISSUER", "ISSUER_REPLIED", "00", "APPROVED", "APPROVED"),
            Arguments.of("ISSUER", "ISSUER_REPLIED", "05", "DECLINED",
                    "DELIBERATE_DECLINE"),
            Arguments.of("PROCESSOR", "BEFORE_NETWORK", "30", "PROCESSOR_REJECTED",
                    "FORMAT"),
            Arguments.of("PROCESSOR", "BEFORE_NETWORK", "30", "PROCESSOR_REJECTED",
                    "DISALLOWED"),
            Arguments.of("NETWORK", "BEFORE_ISSUER", "30", "PROCESSOR_REJECTED",
                    "FORMAT"),
            Arguments.of("NETWORK", "BEFORE_ISSUER", "30", "PROCESSOR_REJECTED",
                    "DISALLOWED"),
            Arguments.of("NETWORK", "BEFORE_ISSUER", "91", "PROCESSOR_REJECTED",
                    "UNAVAILABLE"),
            Arguments.of("NETWORK", "POSSIBLE_ISSUER", "91", "NOT_CONFIRMED",
                    "UNCERTAIN"),
            Arguments.of("NETWORK", "POSSIBLE_ISSUER", "91", "NOT_CONFIRMED",
                    "UNAVAILABLE"),
            Arguments.of("PROCESSOR", "POSSIBLE_ISSUER", "91", "NOT_CONFIRMED",
                    "UNCERTAIN"),
            Arguments.of("PROCESSOR", "POSSIBLE_ISSUER", "91", "NOT_CONFIRMED",
                    "UNAVAILABLE"));

    // --- Approved matrix positives ---------------------------------------------

    static Stream<Arguments> allowedMatrixTuples() {
        return ALLOWED_TUPLES.stream();
    }

    @ParameterizedTest(name = "{0}/{1}/{2}/{3}/{4} is an approved matrix tuple")
    @MethodSource("allowedMatrixTuples")
    void acceptsEveryApprovedMatrixTuple(
            String actor, String stage, String de39, String result, String reason) {
        Iso8583De48Response response = Iso8583De48ResponseParser.parse(
                reply(actor, stage, result, reason));

        Iso8583P4MatrixDecision decision = Iso8583P4MatrixValidator.validate(
                response.value("actor"),
                response.value("stage"),
                de39,
                response.value("result"),
                response.value("reason"));

        assertThat(decision).isEqualTo(
                new Iso8583P4MatrixDecision(actor, stage, de39, result, reason));
    }

    static Stream<Arguments> acceptedResponseFixtures() {
        return responseFixtures()
                .filter(vector -> "ACCEPT".equals(vector.get("expected").asText()))
                .map(vector -> Arguments.of(vector.get("id").asText()));
    }

    @ParameterizedTest(name = "{0} is matrix-eligible")
    @MethodSource("acceptedResponseFixtures")
    void everyAcceptedResponseFixtureIsMatrixEligible(String id) {
        assertMatrixEligible(id);
    }

    static Stream<Arguments> deferredNonP4Fixtures() {
        return responseFixtures()
                .filter(vector -> !"ACCEPT".equals(vector.get("expected").asText()))
                .filter(vector -> !P4_NEGATIVES.containsKey(vector.get("id").asText()))
                .filter(vector -> !SCHEMA_NEGATIVE_IDS.contains(vector.get("id").asText()))
                .map(vector -> Arguments.of(
                        vector.get("id").asText(),
                        vector.get("expected").asText()));
    }

    @ParameterizedTest(name = "{0} ({1}) is matrix-eligible but owned by a later layer")
    @MethodSource("deferredNonP4Fixtures")
    void deferredNonP4FixturesRemainEligibleWithoutClaimingTheirBusinessLayer(
            String id, String expectedLabel) {
        assertThat(expectedLabel).isNotEqualTo("EVIDENCE_MATRIX");
        assertMatrixEligible(id);
    }

    @Test
    void responseFixturesPartitionIntoExactMatrixOutcomes() {
        long total = responseFixtures().count();
        long accepted = responseFixtures()
                .filter(vector -> "ACCEPT".equals(vector.get("expected").asText()))
                .count();
        long laterLayer = responseFixtures()
                .filter(vector -> !"ACCEPT".equals(vector.get("expected").asText()))
                .filter(vector -> !P4_NEGATIVES.containsKey(vector.get("id").asText()))
                .filter(vector -> !SCHEMA_NEGATIVE_IDS.contains(vector.get("id").asText()))
                .count();

        assertThat(total).as("structurally accepted 0110 fixtures").isEqualTo(41);
        assertThat(accepted).as("ACCEPT response fixtures").isEqualTo(22);
        assertThat(SCHEMA_NEGATIVE_IDS).as("schema-negative fixtures").hasSize(4);
        assertThat(P4_NEGATIVES).as("P4-negative fixtures").hasSize(5);
        assertThat(laterLayer).as("matrix-eligible later-layer fixtures").isEqualTo(10);
        assertThat(accepted + SCHEMA_NEGATIVE_IDS.size() + P4_NEGATIVES.size() + laterLayer)
                .isEqualTo(total);
    }

    @Test
    void approvedMatrixHasExactlyElevenTuples() {
        assertThat(ALLOWED_TUPLES).hasSize(11);
        assertThat(Iso8583P4MatrixValidator.SUPPORTED_DE39)
                .containsExactlyInAnyOrder("00", "05", "30", "91");
    }

    // --- Corpus P4 negatives ---------------------------------------------------

    @Test
    void p4CorpusNegativesAreExactlyTheFiveApprovedFixtures() {
        Set<String> ids = corpus().values().stream()
                .filter(Iso8583P4MatrixValidatorTest::isStructurallyAcceptedResponse)
                .filter(vector -> "EVIDENCE_MATRIX".equals(vector.get("expected").asText()))
                .map(vector -> vector.get("id").asText())
                .collect(java.util.stream.Collectors.toSet());

        assertThat(ids).containsExactlyInAnyOrderElementsOf(P4_NEGATIVES.keySet());
        assertThat(ids).hasSize(5);
    }

    static Stream<Arguments> p4NegativeFixtures() {
        return P4_NEGATIVES.entrySet().stream()
                .map(entry -> Arguments.of(entry.getKey(), entry.getValue()));
    }

    @ParameterizedTest(name = "{0} fails the matrix as {1}")
    @MethodSource("p4NegativeFixtures")
    void rejectsEachP4NegativeWithItsExactRule(String id, Expected expected) {
        JsonNode vector = corpus().get(id);
        Iso8583Message message = Iso8583Scanner.scan(bodyOf(vector));
        Iso8583De48Response response = Iso8583De48ResponseParser.parse(message.field(48));

        assertMatrixFails(
                expected,
                response.value("actor"),
                response.value("stage"),
                message.field(39),
                response.value("result"),
                response.value("reason"));
    }

    @Test
    void diagnosticsDoNotEchoTheRejectedTuple() {
        for (String id : P4_NEGATIVES.keySet()) {
            Iso8583Message message = Iso8583Scanner.scan(bodyOf(corpus().get(id)));
            Iso8583De48Response response = Iso8583De48ResponseParser.parse(message.field(48));
            String actor = response.value("actor");
            String stage = response.value("stage");
            String de39 = message.field(39);
            String result = response.value("result");
            String reason = response.value("reason");

            assertThatThrownBy(() -> Iso8583P4MatrixValidator.validate(
                    actor, stage, de39, result, reason))
                    .isInstanceOf(Iso8583P4MatrixException.class)
                    .satisfies(thrown -> {
                        String diagnostic = thrown.getMessage();
                        assertThat(diagnostic)
                                .doesNotContain(actor)
                                .doesNotContain(stage)
                                .doesNotContain(de39)
                                .doesNotContain(result)
                                .doesNotContain(reason)
                                .doesNotContain("=")
                                .doesNotContain(";");
                    });
        }
    }

    // --- Boundaries named by P4 -------------------------------------------------

    @Test
    void rejectedIssuerCodesAreNeverPermittedOutsideIssuerReplied() {
        assertMatrixFails(new Expected(Rule.ACTOR_STAGE_NOT_PERMITTED, "stage"),
                "NETWORK", "ISSUER_REPLIED", "00", "APPROVED", "APPROVED");
        assertMatrixFails(new Expected(Rule.ACTOR_STAGE_NOT_PERMITTED, "stage"),
                "PROCESSOR", "ISSUER_REPLIED", "05", "DECLINED",
                "DELIBERATE_DECLINE");
        assertMatrixFails(new Expected(Rule.ACTOR_STAGE_NOT_PERMITTED, "stage"),
                "ISSUER", "BEFORE_NETWORK", "30", "PROCESSOR_REJECTED", "FORMAT");
        assertMatrixFails(new Expected(Rule.ACTOR_STAGE_NOT_PERMITTED, "stage"),
                "PROCESSOR", "BEFORE_ISSUER", "30", "PROCESSOR_REJECTED", "FORMAT");
        assertMatrixFails(new Expected(Rule.ACTOR_STAGE_NOT_PERMITTED, "stage"),
                "ISSUER", "POSSIBLE_ISSUER", "91", "NOT_CONFIRMED", "UNCERTAIN");
    }

    @Test
    void thirtyIsNeverAnIssuerDecision() {
        assertMatrixFails(new Expected(Rule.DE39_NOT_PERMITTED_FOR_STAGE, "de39"),
                "ISSUER", "ISSUER_REPLIED", "30", "APPROVED", "APPROVED");
        assertMatrixFails(new Expected(Rule.DE39_NOT_PERMITTED_FOR_STAGE, "de39"),
                "ISSUER", "ISSUER_REPLIED", "30", "PROCESSOR_REJECTED", "DISALLOWED");
        assertMatrixFails(new Expected(Rule.DE39_NOT_PERMITTED_FOR_STAGE, "de39"),
                "ISSUER", "ISSUER_REPLIED", "30", "DECLINED",
                "DELIBERATE_DECLINE");
    }

    @Test
    void ninetyOneIsNeverAnIssuerDecline() {
        assertMatrixFails(new Expected(Rule.DE39_NOT_PERMITTED_FOR_STAGE, "de39"),
                "ISSUER", "ISSUER_REPLIED", "91", "DECLINED",
                "DELIBERATE_DECLINE");
        assertMatrixFails(new Expected(Rule.RESULT_NOT_PERMITTED, "result"),
                "NETWORK", "BEFORE_ISSUER", "91", "DECLINED",
                "DELIBERATE_DECLINE");
        assertMatrixFails(new Expected(Rule.RESULT_NOT_PERMITTED, "result"),
                "PROCESSOR", "POSSIBLE_ISSUER", "91", "DECLINED",
                "DELIBERATE_DECLINE");
    }

    @Test
    void unsupportedDe39CodesAreRejectedBeforeStageEvaluation() {
        for (String de39 : List.of("99", "01", "90", "0", "000", "00 ", "AB")) {
            assertMatrixFails(new Expected(Rule.DE39_UNSUPPORTED, "de39"),
                    "ISSUER", "ISSUER_REPLIED", de39, "APPROVED", "APPROVED");
        }
    }

    @Test
    void rejectsResultsThatDoNotMatchTheDe39Row() {
        assertMatrixFails(new Expected(Rule.RESULT_NOT_PERMITTED, "result"),
                "ISSUER", "ISSUER_REPLIED", "00", "DECLINED",
                "DELIBERATE_DECLINE");
        assertMatrixFails(new Expected(Rule.RESULT_NOT_PERMITTED, "result"),
                "ISSUER", "ISSUER_REPLIED", "05", "APPROVED", "APPROVED");
        assertMatrixFails(new Expected(Rule.RESULT_NOT_PERMITTED, "result"),
                "PROCESSOR", "BEFORE_NETWORK", "30", "NOT_CONFIRMED", "UNCERTAIN");
        assertMatrixFails(new Expected(Rule.RESULT_NOT_PERMITTED, "result"),
                "PROCESSOR", "POSSIBLE_ISSUER", "91", "PROCESSOR_REJECTED",
                "UNAVAILABLE");
    }

    @Test
    void rejectsReasonsThatDoNotBelongToTheTuple() {
        assertMatrixFails(new Expected(Rule.REASON_NOT_PERMITTED, "reason"),
                "ISSUER", "ISSUER_REPLIED", "00", "APPROVED",
                "DELIBERATE_DECLINE");
        assertMatrixFails(new Expected(Rule.REASON_NOT_PERMITTED, "reason"),
                "ISSUER", "ISSUER_REPLIED", "05", "DECLINED", "FORMAT");
        assertMatrixFails(new Expected(Rule.REASON_NOT_PERMITTED, "reason"),
                "NETWORK", "BEFORE_ISSUER", "30", "PROCESSOR_REJECTED", "UNCERTAIN");
        assertMatrixFails(new Expected(Rule.REASON_NOT_PERMITTED, "reason"),
                "NETWORK", "BEFORE_ISSUER", "91", "PROCESSOR_REJECTED", "UNCERTAIN");
        assertMatrixFails(new Expected(Rule.REASON_NOT_PERMITTED, "reason"),
                "PROCESSOR", "POSSIBLE_ISSUER", "91", "NOT_CONFIRMED", "FORMAT");
        assertMatrixFails(new Expected(Rule.REASON_NOT_PERMITTED, "reason"),
                "NETWORK", "POSSIBLE_ISSUER", "91", "NOT_CONFIRMED", "DISALLOWED");
    }

    @Test
    void unknownActorStageOrEnumsAreNotSilentlyNormalized() {
        assertMatrixFails(new Expected(Rule.ACTOR_STAGE_NOT_PERMITTED, "stage"),
                "ACQUIRER", "ISSUER_REPLIED", "00", "APPROVED", "APPROVED");
        assertMatrixFails(new Expected(Rule.ACTOR_STAGE_NOT_PERMITTED, "stage"),
                "ISSUER", "AFTER_ISSUER", "00", "APPROVED", "APPROVED");
        assertMatrixFails(new Expected(Rule.RESULT_NOT_PERMITTED, "result"),
                "ISSUER", "ISSUER_REPLIED", "00", "APPROVE", "APPROVED");
        assertMatrixFails(new Expected(Rule.REASON_NOT_PERMITTED, "reason"),
                "ISSUER", "ISSUER_REPLIED", "00", "APPROVED", "TIMEOUT");
    }

    // --- Absence and later-layer separation -------------------------------------

    @Test
    void missingComponentsNeverYieldADecisionOrAManufacturedResponse() {
        assertMissingComponent(null, "ISSUER_REPLIED", "00", "APPROVED", "APPROVED",
                "actor");
        assertMissingComponent("ISSUER", null, "00", "APPROVED", "APPROVED", "stage");
        assertMissingComponent("ISSUER", "ISSUER_REPLIED", null, "APPROVED", "APPROVED",
                "de39");
        assertMissingComponent("ISSUER", "ISSUER_REPLIED", "00", null, "APPROVED",
                "result");
        assertMissingComponent("ISSUER", "ISSUER_REPLIED", "00", "APPROVED", null,
                "reason");

        assertMissingComponent("", "ISSUER_REPLIED", "00", "APPROVED", "APPROVED",
                "actor");
        assertMissingComponent("ISSUER", "", "00", "APPROVED", "APPROVED", "stage");
        assertMissingComponent("ISSUER", "ISSUER_REPLIED", "", "APPROVED", "APPROVED",
                "de39");
        assertMissingComponent("ISSUER", "ISSUER_REPLIED", "00", "", "APPROVED",
                "result");
        assertMissingComponent("ISSUER", "ISSUER_REPLIED", "00", "APPROVED", "",
                "reason");
    }

    @Test
    void matrixEligibilityDoesNotAuthenticateActorOrConfirmLaterBindings() {
        for (String id : BINDING_OR_TRUST_FIXTURES) {
            Iso8583Message message = Iso8583Scanner.scan(bodyOf(corpus().get(id)));
            Iso8583De48Response response = Iso8583De48ResponseParser.parse(message.field(48));

            Iso8583P4MatrixDecision decision = Iso8583P4MatrixValidator.validate(
                    response.value("actor"),
                    response.value("stage"),
                    message.field(39),
                    response.value("result"),
                    response.value("reason"));

            assertThat(decision).isNotNull();
        }
    }

    @Test
    void unsolicitedDe38PresenceIsNotPartOfTheMatrixDecision() {
        Iso8583Message message =
                Iso8583Scanner.scan(bodyOf(corpus().get("N-unsolicited-DE38")));
        Iso8583De48Response response = Iso8583De48ResponseParser.parse(message.field(48));

        assertThat(message.field(38)).isEqualTo("ZZ9999");
        assertThat(Iso8583P4MatrixValidator.validate(
                response.value("actor"),
                response.value("stage"),
                message.field(39),
                response.value("result"),
                response.value("reason")))
                .isEqualTo(new Iso8583P4MatrixDecision(
                        "ISSUER", "ISSUER_REPLIED", "00", "APPROVED", "APPROVED"));
    }

    // --- Helpers ---------------------------------------------------------------

    private static void assertMatrixFails(
            Expected expected,
            String actor,
            String stage,
            String de39,
            String result,
            String reason) {
        assertThatThrownBy(() -> Iso8583P4MatrixValidator.validate(
                actor, stage, de39, result, reason))
                .isInstanceOf(Iso8583P4MatrixException.class)
                .satisfies(thrown -> {
                    Iso8583P4MatrixException failure = (Iso8583P4MatrixException) thrown;
                    assertThat(failure.rule()).isEqualTo(expected.rule());
                    assertThat(failure.component()).isEqualTo(expected.component());
                });
    }

    private static void assertMatrixEligible(String id) {
        Iso8583Message message = Iso8583Scanner.scan(bodyOf(corpus().get(id)));
        Iso8583De48Response response = Iso8583De48ResponseParser.parse(message.field(48));

        Iso8583P4MatrixDecision decision = Iso8583P4MatrixValidator.validate(
                response.value("actor"),
                response.value("stage"),
                message.field(39),
                response.value("result"),
                response.value("reason"));

        assertThat(decision.actor()).isEqualTo(response.value("actor"));
        assertThat(decision.stage()).isEqualTo(response.value("stage"));
        assertThat(decision.de39()).isEqualTo(message.field(39));
        assertThat(decision.result()).isEqualTo(response.value("result"));
        assertThat(decision.reason()).isEqualTo(response.value("reason"));
    }

    private static void assertMissingComponent(
            String actor,
            String stage,
            String de39,
            String result,
            String reason,
            String component) {
        assertThatThrownBy(() -> Iso8583P4MatrixValidator.validate(
                actor, stage, de39, result, reason))
                .isInstanceOf(Iso8583P4MatrixException.class)
                .satisfies(thrown -> {
                    Iso8583P4MatrixException failure = (Iso8583P4MatrixException) thrown;
                    assertThat(failure.rule()).isEqualTo(Rule.MISSING_COMPONENT);
                    assertThat(failure.component()).isEqualTo(component);
                });
    }

    private static String reply(
            String actor, String stage, String result, String reason) {
        return RESPONSE_TEMPLATE.formatted(actor, stage, result, reason);
    }

    private static boolean isStructurallyAcceptedResponse(JsonNode vector) {
        try {
            return Iso8583Scanner.scan(bodyOf(vector)).mti().equals("0110");
        } catch (RuntimeException notStructurallyAccepted) {
            return false;
        }
    }

    private static Stream<JsonNode> responseFixtures() {
        return corpus().values().stream()
                .filter(Iso8583P4MatrixValidatorTest::isStructurallyAcceptedResponse);
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
