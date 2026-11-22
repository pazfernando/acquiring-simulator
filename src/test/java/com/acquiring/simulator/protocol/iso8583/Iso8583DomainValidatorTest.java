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
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

/**
 * Pure domain-profile oracle for the amount, currency, profile-code and
 * Gregorian {@code sent}/DE7 slice.
 *
 * <p>Covers exactly the written scope: DE3/DE22/DE25 profile codes, DE4
 * {@code 100..999999}, DE49 {@code 840}, strict Gregorian {@code sent}
 * including leap days/year changes and invalid dates/times, and DE7 as the
 * original {@code sent} projection. It intentionally does not cover registry,
 * mandate, freshness, 826 admission, attestation, binding or a financial
 * outcome.</p>
 */
class Iso8583DomainValidatorTest {

    private static final Iso8583Message REQUEST_BASE = base("V01-approved-request");
    private static final Iso8583Message RESPONSE_BASE = base("V02-approved-response");

    private static final String SENT = "20261118120000";
    private static final String DE7 = "1118120000";

    @Test
    void acceptsTheInclusiveAmountBoundaries() {
        Iso8583DomainDecision min = Iso8583DomainValidator.validateRequest(
                withAmount(REQUEST_BASE, "000000000100"), request(SENT));
        Iso8583DomainDecision max = Iso8583DomainValidator.validateRequest(
                withAmount(REQUEST_BASE, "000000999999"), request(SENT));

        assertThat(min.amountMinor()).isEqualTo(100L);
        assertThat(max.amountMinor()).isEqualTo(999_999L);
        assertThat(min.currency()).isEqualTo("840");
        assertThat(min.sent()).isEqualTo(SENT);
        assertThat(min.de7()).isEqualTo(DE7);
    }

    @Test
    void rejectsAmountsOutsideTheApprovedRange() {
        assertRequestFails(Rule.AMOUNT_OUT_OF_RANGE, "de4",
                withAmount(REQUEST_BASE, "000000000099"), SENT);
        assertRequestFails(Rule.AMOUNT_OUT_OF_RANGE, "de4",
                withAmount(REQUEST_BASE, "000001000000"), SENT);
    }

    @Test
    void rejectsEveryCurrencyOtherThanUsd() {
        assertRequestFails(Rule.CURRENCY_UNSUPPORTED, "de49",
                with(REQUEST_BASE, 49, "978"), SENT);
        assertRequestFails(Rule.CURRENCY_UNSUPPORTED, "de49",
                with(REQUEST_BASE, 49, "000"), SENT);
        assertRequestFails(Rule.CURRENCY_UNSUPPORTED, "de49",
                with(REQUEST_BASE, 49, "999"), SENT);
    }

    @Test
    void acceptsBothApprovedEntryModesAndRejectsUnapprovedProfileCodes() {
        Iso8583DomainDecision contactless = Iso8583DomainValidator.validateRequest(
                with(REQUEST_BASE, 22, "071"), request(SENT));
        assertThat(contactless.amountMinor()).isEqualTo(1000L);

        assertRequestFails(Rule.PROFILE_CODE_UNSUPPORTED, "de3",
                with(REQUEST_BASE, 3, "000001"), SENT);
        assertRequestFails(Rule.PROFILE_CODE_UNSUPPORTED, "de22",
                with(REQUEST_BASE, 22, "050"), SENT);
        assertRequestFails(Rule.PROFILE_CODE_UNSUPPORTED, "de25",
                with(REQUEST_BASE, 25, "01"), SENT);
    }

    @Test
    void acceptsLeapDaysAndYearChangesWithoutNormalization() {
        assertThat(Iso8583DomainValidator.validateRequest(
                with(withDe7(REQUEST_BASE, "0229120000"), 4, "000000001000"),
                request("20280229120000")).sent()).isEqualTo("20280229120000");

        assertThat(Iso8583DomainValidator.validateRequest(
                with(withDe7(REQUEST_BASE, "1231235959"), 4, "000000001000"),
                request("20261231235959")).sent()).isEqualTo("20261231235959");

        assertThat(Iso8583DomainValidator.validateRequest(
                with(withDe7(REQUEST_BASE, "0101000001"), 4, "000000001000"),
                request("20270101000001")).sent()).isEqualTo("20270101000001");

        // 2000 is divisible by 400, so it is a leap year; 1900 is not.
        assertThat(Iso8583DomainValidator.validateRequest(
                with(withDe7(REQUEST_BASE, "0229120000"), 4, "000000001000"),
                request("20000229120000")).sent()).isEqualTo("20000229120000");
    }

    @Test
    void rejectsInvalidGregorianDatesAndTimesDeterministically() {
        assertInvalidGregorian("20270229120000", "0229120000");
        assertInvalidGregorian("20261318120000", "1318120000");
        assertInvalidGregorian("20260431120000", "0431120000");
        assertInvalidGregorian("20261118240000", "1118240000");
        assertInvalidGregorian("20261118126000", "1118126000");
        assertInvalidGregorian("20261118120060", "1118120060");
        assertInvalidGregorian("19000229120000", "0229120000");
        assertInvalidGregorian("00000101120000", "0101120000");
    }

