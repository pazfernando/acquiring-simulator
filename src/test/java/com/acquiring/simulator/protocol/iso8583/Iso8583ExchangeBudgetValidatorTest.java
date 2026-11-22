package com.acquiring.simulator.protocol.iso8583;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import org.junit.jupiter.api.Test;

/**
 * Deterministic tests for the pure NEW-exchange admission budget.
 *
 * <p>The bound is derived in production from the approved P4 alternatives and
 * the canonical response keys, then pinned here at exactly 173 reserved bytes
 * and 826 admitted request bytes. Approved packed fixtures supply the actual
 * request and response octets: the 999-byte structural case, the 826/827
 * boundary, the seven over-capacity requests and the worst-case 999-byte
 * response built on the 826-byte request prefix.</p>
 */
class Iso8583ExchangeBudgetValidatorTest {

    private static final Path PACKED =
            Path.of("docs/ai-sdlc/work-items/WI-ACQ-OBS-001/vectors/packed-vectors.json");
    private static final ObjectMapper JSON = new ObjectMapper();

    /** The approved worst suffix, evidenced by {@code CAP-response-3}. */
    private static final String WORST_RESPONSE_SUFFIX =
            ";actor=PROCESSOR;stage=BEFORE_NETWORK;result=PROCESSOR_REJECTED;"
                    + "reason=DISALLOWED;evidence="
                    + "E".repeat(Iso8583ExchangeBudgetValidator.EVIDENCE_ID_MAX_BYTES)
                    + ";at=20261118120001";

    @Test
    void derivesTheReserveAndAdmissionBoundFromTheApprovedMatrix() {
        assertThat(Iso8583ExchangeBudgetValidator.STRUCTURAL_DE48_MAX_BYTES).isEqualTo(999);
        assertThat(Iso8583ExchangeBudgetValidator.RESPONSE_TAIL_KEY_OVERHEAD_BYTES)
                .isEqualTo(44);
        assertThat(Iso8583ExchangeBudgetValidator.LONGEST_SUPPORTED_RESPONSE_METADATA_BYTES)
                .isEqualTo(51);
        assertThat(Iso8583ExchangeBudgetValidator.EVIDENCE_ID_MAX_BYTES).isEqualTo(64);
        assertThat(Iso8583ExchangeBudgetValidator.AT_TIMESTAMP_BYTES).isEqualTo(14);

        assertThat(WORST_RESPONSE_SUFFIX).hasSize(173);
        assertThat(Iso8583ExchangeBudgetValidator.RESPONSE_SUFFIX_RESERVE_BYTES).isEqualTo(173);
        assertThat(Iso8583ExchangeBudgetValidator.NEW_REQUEST_DE48_MAX_BYTES).isEqualTo(826);
    }

    @Test
    void rejectsApprovedExactDe48AtNineNineNineFromItsActualPackedBytes() throws IOException {
        JsonNode vector = require("B-DE48-exact-999");
        assertThat(vector.get("expected").asText()).isEqualTo("ADMISSION_RESPONSE_CAPACITY");
        assertThat(vector.get("expectedFirstFailure").asText())
                .isEqualTo(Iso8583ExchangeBudgetValidator.FAILURE_RULE);

        String body = bodyOf(vector);
        Iso8583Message scanned = Iso8583Scanner.scan(body);
        int de48Bytes = scanned.field(48).length();
        assertThat(de48Bytes).isEqualTo(999);

        Iso8583ExchangeBudgetDecision decision = evaluateNew(de48Bytes);
        assertThat(decision).isEqualTo(Iso8583ExchangeBudgetDecision.REJECTED);
        assertThat(decision.failureRule())
                .isEqualTo("ADMISSION_RESPONSE_CAPACITY");

        // The structural packer/scanner keep the 999-byte limit; only NEW
        // exchange admission rejects this structurally valid request.
        assertThat(Iso8583Packer.pack(scanned.mti(), scanned.fields())).isEqualTo(body);
    }

    @Test
    void acceptsEightTwentySixAndRejectsEightTwentySevenAtTheDerivedBoundary()
            throws IOException {
        JsonNode accepted = require("CAP-request-826");
        JsonNode rejected = require("CAP-request-827");
        assertThat(accepted.get("expected").asText()).isEqualTo("ACCEPT");
        assertThat(rejected.get("expected").asText()).isEqualTo("ADMISSION_RESPONSE_CAPACITY");
        assertThat(rejected.get("expectedFirstFailure").asText())
                .isEqualTo(Iso8583ExchangeBudgetValidator.FAILURE_RULE);

        Iso8583Message acceptedMessage = Iso8583Scanner.scan(bodyOf(accepted));
        Iso8583Message rejectedMessage = Iso8583Scanner.scan(bodyOf(rejected));
        assertThat(acceptedMessage.field(48)).hasSize(826);
        assertThat(rejectedMessage.field(48)).hasSize(827);

        assertThat(evaluateNew(826)).isEqualTo(Iso8583ExchangeBudgetDecision.ACCEPTED);
        assertThat(evaluateNew(827)).isEqualTo(Iso8583ExchangeBudgetDecision.REJECTED);

        assertThat(bodyOf(accepted)).hasSize(accepted.get("length").asInt());
        assertThat(bodyOf(rejected)).hasSize(rejected.get("length").asInt());
    }

