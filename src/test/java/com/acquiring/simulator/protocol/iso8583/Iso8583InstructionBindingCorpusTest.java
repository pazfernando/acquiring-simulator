package com.acquiring.simulator.protocol.iso8583;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.acquiring.simulator.protocol.iso8583.Iso8583InstructionBindingException.Rule;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Drives the instruction-binding layer with the approved byte corpus.
 *
 * <p>Each case rebuilds the actual {@code 0110} body from its authoritative
 * {@code hex} octets, then runs the real pipeline: structural scanner → canonical
 * DE48 response schema → P4 actor/stage/DE39/result/reason matrix → complete
 * original-instruction echo binding. The persisted original instruction and the
 * trusted DE38 assertion come from the explicit {@code evidence-fixtures.json}
 * entry, never from the response and never from an auth flag.</p>
 *
 * <p>The original-wire hash is the caller-provided value attested by the
 * fixture; the test separately rehashes the fixture's exact original
 * {@code 0100} hex octets to prove the value was not produced by re-encoding.
 * Only equality/format projections are asserted. No amount/currency/calendar
 * validity, registry or mandate check, attestation signature, full P5 identity
 * or financial admission is claimed.</p>
 */
class Iso8583InstructionBindingCorpusTest {

    private static final Path PACKED =
            Path.of("docs/ai-sdlc/work-items/WI-ACQ-OBS-001/vectors/packed-vectors.json");
    private static final Path EVIDENCE =
            Path.of("docs/ai-sdlc/work-items/WI-ACQ-OBS-001/vectors/evidence-fixtures.json");
    private static final ObjectMapper JSON = new ObjectMapper();

    private record Negative(String fixtureId, String expectedLayer, Rule rule, String component) {
    }

    private static Stream<Negative> bindingNegatives() {
        return Stream.of(
                new Negative("N-wrong-correlation", "BINDING_INSTRUCTION",
                        Rule.INSTRUCTION_FIELD_MISMATCH, "cid"),
                new Negative("N-wrong-context", "BINDING_INSTRUCTION",
                        Rule.INSTRUCTION_FIELD_MISMATCH, "acq"),
                new Negative("N-mismatch-amount", "BINDING_INSTRUCTION",
                        Rule.INSTRUCTION_FIELD_MISMATCH, "de4"),
                new Negative("N-unsolicited-DE38", "BINDING_AUTH_REFERENCE",
                        Rule.AUTHORIZATION_REFERENCE_UNSOLICITED, "de38"),
                new Negative("N-missing-DE38-asserted", "BINDING_AUTH_REFERENCE",
                        Rule.AUTHORIZATION_REFERENCE_MISSING, "de38"));
    }

    @ParameterizedTest
    @MethodSource("bindingNegatives")
    void rejectsApprovedNegativeFixtureAtTheBindingLayer(Negative negative) throws IOException {
        JsonNode vector = require(packedById(), negative.fixtureId());
        JsonNode evidence = require(evidenceById(), "EF:" + negative.fixtureId());
        assertThat(vector.get("expectedFirstFailure").asText())
                .isEqualTo(negative.expectedLayer());

        Iso8583OriginalInstruction original = originalOf(evidence);
        Iso8583TrustedEvidenceRecord trusted = trustedOf(evidence);
        Iso8583Message responseMessage = Iso8583Scanner.scan(bodyOf(vector));
        assertThat(responseMessage.mti()).isEqualTo("0110");

        Iso8583De48Response response =
                Iso8583De48ResponseParser.parse(responseMessage.field(48));
        isP4Eligible(response, responseMessage);

        assertThatThrownBy(() -> Iso8583InstructionBindingValidator.validate(
                original, trusted, responseMessage, response))
                .isInstanceOf(Iso8583InstructionBindingException.class)
                .satisfies(thrown -> {
                    Iso8583InstructionBindingException failure =
                            (Iso8583InstructionBindingException) thrown;
                    assertThat(failure.rule()).isEqualTo(negative.rule());
                    assertThat(failure.component()).isEqualTo(negative.component());
                });
    }

    @Test
    void bindsTheCompleteEchoForTheDe38ApprovalFixture() throws IOException {
        JsonNode vector = require(packedById(), "V10-actual-DE38");
        JsonNode evidence = require(evidenceById(), "EF:V10-actual-DE38");
        Iso8583Message responseMessage = Iso8583Scanner.scan(bodyOf(vector));
        Iso8583De48Response response =
                Iso8583De48ResponseParser.parse(responseMessage.field(48));

        Iso8583InstructionBindingDecision decision = bind(evidence, vector, responseMessage, response);

        assertThat(decision.authorizationId()).isEqualTo("AUTH-001");
        assertThat(decision.authorizationReference()).isEqualTo("AB1234");
    }

    @Test
    void bindsTheCompleteEchoWhenTheTrustedRecordAssertsNoDe38() throws IOException {
        JsonNode vector = require(packedById(), "V02-approved-response");
        JsonNode evidence = require(evidenceById(), "EF:V02-approved-response");
        Iso8583Message responseMessage = Iso8583Scanner.scan(bodyOf(vector));
        Iso8583De48Response response =
                Iso8583De48ResponseParser.parse(responseMessage.field(48));

        Iso8583InstructionBindingDecision decision = bind(evidence, vector, responseMessage, response);

        assertThat(decision.authorizationId()).isEqualTo("AUTH-001");
        assertThat(decision.hasAuthorizationReference()).isFalse();
    }

    // --- Pipeline helpers ------------------------------------------------------

    private static Iso8583InstructionBindingDecision bind(
            JsonNode evidence,
            JsonNode vector,
            Iso8583Message responseMessage,
            Iso8583De48Response response) throws IOException {
        Iso8583OriginalInstruction original = originalOf(evidence);
        Iso8583TrustedEvidenceRecord trusted = trustedOf(evidence);
        assertThat(sha256Hex(vector.get("hex").asText()))
                .as("the binding ran on the exact approved response octets")
                .isEqualTo(evidence.get("attestedFields").get("responseSha256").asText());
        isP4Eligible(response, responseMessage);
        return Iso8583InstructionBindingValidator.validate(
                original, trusted, responseMessage, response);
    }

    private static void isP4Eligible(
            Iso8583De48Response response, Iso8583Message responseMessage) {
        Iso8583P4MatrixValidator.validate(
                response.value("actor"),
                response.value("stage"),
                responseMessage.field(39),
                response.value("result"),
                response.value("reason"));
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

    private static String bodyOf(JsonNode vector) {
        return new String(
                HexFormat.of().parseHex(vector.get("hex").asText()),
                StandardCharsets.ISO_8859_1);
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

    private static Map<String, JsonNode> packedById() {
        return byId(PACKED, "vectors");
    }

    private static Map<String, JsonNode> evidenceById() {
        return byId(EVIDENCE, "fixtures");
    }

    private static Map<String, JsonNode> byId(Path path, String arrayField) {
        try {
            JsonNode root = JSON.readTree(new File(path.toString()));
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
