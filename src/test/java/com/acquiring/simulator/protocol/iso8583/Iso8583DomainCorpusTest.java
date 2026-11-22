package com.acquiring.simulator.protocol.iso8583;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.acquiring.simulator.protocol.iso8583.Iso8583DomainException.Rule;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Drives the domain-profile slice with the approved byte corpus.
 *
 * <p>Each case rebuilds the exact {@code hex} body and runs the real pipeline:
 * structural scanner → canonical DE48 schema → domain profile → (for responses)
 * P4 matrix. Binding is the next downstream layer and is not reached once the
 * domain phase fails, so an amount/currency/date/DE7 failure is the first
 * failure for these fixtures. No registry, freshness, 826 admission,
 * attestation or financial outcome is claimed.</p>
 */
class Iso8583DomainCorpusTest {

    private static final Path PACKED =
            Path.of("docs/ai-sdlc/work-items/WI-ACQ-OBS-001/vectors/packed-vectors.json");
    private static final ObjectMapper JSON = new ObjectMapper();

    private record Negative(String fixtureId, String expectedLabel, Rule rule, String component) {
    }

    private static Stream<Negative> requestNegatives() {
        return Stream.of(
                new Negative("B-below-min", "DOMAIN_AMOUNT",
                        Rule.AMOUNT_OUT_OF_RANGE, "de4"),
                new Negative("B-over-max", "DOMAIN_AMOUNT",
                        Rule.AMOUNT_OUT_OF_RANGE, "de4"),
                new Negative("B-non-usd", "DOMAIN_CURRENCY",
                        Rule.CURRENCY_UNSUPPORTED, "de49"),
                new Negative("D-leap-invalid", "DOMAIN_DATE",
                        Rule.INVALID_GREGORIAN_DATE, "sent"),
                new Negative("D-bad-month", "DOMAIN_DATE",
                        Rule.INVALID_GREGORIAN_DATE, "sent"),
                new Negative("D-DE7-mismatch", "DOMAIN_DE7",
                        Rule.DE7_MISMATCH, "de7"));
    }

    private static Stream<Negative> responseNegatives() {
        return Stream.of(
                new Negative("N-response-DE7", "DOMAIN_DE7",
                        Rule.DE7_MISMATCH, "de7"),
                new Negative("N-response-invalid-date", "DOMAIN_DATE",
                        Rule.INVALID_GREGORIAN_DATE, "sent"));
    }

    private static Stream<String> requestPositives() {
        return Stream.of(
                "B-min-amount", "B-max-amount", "D-leap-valid", "Y-2026", "Y-2027");
    }

    @ParameterizedTest
    @MethodSource("requestNegatives")
    void rejectsApprovedRequestDomainNegative(Negative negative) throws IOException {
        JsonNode vector = require(negative.fixtureId());
        assertThat(vector.get("expectedFirstFailure").asText())
                .isEqualTo(negative.expectedLabel);

        Iso8583Message message = Iso8583Scanner.scan(bodyOf(vector));
        Iso8583De48Request request = Iso8583De48RequestParser.parse(message.field(48));

        assertThatThrownBy(() -> Iso8583DomainValidator.validateRequest(message, request))
                .isInstanceOf(Iso8583DomainException.class)
                .satisfies(thrown -> assertFailure(thrown, negative));
    }

    @ParameterizedTest
    @MethodSource("responseNegatives")
    void rejectsApprovedResponseDomainNegativeBeforeBinding(Negative negative) throws IOException {
        JsonNode vector = require(negative.fixtureId());
        assertThat(vector.get("expectedFirstFailure").asText())
                .isEqualTo(negative.expectedLabel);

        Iso8583Message message = Iso8583Scanner.scan(bodyOf(vector));
        Iso8583De48Response response = Iso8583De48ResponseParser.parse(message.field(48));
        assertP4Eligible(response, message);

        assertThatThrownBy(() -> Iso8583DomainValidator.validateResponse(message, response))
                .isInstanceOf(Iso8583DomainException.class)
                .satisfies(thrown -> assertFailure(thrown, negative));
    }

    @ParameterizedTest
    @MethodSource("requestPositives")
    void acceptsApprovedRequestDomainPositive(String fixtureId) throws IOException {
        JsonNode vector = require(fixtureId);
        assertThat(vector.get("expectedFirstFailure").isNull()
                || vector.get("expectedFirstFailure").isMissingNode()).isTrue();

        Iso8583Message message = Iso8583Scanner.scan(bodyOf(vector));
        Iso8583De48Request request = Iso8583De48RequestParser.parse(message.field(48));

        Iso8583DomainDecision decision = Iso8583DomainValidator.validateRequest(message, request);

        assertThat(decision.sent()).isEqualTo(request.value("sent"));
        assertThat(decision.de7()).isEqualTo(message.field(7));
        assertThat(decision.currency()).isEqualTo("840");
        assertThat(Long.parseLong(message.field(4))).isEqualTo(decision.amountMinor());
    }

    @Test
    void acceptsTheResponsePositiveWithItsLaterAtTime() throws IOException {
        JsonNode vector = require("V02-approved-response");
        Iso8583Message message = Iso8583Scanner.scan(bodyOf(vector));
        Iso8583De48Response response = Iso8583De48ResponseParser.parse(message.field(48));

        assertThat(response.value("at")).isNotEqualTo(response.value("sent"));
        Iso8583DomainDecision decision =
                Iso8583DomainValidator.validateResponse(message, response);

        assertThat(decision.sent()).isEqualTo("20261118120000");
        assertThat(decision.de7()).isEqualTo("1118120000");
    }

    // --- helpers ---------------------------------------------------------------

    private static void assertFailure(Throwable thrown, Negative negative) {
        Iso8583DomainException failure = (Iso8583DomainException) thrown;
        assertThat(failure.rule()).isEqualTo(negative.rule());
        assertThat(failure.component()).isEqualTo(negative.component());
        assertThat(failure.getMessage()).doesNotContain("=", ";");
    }

    private static void assertP4Eligible(
            Iso8583De48Response response, Iso8583Message message) {
        Iso8583P4MatrixValidator.validate(
                response.value("actor"),
                response.value("stage"),
                message.field(39),
                response.value("result"),
                response.value("reason"));
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