    @Test
    void rejectsDe7ThatMatchesOnlyTheResponseAtTime() {
        // sent 12:00:00, DE7 12:00:01, at 12:00:01: DE7 must follow sent.
        Iso8583Message response = with(withDe7(RESPONSE_BASE, "1118120001"), 4, "000000001000");
        assertResponseFails(Rule.DE7_MISMATCH, "de7", response, SENT);
    }

    @Test
    void doesNotValidateTheResponseAtAvailabilityTime() {
        Iso8583Message response = with(withDe7(RESPONSE_BASE, DE7), 4, "000000001000");
        Iso8583DomainDecision decision = Iso8583DomainValidator.validateResponse(
                response, response(SENT, "20261318246060"));

        assertThat(decision.sent()).isEqualTo(SENT);
        assertThat(decision.de7()).isEqualTo(DE7);
    }

    @Test
    void amountFailurePrecedesDateFailureAndProfileFailurePrecedesAmount() {
        Iso8583Message badAmountAndDate =
                with(withDe7(REQUEST_BASE, "1318120000"), 4, "000000000099");
        assertRequestFails(Rule.AMOUNT_OUT_OF_RANGE, "de4",
                badAmountAndDate, "20261318120000");

        Iso8583Message badProfileAmountAndDate =
                with(withDe7(REQUEST_BASE, "1318120000"), 3, "000001");
        badProfileAmountAndDate = with(badProfileAmountAndDate, 4, "000000000099");
        assertRequestFails(Rule.PROFILE_CODE_UNSUPPORTED, "de3",
                badProfileAmountAndDate, "20261318120000");
    }

    // --- helpers ---------------------------------------------------------------

    private static void assertInvalidGregorian(String sent, String de7) {
        Iso8583Message message = with(withDe7(REQUEST_BASE, de7), 4, "000000001000");
        assertRequestFails(Rule.INVALID_GREGORIAN_DATE, "sent", message, sent);
    }

    private static void assertRequestFails(
            Rule rule, String component, Iso8583Message message, String sent) {
        assertThatThrownBy(() -> Iso8583DomainValidator.validateRequest(message, request(sent)))
                .isInstanceOf(Iso8583DomainException.class)
                .satisfies(thrown -> {
                    Iso8583DomainException failure = (Iso8583DomainException) thrown;
                    assertThat(failure.rule()).isEqualTo(rule);
                    assertThat(failure.component()).isEqualTo(component);
                    assertThat(failure.getMessage()).doesNotContain("=", ";");
                });
    }

    private static void assertResponseFails(
            Rule rule, String component, Iso8583Message message, String sent) {
        assertThatThrownBy(() -> Iso8583DomainValidator.validateResponse(
                message, response(sent, "20261118120001")))
                .isInstanceOf(Iso8583DomainException.class)
                .satisfies(thrown -> {
                    Iso8583DomainException failure = (Iso8583DomainException) thrown;
                    assertThat(failure.rule()).isEqualTo(rule);
                    assertThat(failure.component()).isEqualTo(component);
                    assertThat(failure.getMessage()).doesNotContain("=", ";");
                });
    }

    private static Iso8583De48Request request(String sent) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("sent", sent);
        return new Iso8583De48Request(values);
    }

    private static Iso8583De48Response response(String sent, String at) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("sent", sent);
        values.put("at", at);
        return new Iso8583De48Response(values);
    }

    private static Iso8583Message withAmount(Iso8583Message base, String amount) {
        return with(base, 4, amount);
    }

    private static Iso8583Message withDe7(Iso8583Message base, String de7) {
        return with(base, 7, de7);
    }

    private static Iso8583Message with(Iso8583Message base, int dataElement, String value) {
        Map<Integer, String> fields = new TreeMap<>(base.fields());
        fields.put(dataElement, value);
        return new Iso8583Message(
                base.mti(), base.bitmap(), base.headerLength(), base.bodyLength(), fields);
    }

    private static Iso8583Message base(String vectorId) {
        return Iso8583Scanner.scan(bodyOf(vector(vectorId)));
    }

    private static JsonNode vector(String vectorId) {
        try {
            JsonNode root = new ObjectMapper().readTree(
                    new File("docs/ai-sdlc/work-items/WI-ACQ-OBS-001/vectors/packed-vectors.json"));
            for (JsonNode node : root.get("vectors")) {
                if (node.get("id").asText().equals(vectorId)) {
                    return node;
                }
            }
            throw new IllegalStateException("missing approved vector: " + vectorId);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read approved corpus", e);
        }
    }

    private static String bodyOf(JsonNode vector) {
        return new String(
                HexFormat.of().parseHex(vector.get("hex").asText()),
                StandardCharsets.ISO_8859_1);
    }
}
