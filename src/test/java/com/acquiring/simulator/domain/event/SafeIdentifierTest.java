package com.acquiring.simulator.domain.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class SafeIdentifierTest {

    @Test
    void leavesAlreadySafeCandidatesUnchanged() {
        String mixed = "a1b2c3d4e5f6a7b8c9d0e1f2";
        // A 12-digit run is the longest admitted run and must not be rewritten.
        String boundary = "123456789012abcdefabcdef";

        assertThat(SafeIdentifier.normalize(mixed)).isEqualTo(mixed);
        assertThat(SafeIdentifier.normalize(boundary)).isEqualTo(boundary);
        EventSanitizer.requireSafe("correlationId", "CORR-" + mixed);
        EventSanitizer.requireSafe("correlationId", "CORR-" + boundary);
    }

    @Test
    void repairsEveryPanShapedDigitRunDeterministically() {
        List<String> repaired = new ArrayList<>();
        for (int length = SafeIdentifier.MAX_DIGIT_RUN + 1; length <= SafeIdentifier.LENGTH; length++) {
            for (int start = 0; start + length <= SafeIdentifier.LENGTH; start++) {
                char[] candidate = "abcdefabcdefabcdefabcdef".toCharArray();
                for (int i = start; i < start + length; i++) {
                    candidate[i] = (char) ('0' + (i % 10));
                }
                String raw = new String(candidate);

                String normalized = SafeIdentifier.normalize(raw);

                assertThat(normalized).hasSize(SafeIdentifier.LENGTH);
                assertThat(normalized).matches("[0-9a-f]+");
                assertThat(longestDigitRun(normalized))
                        .isLessThanOrEqualTo(SafeIdentifier.MAX_DIGIT_RUN);
                assertThat(normalized).isNotEqualTo(raw);
                assertThat(differences(raw, normalized)).isEqualTo(1);
                // The character that would extend the run past the limit is the one replaced.
                assertThat(normalized.charAt(start + SafeIdentifier.MAX_DIGIT_RUN))
                        .isBetween('a', 'f');
                EventSanitizer.requireSafe("eventId", "EVT-" + normalized);
                EventSanitizer.requireSafe("correlationId", "CORR-" + normalized);
                repaired.add(normalized);
            }
        }
        assertThat(repaired).isNotEmpty();
    }

    @Test
    void knownDangerousGeneratorOutputIsRejectedBeforeRepairAndAcceptedAfter() {
        // 13 consecutive digits bounded by hexadecimal letters: the observed failure shape,
        // where the raw generator output matches the sanitizer's PAN shape.
        String dangerous = "ab1234567890123cdef01234";

        // Reproduction of the observed failure shape: the raw generator output is a PAN shape.
        assertThatThrownBy(() -> EventSanitizer.requireSafe("correlationId", "CORR-" + dangerous))
                .isInstanceOf(EventValidationException.class);

        String normalized = SafeIdentifier.normalize(dangerous);
        EventSanitizer.requireSafe("correlationId", "CORR-" + normalized);
        assertThat(longestDigitRun(normalized))
                .isLessThanOrEqualTo(SafeIdentifier.MAX_DIGIT_RUN);
    }

    @Test
    void repairKeepsAllButTheRepairedCharacterAndSampledVariantsRemainDistinct() {
        String dangerous = "ab1234567890123cdef01234";
        String normalized = SafeIdentifier.normalize(dangerous);

        // 23 of 24 characters survive; only the repaired position loses a nibble.
        assertThat(differences(dangerous, normalized)).isEqualTo(1);

        // Sample-level distinctness for this fixed prefix: 16 hexadecimal variants at a position
        // after the repair point are distinct. This is not a claim of global injectivity or
        // uniqueness across the whole input space.
        List<String> outputs = new ArrayList<>();
        for (char suffix : "0123456789abcdef".toCharArray()) {
            outputs.add(SafeIdentifier.normalize(dangerous.substring(0, 23) + suffix));
        }
        assertThat(outputs).doesNotHaveDuplicates();
    }

    @Test
    void rejectsCandidatesThatCannotBeNormalized() {
        assertThatThrownBy(() -> SafeIdentifier.normalize(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SafeIdentifier.normalize("abcd"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SafeIdentifier.normalize("zzzzzzzzzzzzzzzzzzzzzzzz"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void sanitizerHeuristicsRemainActiveBeyondThePanShape() {
        // Installing the generator fix must not relax the sanitizer for caller-supplied content.
        assertThatThrownBy(() -> EventSanitizer.requireSafe("detail", "PIN BLOCK present"))
                .isInstanceOf(EventValidationException.class);
        assertThatThrownBy(() -> EventSanitizer.requireSafe("detail",
                "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIn0.signature"))
                .isInstanceOf(EventValidationException.class);
        assertThatThrownBy(() -> EventSanitizer.requireSafe("detail", "Bearer abc.def"))
                .isInstanceOf(EventValidationException.class);
    }

    private static int longestDigitRun(String value) {
        int longest = 0;
        int run = 0;
        for (int i = 0; i < value.length(); i++) {
            if (Character.isDigit(value.charAt(i))) {
                run++;
                longest = Math.max(longest, run);
            } else {
                run = 0;
            }
        }
        return longest;
    }

    private static int differences(String left, String right) {
        int count = 0;
        for (int i = 0; i < left.length(); i++) {
            if (left.charAt(i) != right.charAt(i)) {
                count++;
            }
        }
        return count;
    }
}
