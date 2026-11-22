package com.acquiring.simulator.protocol.iso8583;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;

class Iso8583HeaderParserTest {

    /**
     * Prefix of the approved packed 0100 fixture in
     * docs/ai-sdlc/work-items/WI-ACQ-OBS-001/vectors/evidence-fixtures.json.
     */
    private static final String FIXTURE_0100_HEX =
            "30313030333232303034383130384331383030303030303030303030303030303030313030303131";

    @Test
    void parsesKnownPackedFixtureHeader() {
        String body = fixtureBody();

        Iso8583Header header = Iso8583HeaderParser.parse(body);

        assertThat(header.mti()).isEqualTo("0100");
        assertThat(header.headerLength()).isEqualTo(20);
        assertThat(header.bitmap().setFields())
                .containsExactly(3, 4, 7, 11, 22, 25, 32, 37, 41, 42, 48, 49);
        assertThat(header.bitmap().isSet(1)).isFalse();
    }

    @Test
    void rejectsTruncatedAndMalformedBitmapPrefixes() {
        String full = fixtureBody();

        assertThatThrownBy(() -> Iso8583HeaderParser.parse(full.substring(0, 19)))
                .isInstanceOfSatisfying(Iso8583HeaderParseException.class,
                        e -> assertThat(e.rule())
                                .isEqualTo(Iso8583HeaderParseException.Rule.BODY_TOO_SHORT));

        String lowercaseBitmap = full.substring(0, 4) + "3220048108c18000" + full.substring(20);
        assertThatThrownBy(() -> Iso8583HeaderParser.parse(lowercaseBitmap))
                .isInstanceOfSatisfying(Iso8583HeaderParseException.class,
                        e -> assertThat(e.rule())
                                .isEqualTo(Iso8583HeaderParseException.Rule.INVALID_BITMAP));

        String nonHexBitmap = full.substring(0, 4) + "3220048108G18000" + full.substring(20);
        assertThatThrownBy(() -> Iso8583HeaderParser.parse(nonHexBitmap))
                .isInstanceOfSatisfying(Iso8583HeaderParseException.class,
                        e -> assertThat(e.rule())
                                .isEqualTo(Iso8583HeaderParseException.Rule.INVALID_BITMAP));
    }

    @Test
    void rejectsSecondaryBitmapIndicatorAndDisabledFields() {
        String full = fixtureBody();

        // Bit 1 set: secondary bitmap announced even though the profile forbids it.
        String secondary = full.substring(0, 4) + "B220048108C18000" + full.substring(20);
        assertThatThrownBy(() -> Iso8583HeaderParser.parse(secondary))
                .isInstanceOfSatisfying(Iso8583HeaderParseException.class,
                        e -> assertThat(e.rule()).isEqualTo(
                                Iso8583HeaderParseException.Rule.SECONDARY_BITMAP_FORBIDDEN));

        // Bit 2 set: PAN (DE2) is disabled by the profile.
        String pan = full.substring(0, 4) + "7220048108C18000" + full.substring(20);
        assertThatThrownBy(() -> Iso8583HeaderParser.parse(pan))
                .isInstanceOfSatisfying(Iso8583HeaderParseException.class,
                        e -> assertThat(e.rule())
                                .isEqualTo(Iso8583HeaderParseException.Rule.UNSUPPORTED_FIELD));
    }

    private static String fixtureBody() {
        String header = new String(HexFormat.of().parseHex(FIXTURE_0100_HEX), StandardCharsets.US_ASCII);
        // Remainder is irrelevant to this primitive; append a benign suffix.
        return header + "000000";
    }
}
