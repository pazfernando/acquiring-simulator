package com.acquiring.simulator.protocol.iso8583;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.RecordComponent;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Drives the ordered response-validation pipeline with the approved packed
 * corpus.
 *
 * <p>Every covered {@code 0110} fixture is rebuilt from its authoritative
 * {@code hex} octets and evaluated end to end through scanner → canonical DE48
 * response schema → domain → P4 matrix → complete instruction/DE38 binding. The
 * persisted original instruction and trusted DE38 assertion come from the
 * explicit {@code evidence-fixtures.json} entry, never from the response and
 * never from a caller boolean.</p>
 *
 * <p>The wire corpus contains 41 responses. This pipeline covers 38 of them:
 * 22 {@code ACCEPT} and 16 ordered negatives (4 schema, 2 domain, 5 P4,
 * 5 binding). Three fixtures stay explicitly deferred and are <em>not</em>
 * executed: {@code N-forged-actor} and {@code N-unresolvable-evidence} need
 * attestation verification, and {@code N-conflicting-confirmation} needs
 * business-conflict/duplicate admission. Both are outside this slice, so the
 * suite never presents them as accepted.</p>
 *
 * <p>The focused cases do not restate the individual layer rules. They only
 * prove a deterministic first failure across several invalid layers and that
 * the trusted-record seam is required and is never rebuilt from the response.
 * No freshness window or 826-byte budget applies to a response.</p>
 */
class Iso8583ResponseValidationPipelineTest {

    private static final Path PACKED =
            Path.of("docs/ai-sdlc/work-items/WI-ACQ-OBS-001/vectors/packed-vectors.json");
    private static final Path EVIDENCE =
            Path.of("docs/ai-sdlc/work-items/WI-ACQ-OBS-001/vectors/evidence-fixtures.json");
    private static final Path MAPPING =
            Path.of("target", "p2a9-response-first-failure.tsv");
    private static final ObjectMapper JSON = new ObjectMapper();

    private static final int RESPONSE_CORPUS_SIZE = 41;
    private static final int COVERED_RESPONSES = 38;
    private static final int ACCEPTED_RESPONSES = 22;

    private static final Map<String, String> DEFERRED = Map.of(
            "N-forged-actor", "AUTH_ATTESTATION",
            "N-unresolvable-evidence", "AUTH_ATTESTATION",
            "N-conflicting-confirmation", "BUSINESS_CONFLICT_QUARANTINE");

    private static final List<String> PER_VECTOR_MAPPING =
            java.util.Collections.synchronizedList(new ArrayList<>());

    // --- Approved covered corpus ----------------------------------------------

