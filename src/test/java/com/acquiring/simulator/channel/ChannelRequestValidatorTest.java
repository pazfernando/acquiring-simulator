package com.acquiring.simulator.channel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.acquiring.simulator.domain.model.CvmIndicator;
import com.acquiring.simulator.domain.model.EntryMode;
import com.acquiring.simulator.domain.model.RiskIndicator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Deterministic tests for the pure C1 channel input validator.
 *
 * <p>The eight approved {@code channel-vectors.json} amount cases are executed
 * from their actual JSON bodies and must reproduce the recorded oracle label:
 * three {@code ACCEPT} values with their exact minor units and 12-digit DE4
 * projection, one below-floor {@code DOMAIN_AMOUNT}, three
 * {@code CHANNEL_SCHEMA_AMOUNT} forms and one {@code CHANNEL_TYPE_AMOUNT} JSON
 * number. Composition tests cover the closed schema, typing, unknown/duplicate
 * keys, references, literals, the 4096-byte boundary and safe-first-failure
 * behavior. No test re-implements ISO validation; the DE4 column is only the
 * zero-padded projection of the integer this slice derives.</p>
 */
class ChannelRequestValidatorTest {

    private static final Path CHANNEL_VECTORS =
            Path.of("docs/ai-sdlc/work-items/WI-ACQ-OBS-001/vectors/channel-vectors.json");
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void approvedChannelAmountVectorInventoryIsEightCases() throws IOException {
        List<String> ids = new ArrayList<>();
        channelCases().forEach(vector -> ids.add(vector.get("id").asText()));
        assertThat(ids).containsExactly("J1", "J2", "J3", "J4", "J5", "J6", "J7", "J8");
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @MethodSource("amountVectorCases")
    void approvedAmountVectorReproducesItsOracle(String id, String expected, String body)
            throws IOException {
        if ("ACCEPT".equals(expected)) {
            JsonNode vector = requireVector(id);
            ChannelAuthorizationRequest request = ChannelRequestValidator.validate(body);
            long expectedMinor = vector.get("amountMinor").asLong();
            String channelAmount = vector.get("body").get("amount").asText();
            assertThat(request.amountMinor()).isEqualTo(expectedMinor);
            // Exact integer arithmetic, never a float or a rounded value.
            assertThat(request.amount())
                    .isEqualByComparingTo(new BigDecimal(channelAmount))
                    .hasScaleOf(2);
            assertThat(request.amount().toPlainString()).isEqualTo(channelAmount);
            assertThat("%012d".formatted(request.amountMinor()))
                    .isEqualTo(vector.get("DE4").asText());
            assertThat(vector.get("noRounding").asBoolean()).isTrue();
            return;
        }
        ChannelRequestValidationException failure = failureOf(body);
        assertThat(failure.rule()).isEqualTo(expected);
        assertThat(failure.errorCode()).isEqualTo(ChannelErrorCode.INVALID_INPUT);
        assertThat(failure.field()).isEqualTo("amount");
    }

    @Test
    void acceptsTheExactDeclaredSchemaAndAppliesTheRiskDefault() throws IOException {
        ChannelAuthorizationRequest request = ChannelRequestValidator.validate(j1Body());
        assertThat(request.contractVersion()).isEqualTo("1");
        assertThat(request.operation()).isEqualTo("A");
        assertThat(request.correlationId()).isEqualTo("CORR-001");
        assertThat(request.acquirerRef()).isEqualTo("ACQ-A");
        assertThat(request.merchantRef()).isEqualTo("MERCHANT-A");
        assertThat(request.terminalRef()).isEqualTo("TERMINAL-A");
        assertThat(request.paymentRef()).isEqualTo("PAY-001");
        assertThat(request.credentialFixtureRef()).isEqualTo("SIM-APP-001");
        assertThat(request.amountMinor()).isEqualTo(100L);
        assertThat(request.currency()).isEqualTo("USD");
        assertThat(request.entryMode()).isEqualTo(EntryMode.SIMULATED_CONTACT);
        assertThat(request.cvm()).isEqualTo(CvmIndicator.SIMULATED_VERIFIED);
        assertThat(request.cryptogramPresent()).isTrue();
        assertThat(request.risk()).isEqualTo(RiskIndicator.PASS);
        assertThat(request.priorAuthorizationId()).isNull();
    }

    @Test
    void acceptsExplicitRiskAndPriorReferenceAndBothApprovedLiteralVariants() throws IOException {
        String body = j1Body()
                .replace("\"simulated-contact\"", "\"simulated-contactless\"")
                .replace("\"simulated-verified\"", "\"simulated-not-required\"")
                .replace("\"credentialFixtureRef\":\"SIM-APP-001\"",
                        "\"credentialFixtureRef\":\"SIM-APP-001\",\"risk\":\"reject\","
                                + "\"priorAuthorizationId\":\"AUTH-0001\"");
        ChannelAuthorizationRequest request = ChannelRequestValidator.validate(body);
        assertThat(request.entryMode()).isEqualTo(EntryMode.SIMULATED_CONTACTLESS);
        assertThat(request.cvm()).isEqualTo(CvmIndicator.SIMULATED_NOT_REQUIRED);
        assertThat(request.risk()).isEqualTo(RiskIndicator.REJECT);
        assertThat(request.priorAuthorizationId()).isEqualTo("AUTH-0001");
    }

    @Test
    void rejectsUnknownCallerFieldsWithoutEchoingThem() throws IOException {
        String base = j1Body();
        for (String extra : List.of(
                "\"decision\":\"APPROVED\"",
                "\"route\":\"DOMESTIC\"",
                "\"actor\":\"ISSUER\"",
                "\"generation\":7",
                "\"pan\":\"4111111111111111\"")) {
            ChannelRequestValidationException failure = failureOf(withExtraField(base, extra));
            assertThat(failure.rule()).isEqualTo("UNKNOWN_FIELD");
            assertThat(failure.errorCode()).isEqualTo(ChannelErrorCode.INVALID_INPUT);
            assertThat(failure.field()).isNull();
            assertThat(failure.getMessage()).doesNotContain("APPROVED", "DOMESTIC", "ISSUER",
                    "4111111111111111", "decision", "route", "actor", "generation", "pan");
        }
    }

    @Test
    void rejectsDuplicateKeysDeterministically() throws IOException {
        String body = j1Body().replace("\"currency\":\"USD\"",
                "\"currency\":\"USD\",\"currency\":\"USD\"");
        ChannelRequestValidationException failure = failureOf(body);
        assertThat(failure.rule()).isEqualTo("DUPLICATE_FIELD");
        assertThat(failure.field()).isEqualTo("currency");
    }

    @Test
    void rejectsMissingNullAndWronglyTypedFields() throws IOException {
        String base = j1Body();
        assertThat(failureOf(base.replace("\"currency\":\"USD\",", "")).rule())
                .isEqualTo("MISSING_FIELD");
        assertThat(failureOf(base.replace("\"currency\":\"USD\"", "\"currency\":null")).rule())
                .isEqualTo("NULL_FIELD");
        assertThat(failureOf(base.replace("\"currency\":\"USD\"", "\"currency\":840")).rule())
                .isEqualTo("WRONG_TYPE");
        // No string-to-boolean coercion and no number-to-string coercion.
        assertThat(failureOf(base.replace("\"cryptogramPresent\":true",
                "\"cryptogramPresent\":\"true\"")).field()).isEqualTo("cryptogramPresent");
        assertThat(failureOf(base.replace("\"correlationId\":\"CORR-001\"",
                "\"correlationId\":42")).rule()).isEqualTo("WRONG_TYPE");
    }

    @Test
    void rejectsUnapprovedLiterals() throws IOException {
        String base = j1Body();
        assertLiteralFailure(base.replace("\"contractVersion\":\"1\"",
                "\"contractVersion\":\"2\""), "contractVersion");
        assertLiteralFailure(base.replace("\"operation\":\"A\"",
                "\"operation\":\"B\""), "operation");
        assertLiteralFailure(base.replace("\"currency\":\"USD\"",
                "\"currency\":\"MXN\""), "currency");
        assertLiteralFailure(base.replace("\"simulated-contact\"",
                "\"contact\""), "entryMode");
        assertLiteralFailure(base.replace("\"simulated-verified\"",
                "\"verified\""), "cvm");
        assertLiteralFailure(withExtraField(base, "\"risk\":\"maybe\""), "risk");
    }

    @Test
    void rejectsReferenceAlphabetBoundsAndNonAsciiValues() throws IOException {
        String base = j1Body();
        assertThat(failureOf(base.replace("\"PAY-001\"", "\"PAY 001\""), "paymentRef").rule())
                .isEqualTo("INVALID_REFERENCE");
        assertThat(failureOf(base.replace("\"ACQ-A\"", "\"ACQ/A\""), "acquirerRef").rule())
                .isEqualTo("INVALID_REFERENCE");
        assertThat(failureOf(base.replace("\"MERCHANT-A\"",
                "\"" + "M".repeat(129) + "\""), "merchantRef").rule())
                .isEqualTo("INVALID_REFERENCE");
        assertThat(failureOf(base.replace("\"CORR-001\"",
                "\"" + "C".repeat(65) + "\""), "correlationId").rule())
                .isEqualTo("INVALID_REFERENCE");
        // Raw non-ASCII bytes fail before JSON decoding/parsing and name no field.
        assertThat(failureOf(base.replace("\"PAY-001\"",
                "\"PAY-001\u00e9\"")).rule()).isEqualTo("NON_ASCII_BODY");
        // A JSON \\u escape stays ASCII on the wire but decodes to a non-approved
        // reference character, so the reference alphabet rejects it.
        assertThat(failureOf(base.replace("\"PAY-001\"",
                "\"PAY-001\\u00e9\""), "paymentRef").rule()).isEqualTo("INVALID_REFERENCE");
    }

    @Test
    void enforcesTheApprovedBodySizeBoundaryBeforeParsing() throws IOException {
        String base = j1Body();
        String padded = base + " ".repeat(ChannelRequestValidator.MAX_BODY_BYTES - base.length());
        assertThat(padded).hasSize(ChannelRequestValidator.MAX_BODY_BYTES);
        assertThat(ChannelRequestValidator.validate(padded).amountMinor()).isEqualTo(100L);

        String tooLarge = base + " ".repeat(ChannelRequestValidator.MAX_BODY_BYTES + 1 - base.length());
        assertThat(tooLarge).hasSize(ChannelRequestValidator.MAX_BODY_BYTES + 1);
        ChannelRequestValidationException failure = failureOf(tooLarge);
        assertThat(failure.errorCode()).isEqualTo(ChannelErrorCode.BODY_TOO_LARGE);
        assertThat(failure.rule()).isEqualTo("BODY_TOO_LARGE");

        // A malformed 4096-byte body is a schema failure, not a size failure.
        assertThat(failureOf("A".repeat(ChannelRequestValidator.MAX_BODY_BYTES)).rule())
                .isEqualTo("INVALID_JSON");
        assertThat(failureOf((byte[]) null).rule()).isEqualTo("EMPTY_BODY");
    }

    @Test
    void rejectsMalformedJsonTrailingContentAndNonObjectRoots() throws IOException {
        assertThat(failureOf("{").rule()).isEqualTo("INVALID_JSON");
        assertThat(failureOf("[]").rule()).isEqualTo("INVALID_JSON");
        assertThat(failureOf(j1Body() + "{}").rule()).isEqualTo("TRAILING_CONTENT");
    }

    @Test
    void optionalPriorReferenceIsBoundedAndNullIsNotACoercion() throws IOException {
        String base = j1Body();
        String maximum = "P".repeat(ChannelRequestValidator.PRIOR_AUTHORIZATION_ID_MAX_LENGTH);
        String accepted = withExtraField(base, "\"priorAuthorizationId\":\"" + maximum + "\"");
        assertThat(ChannelRequestValidator.validate(accepted).priorAuthorizationId())
                .isEqualTo(maximum);

        String oversize = withExtraField(base,
                "\"priorAuthorizationId\":\"" + "P".repeat(65) + "\"");
        assertThat(failureOf(oversize, "priorAuthorizationId").rule())
                .isEqualTo("INVALID_REFERENCE");
        assertThat(failureOf(withExtraField(base, "\"priorAuthorizationId\":null"),
                "priorAuthorizationId").rule()).isEqualTo("NULL_FIELD");
    }

    @Test
    void amountConversionIsExactAndUsesNoFloatingPoint() {
        assertThat(ChannelAmount.toMinorUnits("1.00")).isEqualTo(100L);
        assertThat(ChannelAmount.toMinorUnits("10.01")).isEqualTo(1001L);
        assertThat(ChannelAmount.toMinorUnits("9999.99")).isEqualTo(999_999L);
        assertThat(ChannelAmount.toAmount(1001L)).isEqualByComparingTo(new BigDecimal("10.01"));
        assertThat(ChannelAmount.toAmount(1001L).scale()).isEqualTo(2);
        assertThat(ChannelAmount.toAmount(1001L).toPlainString()).isEqualTo("10.01");

        for (String schemaInvalid : List.of(
                "1", "1.0", ".99", "1.000", "10000.00", "01.00", "00.99", "1,00",
                "-1.00", "+1.00", "1.00 ", " 1.00", "1.0e2", "\u0661.00")) {
            assertAmountRule(schemaInvalid, ChannelAmount.CHANNEL_SCHEMA_AMOUNT);
        }
        for (String belowFloor : List.of("0.99", "0.00")) {
            assertAmountRule(belowFloor, ChannelAmount.DOMAIN_AMOUNT);
        }
    }

    private static void assertAmountRule(String amount, String expected) {
        assertThatThrownBy(() -> ChannelAmount.toMinorUnits(amount))
                .isInstanceOf(ChannelRequestValidationException.class)
                .satisfies(thrown -> {
                    ChannelRequestValidationException failure =
                            (ChannelRequestValidationException) thrown;
                    assertThat(failure.rule()).isEqualTo(expected);
                    assertThat(failure.errorCode()).isEqualTo(ChannelErrorCode.INVALID_INPUT);
                    assertThat(failure.field()).isEqualTo("amount");
                    assertThat(failure.getMessage()).doesNotContain(amount);
                });
    }

    private static void assertLiteralFailure(String body, String field) {
        ChannelRequestValidationException failure = failureOf(body);
        assertThat(failure.rule()).isEqualTo("INVALID_LITERAL");
        assertThat(failure.field()).isEqualTo(field);
    }

    private static ChannelRequestValidationException failureOf(String body, String field) {
        ChannelRequestValidationException failure = failureOf(body);
        assertThat(failure.field()).isEqualTo(field);
        return failure;
    }

    private static ChannelRequestValidationException failureOf(String body) {
        return failureOf(body.getBytes(StandardCharsets.UTF_8));
    }

    private static ChannelRequestValidationException failureOf(byte[] body) {
        try {
            ChannelRequestValidator.validate(body);
        } catch (ChannelRequestValidationException e) {
            return e;
        }
        throw new AssertionError("expected a channel validation failure");
    }

    private static String withExtraField(String body, String extra) {
        int close = body.lastIndexOf('}');
        return body.substring(0, close) + "," + extra + "}";
    }

    private static String j1Body() throws IOException {
        return JSON.writeValueAsString(requireVector("J1").get("body"));
    }

    private static JsonNode requireVector(String id) throws IOException {
        for (JsonNode vector : channelCases()) {
            if (id.equals(vector.get("id").asText())) {
                return vector;
            }
        }
        throw new IllegalArgumentException("missing channel vector " + id);
    }

    private static Stream<Arguments> amountVectorCases() {
        try {
            List<Arguments> arguments = new ArrayList<>();
            for (JsonNode vector : channelCases()) {
                arguments.add(Arguments.of(
                        vector.get("id").asText(),
                        vector.get("expected").asText(),
                        JSON.writeValueAsString(vector.get("body"))));
            }
            return arguments.stream();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static List<JsonNode> channelCases() throws IOException {
        List<JsonNode> cases = new ArrayList<>();
        JSON.readTree(CHANNEL_VECTORS.toFile()).get("cases").forEach(cases::add);
        return cases;
    }
}
