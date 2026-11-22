package com.acquiring.simulator.protocol.iso8583;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.acquiring.simulator.protocol.iso8583.Iso8583FieldException.Rule;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.stream.Stream;

class Iso8583FieldReaderTest {

    /**
     * ASCII body octets 59..99 of the approved packed 0100 fixture in
     * docs/ai-sdlc/work-items/WI-ACQ-OBS-001/vectors/packed-vectors.json
     * (V01-approved-request): DE32 LL prefix+value, DE37, DE41, DE42 and the
     * first three octets of the DE48 LLL prefix. Copied byte-for-byte.
     */
    private static final String FIXTURE_DE32_TO_DE48_PREFIX =
            "011000000000001TERM0001MERCH0000000001188";

    // --- DE32: LL n1..11 -----------------------------------------------------

    @ParameterizedTest(name = "DE32 LL n accepts {0} ({1} digits)")
    @MethodSource("de32AcceptedAliases")
    void readsDe32LlNumericAtBothLengthBounds(String alias, int declaredDigits) {
        String encoded = "%02d".formatted(declaredDigits) + alias;

        Iso8583FieldReader reader = Iso8583FieldReader.over(encoded + "TAIL");

        assertThat(reader.readLlDigits(1, 11)).isEqualTo(alias);
        assertThat(reader.position()).isEqualTo(encoded.length());
        assertThat(reader.remaining()).isEqualTo(4);
    }

    static Stream<Arguments> de32AcceptedAliases() {
        return Stream.of(
                Arguments.of("1", 1),
                Arguments.of("12345678901", 11));
    }

    @ParameterizedTest(name = "DE32 rejects [{0}] as {1}")
    @MethodSource("de32RejectedBodies")
    void rejectsMalformedOrOverLimitDe32LlNumeric(String body, Rule expected) {
        assertFailsWith(expected, () -> Iso8583FieldReader.over(body).readLlDigits(1, 11));
    }

    static Stream<Arguments> de32RejectedBodies() {
        return Stream.of(
                // prefix declares 11 but only 10 value octets follow
                Arguments.of("111234567890", Rule.BODY_TOO_SHORT),
                // truncated length prefix
                Arguments.of("1", Rule.BODY_TOO_SHORT),
                // prefix 12 exceeds the DE32 maximum, even with 12 octets present
                Arguments.of("12123456789012", Rule.LENGTH_OUT_OF_RANGE),
                // zero is not a representable DE32 alias
                Arguments.of("00", Rule.LENGTH_OUT_OF_RANGE),
                // non-decimal length prefix
                Arguments.of("1A1", Rule.INVALID_LENGTH_PREFIX),
                // non-numeric value octet
                Arguments.of("01A", Rule.INVALID_NUMERIC),
                // Unicode digit is not the ASCII decimal alphabet
                Arguments.of("01\u0661", Rule.INVALID_NUMERIC));
    }

    // --- DE42: fixed 15 uppercase alphanumeric -------------------------------

    @Test
    void readsDe42AsExactlyFifteenAndProvesDe32PrefixIsNotPartOfDe42() {
        Iso8583FieldReader reader = Iso8583FieldReader.over(FIXTURE_DE32_TO_DE48_PREFIX);

        // DE32 "01" prefix + value "1"; a fixed-width DE32 misread (the
        // historical probe defect) would shift every following field.
        assertThat(reader.readLlDigits(1, 11)).isEqualTo("1");
        assertThat(reader.readFixedDigits(12)).isEqualTo("000000000001");
        assertThat(reader.readFixedUpperAlnum(8)).isEqualTo("TERM0001");

        assertThat(reader.readFixedUpperAlnum(15)).isEqualTo("MERCH0000000001");
        assertThat(reader.position()).isEqualTo(FIXTURE_DE32_TO_DE48_PREFIX.length() - 3);
        assertThat(FIXTURE_DE32_TO_DE48_PREFIX.substring(reader.position())).isEqualTo("188");
    }

    @ParameterizedTest(name = "DE42 rejects 15-octet value [{0}] as {1}")
    @MethodSource("de42RejectedValues")
    void rejectsNonUppercaseAlnumDe42Values(String value, Rule expected) {
        assertThat(value).hasSize(15);
        assertFailsWith(expected, () -> Iso8583FieldReader.over(value).readFixedUpperAlnum(15));
    }

    static Stream<Arguments> de42RejectedValues() {
        return Stream.of(
                Arguments.of("merch0000000001", Rule.INVALID_UPPER_ALNUM),
                Arguments.of("MERCH 000000001", Rule.INVALID_UPPER_ALNUM),
                Arguments.of("MERCH000000000\u0661", Rule.INVALID_UPPER_ALNUM));
    }

