package com.acquiring.simulator.protocol.iso8583;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.RecordComponent;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Drives the ordered request-validation pipeline with the approved packed
 * corpus and a few composition-only first-failure cases.
 *
 * <p>The corpus is the immutable reviewed vector file. Every {@code 0100}
 * fixture is rebuilt from its authoritative {@code hex} octets and evaluated
 * end to end through scanner → canonical DE48 schema → domain → freshness →
 * 826-byte budget. The expected value is the vector's approved
 * {@code expectedFirstFailure}, or {@code ACCEPT} when it has none. Responses
 * and later layers (P4, binding, attestation, registry, duplicate/conflict,
 * claim and dispatch) are intentionally outside this REQUEST pipeline.</p>
 *
 * <p>The focused cases do not restate the individual layer rules. They only
 * prove that a request invalid at several layers still yields one deterministic
 * first failure in the approved order, and that replay/late modes do not apply
 * the new-exchange checks.</p>
 */
class Iso8583RequestValidationPipelineTest {

    private static final Path PACKED =
            Path.of("docs/ai-sdlc/work-items/WI-ACQ-OBS-001/vectors/packed-vectors.json");
    private static final Path MAPPING =
            Path.of("target", "p2a8-request-first-failure.tsv");
    private static final ObjectMapper JSON = new ObjectMapper();

    private static final int REQUEST_CORPUS_SIZE = 43;
    private static final int ACCEPTED_REQUESTS = 9;

    private static final List<String> PER_VECTOR_MAPPING =
            java.util.Collections.synchronizedList(new ArrayList<>());

    private static final Instant DEFAULT_RECEIPT = Instant.parse("2026-11-18T12:00:00Z");

    // --- Approved request corpus ----------------------------------------------

