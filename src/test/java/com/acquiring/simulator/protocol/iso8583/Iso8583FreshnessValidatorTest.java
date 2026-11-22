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
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * Deterministic tests for the pure NEW-request freshness window.
 *
 * <p>The approved stale case is read from its actual {@code D-stale} hex body:
 * scanner → canonical DE48 request schema → {@code sent} → freshness. Boundary
 * tests pin the inclusive 300-second edge and the first outside value. The
 * cross-year cases use the full Gregorian {@code sent} instant against a server
 * clock that has already rolled into the next year. Replay/late modes must not
 * read or re-evaluate the submitted timestamp.</p>
 */
class Iso8583FreshnessValidatorTest {

    private static final Path PACKED =
            Path.of("docs/ai-sdlc/work-items/WI-ACQ-OBS-001/vectors/packed-vectors.json");
    private static final ObjectMapper JSON = new ObjectMapper();

    private static final Instant RECEIPT = Instant.parse("2026-11-18T12:00:00Z");

    @Test
    void rejectsApprovedDStaleFromItsActualHex() throws IOException {
        JsonNode vector = require("D-stale");
        assertThat(vector.get("expectedFirstFailure").asText())
                .isEqualTo(Iso8583FreshnessValidator.FAILURE_RULE);
        assertThat(Instant.parse(vector.get("receivedAt").asText())).isEqualTo(RECEIPT);

        Iso8583Message message = Iso8583Scanner.scan(bodyOf(vector));
        Iso8583De48Request request = Iso8583De48RequestParser.parse(message.field(48));
        String sent = request.value("sent");
        assertThat(sent).isEqualTo(vector.get("extension").get("sent").asText());
        assertThat(sent).isEqualTo("20261118115459");

        Iso8583FreshnessDecision decision = Iso8583FreshnessValidator.evaluate(
                sent, RECEIPT, Iso8583AdmissionMode.NEW_FIRST_DISPATCH);

        assertThat(decision).isEqualTo(Iso8583FreshnessDecision.STALE);
        assertThat(decision.failureRule()).isEqualTo("ADMISSION_FRESHNESS");
    }

    @Test
    void acceptsExactlyThreeHundredSecondsAndRejectsJustOutside() {
        assertThat(evaluateNew("20261118115500", RECEIPT))
                .isEqualTo(Iso8583FreshnessDecision.FRESH);

        assertThat(evaluateNew("20261118115459", RECEIPT))
                .isEqualTo(Iso8583FreshnessDecision.STALE);
        assertThat(evaluateNew("20261118115500", Instant.parse("2026-11-18T12:00:00.001Z")))
                .isEqualTo(Iso8583FreshnessDecision.STALE);
        assertThat(Iso8583FreshnessDecision.FRESH.failureRule()).isNull();
        assertThat(Iso8583FreshnessDecision.NOT_APPLICABLE.failureRule()).isNull();
    }

    @Test
    void usesTheFullGregorianInstantAcrossTheYearBoundary() {
        Instant newYearServerClock = Instant.parse("2027-01-01T00:00:00Z");

        // Profile examples: 2026-12-31T23:59:59Z and 2027-01-01T00:00:01Z.
        assertThat(evaluateNew("20261231235959", newYearServerClock))
                .isEqualTo(Iso8583FreshnessDecision.FRESH);
        assertThat(evaluateNew("20270101000001", newYearServerClock))
                .isEqualTo(Iso8583FreshnessDecision.FRESH);

        // 301 seconds before the new-year receipt is stale, with no year wrap.
        assertThat(evaluateNew("20261231235459", newYearServerClock))
                .isEqualTo(Iso8583FreshnessDecision.STALE);
    }

    @Test
    void doesNotApplyNewFreshnessToAuthorizedReplayOrLateEvidence() {
        for (Iso8583AdmissionMode mode : new Iso8583AdmissionMode[]{
                Iso8583AdmissionMode.AUTHORIZED_HISTORICAL_REPLAY,
                Iso8583AdmissionMode.LATE_EVIDENCE}) {
            assertThat(Iso8583FreshnessValidator.evaluate("20261118115459", RECEIPT, mode))
                    .as("%s stale value is not re-evaluated", mode)
                    .isEqualTo(Iso8583FreshnessDecision.NOT_APPLICABLE);
            assertThat(Iso8583FreshnessValidator.evaluate(null, null, mode))
                    .as("%s does not read the submitted timestamp", mode)
                    .isEqualTo(Iso8583FreshnessDecision.NOT_APPLICABLE);
        }
    }

    @Test
    void requiresATrustedModeAndNewDispatchInputs() {
        assertThatThrownBy(() -> Iso8583FreshnessValidator.evaluate(
                "20261118115500", RECEIPT, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("server");

        assertThatThrownBy(() -> Iso8583FreshnessValidator.evaluate(
                null, RECEIPT, Iso8583AdmissionMode.NEW_FIRST_DISPATCH))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("sent");

        assertThatThrownBy(() -> Iso8583FreshnessValidator.evaluate(
                "20261118115500", null, Iso8583AdmissionMode.NEW_FIRST_DISPATCH))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("receipt");
    }

    // --- helpers ---------------------------------------------------------------

    private static Iso8583FreshnessDecision evaluateNew(String sent, Instant receivedAt) {
        return Iso8583FreshnessValidator.evaluate(
                sent, receivedAt, Iso8583AdmissionMode.NEW_FIRST_DISPATCH);
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