    @Test
    void rejectsDe42AtHistoricalFourteenOrSixteenWidthWithoutPadOrTruncate() {
        assertFailsWith(Rule.BODY_TOO_SHORT,
                () -> Iso8583FieldReader.over("MERCH000000000").readFixedUpperAlnum(15));

        Iso8583FieldReader sixteen = Iso8583FieldReader.over("MERCH0000000001X");
        assertThat(sixteen.readFixedUpperAlnum(15)).isEqualTo("MERCH0000000001");
        assertFailsWith(Rule.TRAILING_BYTES, sixteen::requireEnd);
    }

    // --- DE48: LLL printable ASCII 1..999 ------------------------------------

    @ParameterizedTest(name = "DE48 LLL printable accepts {0} octet(s)")
    @ValueSource(ints = {1, 999})
    void readsDe48LllPrintableAtBothLengthBounds(int length) {
        String value = "A".repeat(length);
        String encoded = "%03d".formatted(length) + value;

        Iso8583FieldReader reader = Iso8583FieldReader.over(encoded + "Z");

        assertThat(reader.readLllPrintableAscii(1, 999)).isEqualTo(value);
        assertThat(reader.position()).isEqualTo(encoded.length());
        assertThat(reader.remaining()).isEqualTo(1);
    }

    @ParameterizedTest(name = "DE48 rejects [{0}] as {1}")
    @MethodSource("de48RejectedBodies")
    void rejectsMalformedLengthOrNonPrintableDe48(String body, Rule expected) {
        assertFailsWith(expected, () -> Iso8583FieldReader.over(body).readLllPrintableAscii(1, 999));
    }

    static Stream<Arguments> de48RejectedBodies() {
        return Stream.of(
                // zero-length extension is not a canonical DE48 value
                Arguments.of("000", Rule.LENGTH_OUT_OF_RANGE),
                Arguments.of("99", Rule.BODY_TOO_SHORT),
                Arguments.of("1A0x", Rule.INVALID_LENGTH_PREFIX),
                Arguments.of("005abc", Rule.BODY_TOO_SHORT),
                Arguments.of("003a\u0007b", Rule.NON_PRINTABLE_ASCII),
                Arguments.of("003a\u007Fb", Rule.NON_PRINTABLE_ASCII),
                Arguments.of("003a\u00E9b", Rule.NON_PRINTABLE_ASCII));
    }

    // --- Cursor bounds and exact end-of-body ---------------------------------

    @Test
    void cursorHonoursStartOffsetAndRequiresExactEndOfBody() {
        Iso8583FieldReader overOffset = Iso8583FieldReader.startingAt("XX003abc", 2);
        assertThat(overOffset.readLllPrintableAscii(1, 999)).isEqualTo("abc");
        assertThat(overOffset.position()).isEqualTo(8);
        overOffset.requireEnd();

        Iso8583FieldReader trailing = Iso8583FieldReader.over("003abcX");
        assertThat(trailing.readLllPrintableAscii(1, 999)).isEqualTo("abc");
        assertFailsWith(Rule.TRAILING_BYTES, trailing::requireEnd);

        assertFailsWith(Rule.START_OUT_OF_BOUNDS,
                () -> Iso8583FieldReader.startingAt("abc", 4));
        assertFailsWith(Rule.START_OUT_OF_BOUNDS,
                () -> Iso8583FieldReader.startingAt("abc", -1));
    }

    // --- Fixed numeric framing ----------------------------------------------

    @Test
    void fixedNumericConsumesExactlyItsWidthAndRejectsShortOrNonDecimalBodies() {
        Iso8583FieldReader reader = Iso8583FieldReader.over("840X");
        assertThat(reader.readFixedDigits(3)).isEqualTo("840");
        assertThat(reader.position()).isEqualTo(3);
        assertThat(reader.remaining()).isEqualTo(1);

        assertFailsWith(Rule.BODY_TOO_SHORT,
                () -> Iso8583FieldReader.over("84").readFixedDigits(3));
        assertFailsWith(Rule.INVALID_NUMERIC,
                () -> Iso8583FieldReader.over("8A0").readFixedDigits(3));
        assertFailsWith(Rule.INVALID_NUMERIC,
                () -> Iso8583FieldReader.over("84 ").readFixedDigits(3));
        assertFailsWith(Rule.INVALID_NUMERIC,
                () -> Iso8583FieldReader.over("\u0668\u0664\u0660").readFixedDigits(3));
    }

    private static void assertFailsWith(Rule expected, ThrowingCallable callable) {
        assertThatThrownBy(callable)
                .isInstanceOfSatisfying(Iso8583FieldException.class,
                        e -> assertThat(e.rule()).isEqualTo(expected));
    }
}