    static Stream<Arguments> requestFixtures() {
        return requestVectors().stream().map(vector -> Arguments.of(
                vector.get("id").asText(),
                expectedLabel(vector),
                body(vector),
                Instant.parse(vector.get("receivedAt").asText())));
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @MethodSource("requestFixtures")
    void matchesApprovedRequestFirstFailureOracle(
            String id, String expected, String body, Instant receivedAt) {
        String observed = observe(body, receivedAt, Iso8583AdmissionMode.NEW_FIRST_DISPATCH);

        assertThat(observed).as("first failure for approved fixture %s", id)
                .isEqualTo(expected);
        PER_VECTOR_MAPPING.add(id + "\t" + expected + "\t" + observed);
    }

    @Test
    void requestCorpusPartitionsIntoNineAcceptsAndThirtyFourNegatives() {
        List<JsonNode> requests = requestVectors();
        long accepted = requests.stream()
                .filter(vector -> "ACCEPT".equals(vector.get("expected").asText()))
                .count();

        assertThat(requests).as("approved 0100 request fixtures")
                .hasSize(REQUEST_CORPUS_SIZE);
        assertThat(accepted).as("approved ACCEPT request fixtures")
                .isEqualTo(ACCEPTED_REQUESTS);
        assertThat(REQUEST_CORPUS_SIZE - accepted).as("ordered negative request fixtures")
                .isEqualTo(34);
    }

    // --- First-failure order on multiply-invalid requests ----------------------

    @Test
    void schemaFailurePrecedesStaleFreshness() {
        String multiplyInvalid = stale(bodyOf("N-unknown-key"));

        assertThat(observe(multiplyInvalid, DEFAULT_RECEIPT,
                Iso8583AdmissionMode.NEW_FIRST_DISPATCH))
                .isEqualTo("SCHEMA_CANONICAL");
    }

    @Test
    void schemaFailurePrecedesExact999Capacity() {
        String multiplyInvalid = unknownRiskAt999(bodyOf("B-DE48-exact-999"));

        assertThat(observe(multiplyInvalid, DEFAULT_RECEIPT,
                Iso8583AdmissionMode.NEW_FIRST_DISPATCH))
                .isEqualTo("SCHEMA_CANONICAL");
    }

    @Test
    void domainCurrencyFailurePrecedesStaleFreshness() {
        String multiplyInvalid = nonUsd(bodyOf("D-stale"));

        assertThat(observe(multiplyInvalid, DEFAULT_RECEIPT,
                Iso8583AdmissionMode.NEW_FIRST_DISPATCH))
                .isEqualTo("DOMAIN_CURRENCY");
    }

    @Test
    void domainCurrencyFailurePrecedesOverBudgetCapacity() {
        String multiplyInvalid = nonUsd(bodyOf("CAP-request-827"));

        assertThat(observe(multiplyInvalid, DEFAULT_RECEIPT,
                Iso8583AdmissionMode.NEW_FIRST_DISPATCH))
                .isEqualTo("DOMAIN_CURRENCY");
    }

    @Test
    void freshnessFailurePrecedesOverBudgetCapacity() {
        String multiplyInvalid = stale(bodyOf("CAP-request-827"));

        assertThat(observe(multiplyInvalid, DEFAULT_RECEIPT,
                Iso8583AdmissionMode.NEW_FIRST_DISPATCH))
                .isEqualTo("ADMISSION_FRESHNESS");
    }

    @Test
    void firstFailureIsDeterministicAcrossRepeatedEvaluations() {
        String multiplyInvalid = nonUsd(stale(bodyOf("CAP-request-827")));

        for (int attempt = 0; attempt < 5; attempt++) {
            assertThat(observe(multiplyInvalid, DEFAULT_RECEIPT,
                    Iso8583AdmissionMode.NEW_FIRST_DISPATCH))
                    .as("attempt %s", attempt)
                    .isEqualTo("DOMAIN_CURRENCY");
        }
    }

    // --- Trusted mode and envelope boundary ------------------------------------

    @Test
    void replayAndLateDoNotApplyNewExchangeChecks() {
        Iso8583RequestValidation replay = Iso8583RequestValidationPipeline.evaluate(
                bodyOf("D-stale"), null,
                Iso8583AdmissionMode.AUTHORIZED_HISTORICAL_REPLAY);
        assertThat(replay.freshness()).isEqualTo(Iso8583FreshnessDecision.NOT_APPLICABLE);
        assertThat(replay.exchangeBudget())
                .isEqualTo(Iso8583ExchangeBudgetDecision.NOT_APPLICABLE);

        Iso8583RequestValidation late = Iso8583RequestValidationPipeline.evaluate(
                bodyOf("B-DE48-exact-999"), null,
                Iso8583AdmissionMode.LATE_EVIDENCE);
        assertThat(late.freshness()).isEqualTo(Iso8583FreshnessDecision.NOT_APPLICABLE);
        assertThat(late.exchangeBudget())
                .isEqualTo(Iso8583ExchangeBudgetDecision.NOT_APPLICABLE);
    }

    @Test
    void envelopeExposesValidatedStructureAndDomainWithoutAnAdmissionFlag() {
        Iso8583RequestValidation validation = Iso8583RequestValidationPipeline.evaluate(
                bodyOf("V01-approved-request"), DEFAULT_RECEIPT,
                Iso8583AdmissionMode.NEW_FIRST_DISPATCH);

        assertThat(validation.mode()).isEqualTo(Iso8583AdmissionMode.NEW_FIRST_DISPATCH);
        assertThat(validation.message().mti()).isEqualTo("0100");
        assertThat(validation.extension().value("sent"))
                .isEqualTo(validation.domain().sent());
        assertThat(validation.de48Bytes())
                .isEqualTo(validation.message().field(48).length());
        assertThat(validation.freshness()).isEqualTo(Iso8583FreshnessDecision.FRESH);

        List<String> components = Stream.of(Iso8583RequestValidation.class.getRecordComponents())
                .map(RecordComponent::getName)
                .toList();
        assertThat(components)
                .doesNotContain("admitted", "accepted", "authorized", "authenticated");
    }

    @Test
    void responseMtiIsRejectedAtTheRequestBoundary() {
        assertThat(observe(bodyOf("V02-approved-response"), DEFAULT_RECEIPT,
                Iso8583AdmissionMode.NEW_FIRST_DISPATCH))
                .isEqualTo("PARSER_MTI");
    }

    // --- Per-vector actual mapping artifact -----------------------------------

    @AfterAll
    static void writePerVectorMapping() throws IOException {
        List<String> lines = new ArrayList<>();
        lines.add("fixture\texpected\tobserved\tmatch");
        PER_VECTOR_MAPPING.stream()
                .sorted()
                .forEach(row -> {
                    String[] parts = row.split("\t", -1);
                    lines.add(row + "\t" + parts[1].equals(parts[2]));
                });
        Files.createDirectories(MAPPING.getParent());
        Files.write(MAPPING, lines);
    }

    // --- Helpers ---------------------------------------------------------------

    private static String observe(
            String body, Instant receivedAt, Iso8583AdmissionMode mode) {
        try {
            Iso8583RequestValidation validation =
                    Iso8583RequestValidationPipeline.evaluate(body, receivedAt, mode);
            assertThat(validation.mode()).isEqualTo(mode);
            assertThat(validation.message().mti()).isEqualTo("0100");
            assertThat(validation.domain().sent())
                    .isEqualTo(validation.extension().value("sent"));
            return "ACCEPT";
        } catch (Iso8583RequestValidationException failure) {
            return failure.failureLabel();
        }
    }

    private static String expectedLabel(JsonNode vector) {
        return vector.hasNonNull("expectedFirstFailure")
                ? vector.get("expectedFirstFailure").asText()
                : vector.get("expected").asText();
    }

    private static List<JsonNode> requestVectors() {
        return StreamSupport.stream(corpus().get("vectors").spliterator(), false)
                .filter(vector -> vector.get("ascii").asText().startsWith("0100"))
                .toList();
    }

    private static JsonNode corpus() {
        try {
            return JSON.readTree(PACKED.toFile());
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read approved packed vectors", e);
        }
    }

    private static String bodyOf(String fixtureId) {
        Map<String, JsonNode> byId = new LinkedHashMap<>();
        for (JsonNode vector : corpus().get("vectors")) {
            byId.put(vector.get("id").asText(), vector);
        }
        JsonNode vector = byId.get(fixtureId);
        assertThat(vector).as("approved fixture %s", fixtureId).isNotNull();
        return body(vector);
    }

    private static String body(JsonNode vector) {
        return new String(
                HexFormat.of().parseHex(vector.get("hex").asText()),
                StandardCharsets.ISO_8859_1);
    }

    /**
     * Move the fixed DE7 and the canonical {@code sent} together to 301 seconds
     * before the corpus receipt time. Lengths are unchanged, so no length prefix
     * or body offset moves.
     */
    private static String stale(String body) {
        assertThat(body).contains("sent=20261118120000");
        assertThat(body.substring(38, 48)).isEqualTo("1118120000");
        String withSent = body.replace(
                "sent=20261118120000", "sent=20261118115459");
        return withSent.substring(0, 38) + "1118115459" + withSent.substring(48);
    }

    private static String nonUsd(String body) {
        assertThat(body).endsWith("840");
        return body.substring(0, body.length() - 3) + "978";
    }

    private static String unknownRiskAt999(String body) {
        assertThat(body).contains(";risk=");
        return body.replace(";risk=", ";rixk=");
    }
}
