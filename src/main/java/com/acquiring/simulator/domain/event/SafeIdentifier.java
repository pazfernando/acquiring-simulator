package com.acquiring.simulator.domain.event;

import java.util.Locale;

/**
 * Normalization for identifiers the simulator generates itself (authorization, correlation and
 * evidence identifiers). These identifiers travel into canonical event payloads, so they must
 * satisfy {@link EventSanitizer} without the sanitizer being relaxed for caller-supplied data.
 *
 * <p>The generator emits 24 lowercase hexadecimal characters. That alphabet and length are kept.
 * A run of 13 to 19 digits would look like a PAN to {@link EventSanitizer#requireSafe}, so a
 * candidate carrying a run longer than {@link #MAX_DIGIT_RUN} is repaired deterministically: the
 * character that would extend the run past the limit is replaced by a hexadecimal letter derived
 * from that same digit. Every other character is preserved, so the repair removes at most one
 * nibble per over-long run and never changes an already-safe candidate. This is a construction,
 * not sampling: no candidate can carry a PAN-shaped run after normalization.
 */
public final class SafeIdentifier {

    /** Contract length of a generated identifier payload (without the AUTH-/CORR-/EVT- prefix). */
    public static final int LENGTH = 24;

    /** Longest admitted digit run; a PAN shape needs 13 or more consecutive digits. */
    public static final int MAX_DIGIT_RUN = 12;

    private static final char[] HEX_LETTERS = {'a', 'b', 'c', 'd', 'e', 'f'};

    private SafeIdentifier() {
    }

    /**
     * Returns the first {@link #LENGTH} characters of {@code candidate}, guaranteed free of a
     * PAN-shaped digit run.
     *
     * @throws IllegalArgumentException when the candidate is shorter than {@link #LENGTH} or is
     *     not hexadecimal
     */
    public static String normalize(String candidate) {
        if (candidate == null || candidate.length() < LENGTH) {
            throw new IllegalArgumentException(
                    "identifier candidate must supply at least " + LENGTH + " characters");
        }
        char[] chars = candidate.substring(0, LENGTH).toLowerCase(Locale.ROOT).toCharArray();
        for (char c : chars) {
            if (!isHex(c)) {
                throw new IllegalArgumentException("identifier candidate must be hexadecimal");
            }
        }
        int run = 0;
        for (int i = 0; i < chars.length; i++) {
            if (chars[i] >= '0' && chars[i] <= '9') {
                run++;
                if (run > MAX_DIGIT_RUN) {
                    chars[i] = HEX_LETTERS[(chars[i] - '0') % HEX_LETTERS.length];
                    run = 0;
                }
            } else {
                run = 0;
            }
        }
        return new String(chars);
    }

    private static boolean isHex(char c) {
        return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f');
    }
}