    static Stream<Arguments> coveredResponseFixtures() {
        return coveredVectors().stream().map(vector -> Arguments.of(
                vector.get("id").asText(),
                expectedLabel(vector),
                vector));
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @MethodSource("coveredResponseFixtures")
    void matchesApprovedResponseFirstFailureOracle(
            String id, String expected, JsonNode vector) throws IOException {
        String observed = observe(vector, evidenceOf(id));

        assertThat(observed).as("first failure for approved fixture %s", id)
                .isEqualTo(expected);
        PER_VECTOR_MAPPING.add(id + "\t" + expected + "\t" + observed);
    }

    @Test
    void responseCorpusPartitionsIntoCoveredAndDeferredFixtures() {
        List<JsonNode> responses = responseVectors();
        long accepted = coveredVectors().stream()
                .filter(vector -> "ACCEPT".equals(vector.get("expected").asText()))
                .count();

        assertThat(responses).as("approved 0110 response fixtures")
                .hasSize(RESPONSE_CORPUS_SIZE);
        assertThat(coveredVectors()).as("covered response fixtures")
                .hasSize(COVERED_RESPONSES);
        assertThat(accepted).as("approved ACCEPT response fixtures")
                .isEqualTo(ACCEPTED_RESPONSES);
        assertThat(COVERED_RESPONSES - accepted).as("ordered negative responses")
                .isEqualTo(16);
        assertThat(coveredVectors().size() + DEFERRED.size())
                .isEqualTo(RESPONSE_CORPUS_SIZE);
    }

    @Test
    void attestationAndBusinessConflictFixturesStayExplicitlyDeferred() {
        assertThat(DEFERRED).hasSize(3);
        Set<String> coveredIds = coveredVectors().stream()
                .map(vector -> vector.get("id").asText())
                .collect(java.util.stream.Collectors.toSet());

        assertThat(coveredIds).doesNotContainAnyElementsOf(DEFERRED.keySet());
        for (Map.Entry<String, String> deferred : DEFERRED.entrySet()) {
            JsonNode vector = responseById().get(deferred.getKey());
            assertThat(vector).as("deferred fixture %s", deferred.getKey()).isNotNull();
            assertThat(vector.get("expectedFirstFailure").asText())
                    .as("declared later-phase oracle for %s", deferred.getKey())
                    .isEqualTo(deferred.getValue());
        }
        assertThat(responseById().keySet()).containsAll(DEFERRED.keySet());
    }

    // --- First-failure order on multiply-invalid responses --------------------

    @Test
    void schemaFailurePrecedesDomainAndLaterLayers() {
        String multiplyInvalid = bodyOf("N-response-DE7")
                .replace(";evidence=", ";evidense=");

        assertThat(observe(multiplyInvalid, "N-response-DE7"))
                .isEqualTo("SCHEMA_CANONICAL");
    }

    @Test
    void domainFailurePrecedesP4AndBinding() {
        // N-00-network is P4-invalid and its DE49 echo is also binding-invalid
        // once changed; the domain currency check still wins.
        String multiplyInvalid = nonUsd(bodyOf("N-00-network"));

        assertThat(observe(multiplyInvalid, "N-00-network"))
                .isEqualTo("DOMAIN_CURRENCY");
    }

    @Test
    void p4FailurePrecedesInstructionBinding() {
        // N-wrong-correlation already has a binding mismatch; an equally long
        // stage change makes the claimed P4 tuple invalid first.
        String multiplyInvalid = bodyOf("N-wrong-correlation")
                .replace("stage=ISSUER_REPLIED", "stage=BEFORE_NETWORK");

        assertThat(observe(multiplyInvalid, "N-wrong-correlation"))
                .isEqualTo("EVIDENCE_MATRIX");
    }

    @Test
    void instructionEchoFailurePrecedesAuthorizationReference() {
        // N-unsolicited-DE38 would fail the DE38 check; the instruction echo is
        // checked first and must win.
        String multiplyInvalid = bodyOf("N-unsolicited-DE38")
                .replace("cid=CORR-001", "cid=CORR-999");

        assertThat(observe(multiplyInvalid, "N-unsolicited-DE38"))
                .isEqualTo("BINDING_INSTRUCTION");
    }

    @Test
    void firstFailureIsDeterministicAcrossRepeatedEvaluations() {
        String multiplyInvalid = bodyOf("N-00-network")
                .replace("stage=ISSUER_REPLIED", "stage=BEFORE_NETWORK");
        multiplyInvalid = nonUsd(multiplyInvalid);

        for (int attempt = 0; attempt < 5; attempt++) {
            assertThat(observe(multiplyInvalid, "N-00-network"))
                    .as("attempt %s", attempt)
                    .isEqualTo("DOMAIN_CURRENCY");
        }
    }

    // --- Trusted-record seam and envelope boundary ----------------------------

    @Test
    void missingTrustedRecordFailsClosedBeforeAnyPhase() {
        JsonNode vector = require(packedById(), "V02-approved-response");
        JsonNode evidence = require(evidenceById(), "EF:V02-approved-response");
        String body = body(vector);

        assertTrustStage(() -> Iso8583ResponseValidationPipeline.evaluate(
                        body, null, trustedOf(evidence)),
                "TRUSTED_RECORD_REQUIRED");
        assertTrustStage(() -> Iso8583ResponseValidationPipeline.evaluate(
                        body, originalOf(evidence), null),
                "TRUSTED_RECORD_REQUIRED");
    }

    @Test
    void malformedTrustedRecordFailsClosed() {
        JsonNode evidence = require(evidenceById(), "EF:V02-approved-response");

        assertTrustStage(() -> Iso8583ResponseValidationPipeline.evaluate(
                        bodyOf("V02-approved-response"),
                        originalOf(evidence),
                        new Iso8583TrustedEvidenceRecord("not-a-hash", null)),
                "TRUSTED_RECORD_INVALID");
    }

    @Test
    void trustedHashIsNeverSubstitutedWithTheResponseHash() throws IOException {
        JsonNode vector = require(packedById(), "V02-approved-response");
        JsonNode evidence = require(evidenceById(), "EF:V02-approved-response");
        String originalHash = evidence.get("attestedFields").get("instructionSha256").asText();
        String responseHash = sha256Hex(vector.get("hex").asText());

        assertThat(responseHash)
                .as("the response hash is a different value from the original hash")
                .isNotEqualTo(originalHash);

        Iso8583ResponseValidationException failure = failureOf(
                bodyOf("V02-approved-response"),
                originalOf(evidence),
                new Iso8583TrustedEvidenceRecord(responseHash, null));

        assertThat(failure.stage())
                .isEqualTo(Iso8583ResponseValidationException.Stage.INSTRUCTION);
        assertThat(failure.failureLabel()).isEqualTo("BINDING_INSTRUCTION");
    }

    @Test
    void envelopeExposesValidatedStructureWithoutATrustFlag() throws IOException {
        JsonNode vector = require(packedById(), "V02-approved-response");
        JsonNode evidence = require(evidenceById(), "EF:V02-approved-response");
        Iso8583OriginalInstruction original = originalOf(evidence);

        Iso8583ResponseValidation validation = Iso8583ResponseValidationPipeline.evaluate(
                body(vector), original, trustedOf(evidence));

        assertThat(validation.message().mti()).isEqualTo("0110");
        assertThat(validation.extension().value("sent"))
                .isEqualTo(validation.domain().sent());
        assertThat(validation.p4().de39()).isEqualTo(validation.message().field(39));
        assertThat(validation.instructionBinding().authorizationId())
                .isEqualTo(original.authorizationId());

        List<String> components =
                Stream.of(Iso8583ResponseValidation.class.getRecordComponents())
                        .map(RecordComponent::getName)
                        .toList();
        assertThat(components)
                .containsExactly("message", "extension", "domain", "p4", "instructionBinding");
        assertThat(String.join(" ", components).toLowerCase())
                .doesNotContain("verified", "accepted", "authorized",
                        "authenticated", "admitted", "valid");
    }

    @Test
    void responsePipelineTakesNoReceiptInstantAndNoFreshnessOrBudget() {
        Parameter[] parameters = Stream.of(
                        Iso8583ResponseValidationPipeline.class.getDeclaredMethods())
                .filter(method -> method.getName().equals("evaluate"))
                .map(Method::getParameters)
                .findFirst()
                .orElseThrow();
        assertThat(Stream.of(parameters)
                .map(Parameter::getType)
                .map(Class::getSimpleName))
                .doesNotContain("Instant");

        List<String> components =
                Stream.of(Iso8583ResponseValidation.class.getRecordComponents())
                        .map(RecordComponent::getName)
                        .toList();
        assertThat(components).doesNotContain("freshness", "exchangeBudget", "receivedAt");
    }

    @Test
    void requestMtiIsRejectedAtTheResponseBoundary() {
        assertThat(observe(bodyOf("V01-approved-request"), "V02-approved-response"))
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

    private static String observe(JsonNode vector, JsonNode evidence) {
        return observe(body(vector), evidence);
    }

    private static String observe(String body, String evidenceFixtureId) {
        JsonNode evidence = require(evidenceById(), "EF:" + evidenceFixtureId);
        return observe(body, evidence);
    }

    private static String observe(String body, JsonNode evidence) {
        try {
            Iso8583ResponseValidation validation =
                    Iso8583ResponseValidationPipeline.evaluate(
                            body, originalOf(evidence), trustedOf(evidence));
            assertThat(validation.message().mti()).isEqualTo("0110");
            return "ACCEPT";
        } catch (Iso8583ResponseValidationException failure) {
            return failure.failureLabel();
        }
    }

    private static Iso8583ResponseValidationException failureOf(
            String body,
            Iso8583OriginalInstruction original,
            Iso8583TrustedEvidenceRecord trusted) {
        try {
            Iso8583ResponseValidationPipeline.evaluate(body, original, trusted);
        } catch (Iso8583ResponseValidationException failure) {
            return failure;
        }
        throw new AssertionError("expected a response-validation failure");
    }

    private static void assertTrustStage(
            org.assertj.core.api.ThrowableAssert.ThrowingCallable call,
            String expectedLabel) {
        org.assertj.core.api.Assertions.assertThatThrownBy(call)
                .isInstanceOf(Iso8583ResponseValidationException.class)
                .satisfies(thrown -> {
                    Iso8583ResponseValidationException failure =
                            (Iso8583ResponseValidationException) thrown;
                    assertThat(failure.stage())
                            .isEqualTo(Iso8583ResponseValidationException.Stage.TRUST);
                    assertThat(failure.failureLabel()).isEqualTo(expectedLabel);
                });
    }

    private static Iso8583OriginalInstruction originalOf(JsonNode evidence) {
        JsonNode fields = evidence.get("originalFields");
        JsonNode extension = evidence.get("originalExtension");
        String attestedHash = evidence.get("attestedFields").get("instructionSha256").asText();

        assertThat(sha256Hex(evidence.get("original0100Hex").asText()))
                .as("the caller-provided hash is the exact persisted original octets")
                .isEqualTo(attestedHash);

        Iso8583PhysicalInstruction physical = new Iso8583PhysicalInstruction(
                fields.get("3").asText(),
                Long.parseLong(fields.get("4").asText()),
                fields.get("7").asText(),
                fields.get("11").asText(),
                fields.get("22").asText(),
                fields.get("25").asText(),
                fields.get("32").asText(),
                fields.get("37").asText(),
                fields.get("41").asText(),
                fields.get("42").asText(),
                fields.get("49").asText());

        return new Iso8583OriginalInstruction(
                attestedHash,
                extension.get("aid").asText(),
                extension.get("tid").asText(),
                extension.get("cid").asText(),
                extension.get("att").asText(),
                extension.get("acq").asText(),
                extension.get("mid").asText(),
                extension.get("term").asText(),
                extension.get("pay").asText(),
                Integer.parseInt(extension.get("gen").asText()),
                extension.get("fixture").asText(),
                extension.get("cvm").asText(),
                extension.get("crypto").asText(),
                extension.get("risk").asText(),
                extension.get("sent").asText(),
                extension.has("prior") ? extension.get("prior").asText() : null,
                physical);
    }

    private static Iso8583TrustedEvidenceRecord trustedOf(JsonNode evidence) {
        JsonNode attested = evidence.get("attestedFields");
        JsonNode reference = attested.get("authorizationReference");
        return new Iso8583TrustedEvidenceRecord(
                attested.get("instructionSha256").asText(),
                reference == null || reference.isNull() ? null : reference.asText());
    }

    private static JsonNode evidenceOf(String fixtureId) {
        return require(evidenceById(), "EF:" + fixtureId);
    }

    private static String expectedLabel(JsonNode vector) {
        return vector.hasNonNull("expectedFirstFailure")
                ? vector.get("expectedFirstFailure").asText()
                : vector.get("expected").asText();
    }

    private static List<JsonNode> coveredVectors() {
        return responseVectors().stream()
                .filter(vector -> !DEFERRED.containsKey(vector.get("id").asText()))
                .toList();
    }

    private static List<JsonNode> responseVectors() {
        return StreamSupport.stream(corpus().get("vectors").spliterator(), false)
                .filter(vector -> vector.get("ascii").asText().startsWith("0110"))
                .toList();
    }

    private static JsonNode corpus() {
        try {
            return JSON.readTree(PACKED.toFile());
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read approved packed vectors", e);
        }
    }

    private static Map<String, JsonNode> responseById() {
        Map<String, JsonNode> byId = new LinkedHashMap<>();
        for (JsonNode vector : responseVectors()) {
            byId.put(vector.get("id").asText(), vector);
        }
        return byId;
    }

    private static Map<String, JsonNode> packedById() {
        return byId(PACKED, "vectors");
    }

    private static Map<String, JsonNode> evidenceById() {
        return byId(EVIDENCE, "fixtures");
    }

    private static String bodyOf(String fixtureId) {
        return body(require(packedById(), fixtureId));
    }

    private static String body(JsonNode vector) {
        return new String(
                HexFormat.of().parseHex(vector.get("hex").asText()),
                StandardCharsets.ISO_8859_1);
    }

    private static String nonUsd(String body) {
        assertThat(body).endsWith("840");
        return body.substring(0, body.length() - 3) + "978";
    }

    private static String sha256Hex(String hex) {
        try {
            byte[] octets = HexFormat.of().parseHex(hex);
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(octets));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is required", impossible);
        }
    }

    private static JsonNode require(Map<String, JsonNode> byId, String id) {
        JsonNode node = byId.get(id);
        assertThat(node).as("approved fixture %s", id).isNotNull();
        return node;
    }

    private static Map<String, JsonNode> byId(Path path, String arrayField) {
        try {
            JsonNode root = JSON.readTree(path.toFile());
            Map<String, JsonNode> byId = new LinkedHashMap<>();
            for (JsonNode node : root.get(arrayField)) {
                byId.put(node.get("id").asText(), node);
            }
            return byId;
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read approved corpus", e);
        }
    }
}