    @Test
    void rejectsEveryOverCapacityRequestWithoutDroppingPriorOrExtensionKeys()
            throws IOException {
        for (int i = 1; i <= 7; i++) {
            JsonNode vector = require("CAP-over-request-" + i);
            assertThat(vector.get("expected").asText())
                    .as("CAP-over-request-%d verdict", i)
                    .isEqualTo("ADMISSION_RESPONSE_CAPACITY");

            String de48 = Iso8583Scanner.scan(bodyOf(vector)).field(48);
            assertThat(de48).as("CAP-over-request-%d DE48 octets", i).hasSize(827);

            Iso8583De48Request request = Iso8583De48RequestParser.parse(de48);
            assertThat(request.values()).as("echoed prefix is not trimmed").hasSize(17);
            assertThat(request.has("prior")).as("prior is not dropped to fit").isTrue();
            assertThat(request.value("prior"))
                    .isEqualTo(vector.get("extension").get("prior").asText());

            assertThat(evaluateNew(de48.length()))
                    .as("CAP-over-request-%d budget", i)
                    .isEqualTo(Iso8583ExchangeBudgetDecision.REJECTED);
        }
    }

    @Test
    void keepsTheWorstResponseForTheAdmittedRequestAtNineNineNineAndPacksIt()
            throws IOException {
        Iso8583Message request =
                Iso8583Scanner.scan(bodyOf(require("CAP-request-826")));
        String requestDe48 = request.field(48);
        assertThat(requestDe48).hasSize(826);

        String responseDe48 = requestDe48 + WORST_RESPONSE_SUFFIX;
        assertThat(responseDe48).hasSize(
                Iso8583ExchangeBudgetValidator.STRUCTURAL_DE48_MAX_BYTES);

        Iso8583De48Response response = Iso8583De48ResponseParser.parse(responseDe48);
        assertThat(response.values()).hasSize(23);
        assertThat(response.value("evidence")).hasSize(64);

        // The approved CAP-response-3 is exactly this worst case: the 826-byte
        // request prefix plus the 173-byte suffix, and it remains representable.
        String approvedResponseBody = bodyOf(require("CAP-response-3"));
        Iso8583Message approvedResponse = Iso8583Scanner.scan(approvedResponseBody);
        assertThat(approvedResponse.field(48)).isEqualTo(responseDe48);
        assertThat(Iso8583Packer.pack(approvedResponse.mti(), approvedResponse.fields()))
                .isEqualTo(approvedResponseBody);
    }

    @Test
    void doesNotReEvaluateAuthorizedReplayOrLateEvidence() {
        for (Iso8583AdmissionMode mode : new Iso8583AdmissionMode[]{
                Iso8583AdmissionMode.AUTHORIZED_HISTORICAL_REPLAY,
                Iso8583AdmissionMode.LATE_EVIDENCE}) {
            assertThat(evaluate(827, mode))
                    .as("%s over-budget value is not re-evaluated", mode)
                    .isEqualTo(Iso8583ExchangeBudgetDecision.NOT_APPLICABLE);
            assertThat(evaluate(999, mode))
                    .as("%s structural maximum is not re-encoded", mode)
                    .isEqualTo(Iso8583ExchangeBudgetDecision.NOT_APPLICABLE);
            assertThat(evaluate(-1, mode))
                    .as("%s does not read a fresh request length", mode)
                    .isEqualTo(Iso8583ExchangeBudgetDecision.NOT_APPLICABLE);
            assertThat(Iso8583ExchangeBudgetDecision.NOT_APPLICABLE.failureRule()).isNull();
        }
    }

    @Test
    void requiresATrustedModeAndStructurallyAcceptedLengths() {
        assertThatThrownBy(() -> Iso8583ExchangeBudgetValidator.evaluate(
                826, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("server");

        for (int impossible : List.of(-1, 0, 1000)) {
            assertThatThrownBy(() -> evaluateNew(impossible))
                    .as("structural length %d", impossible)
                    .isInstanceOf(IllegalArgumentException.class);
        }

        assertThat(evaluateNew(1)).isEqualTo(Iso8583ExchangeBudgetDecision.ACCEPTED);
        assertThat(evaluateNew(826)).isEqualTo(Iso8583ExchangeBudgetDecision.ACCEPTED);
        assertThat(evaluateNew(827)).isEqualTo(Iso8583ExchangeBudgetDecision.REJECTED);
        assertThat(evaluateNew(999)).isEqualTo(Iso8583ExchangeBudgetDecision.REJECTED);
    }

    // --- helpers ---------------------------------------------------------------

    private static Iso8583ExchangeBudgetDecision evaluateNew(int requestDe48Bytes) {
        return evaluate(requestDe48Bytes, Iso8583AdmissionMode.NEW_FIRST_DISPATCH);
    }

    private static Iso8583ExchangeBudgetDecision evaluate(
            int requestDe48Bytes, Iso8583AdmissionMode mode) {
        return Iso8583ExchangeBudgetValidator.evaluate(requestDe48Bytes, mode);
    }

    private static String bodyOf(JsonNode vector) {
        return new String(
                HexFormat.of().parseHex(vector.get("hex").asText()),
                StandardCharsets.ISO_8859_1);
    }

    private static JsonNode require(String id) {
        JsonNode node = packedById().get(id);
        assertThat(node).as("approved fixture %s", id).isNotNull();
        return node;
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
