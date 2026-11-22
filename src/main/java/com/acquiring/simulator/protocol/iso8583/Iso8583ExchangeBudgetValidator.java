package com.acquiring.simulator.protocol.iso8583;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Pure NEW-exchange admission-budget validator derived from the approved
 * response suffix.
 *
 * <p>Approved source rule ({@code iso8583-profile.md} P2a): structural parsing
 * still accepts DE48 up to 999 bytes, but a NEW request must be admitted only
 * when its DE48 is at most 826 bytes. The 173-byte difference is not a loose
 * capacity choice: it is the exact length of the longest supported canonical
 * response suffix, so that every echoed request can carry the worst mandatory
 * response metadata without discovering an overflow after a permitted send.</p>
 *
 * <p>The reserve is derived here from the approved selected family instead of
 * being copied as a number:</p>
 *
 * <ul>
 *   <li>{@code ;actor=;stage=;result=;reason=;evidence=;at=} key/separator
 *       overhead comes from {@link Iso8583De48ResponseParser#RESPONSE_KEYS};</li>
 *   <li>the longest {@code actor/stage/result/reason} value combination comes
 *       from the combinations the approved {@link Iso8583P4MatrixValidator}
 *       actually accepts;</li>
 *   <li>the evidence identifier is the approved 64-byte maximum and {@code at}
 *       is its fixed 14-digit Gregorian form.</li>
 * </ul>
 *
 * <p>The result is exactly 173 bytes (44 + 51 + 64 + 14) and therefore exactly
 * 826 admitted request bytes. No padding, rounding, truncation, key dropping,
 * reference shortening or alternate reserve is allowed. A 827–999-byte request
 * is structurally valid but fails {@code ADMISSION_RESPONSE_CAPACITY} before
 * claim or dispatch; 826 bytes is accepted; a 999-byte response remains
 * structurally representable. Authorized historical replay and late evidence
 * are not re-evaluated, re-encoded or sent again: the persisted original stays
 * authoritative. The mode is a trusted server-side enum, never a wire field.</p>
 *
 * <p>This is a byte-budget rule only. It does not parse DE48, authenticate a
 * producer, resolve a registry fixture/mandate/route, apply the P4 matrix,
 * bind an instruction or DE38, decide duplicate/conflict semantics, or claim a
 * financial outcome.</p>
 */
public final class Iso8583ExchangeBudgetValidator {

    /** Structural DE48 maximum from P1; structural packing keeps this limit. */
    public static final int STRUCTURAL_DE48_MAX_BYTES = Iso8583De48RequestParser.MAX_BYTES;

    /** Approved maximum length of the response {@code evidence} identifier. */
    public static final int EVIDENCE_ID_MAX_BYTES = 64;

    /** Fixed width of the canonical response {@code at} timestamp. */
    public static final int AT_TIMESTAMP_BYTES = 14;

    /** First-failure label when a structurally valid NEW request exceeds 826. */
    public static final String FAILURE_RULE = "ADMISSION_RESPONSE_CAPACITY";

    /** Candidate values from the approved P1/P4 profile; every one is probed. */
    private static final List<String> ACTORS = List.of("ISSUER", "NETWORK", "PROCESSOR");

    private static final List<String> STAGES = List.of(
            "ISSUER_REPLIED", "BEFORE_ISSUER", "POSSIBLE_ISSUER", "BEFORE_NETWORK");

    private static final List<String> RESULTS = List.of(
            "APPROVED", "DECLINED", "PROCESSOR_REJECTED", "NOT_CONFIRMED");

    private static final List<String> REASONS = List.of(
            "APPROVED", "DELIBERATE_DECLINE", "FORMAT", "DISALLOWED", "UNAVAILABLE", "UNCERTAIN");

    private static final Set<String> DE39_CODES = Iso8583P4MatrixValidator.SUPPORTED_DE39;

    /**
     * Bytes used by the six mandatory response tail keys, including each
     * leading {@code ;} and separating {@code =}.
     */
    public static final int RESPONSE_TAIL_KEY_OVERHEAD_BYTES =
            responseTailKeyOverheadBytes();

    /** Longest metadata values accepted by the approved P4 matrix. */
    public static final int LONGEST_SUPPORTED_RESPONSE_METADATA_BYTES =
            longestSupportedMetadataBytes();

    /** Exact suffix reserve: keys + metadata + evidence + at. */
    public static final int RESPONSE_SUFFIX_RESERVE_BYTES =
            RESPONSE_TAIL_KEY_OVERHEAD_BYTES
                    + LONGEST_SUPPORTED_RESPONSE_METADATA_BYTES
                    + EVIDENCE_ID_MAX_BYTES
                    + AT_TIMESTAMP_BYTES;

    /** Largest NEW request DE48 that still leaves room for the worst response. */
    public static final int NEW_REQUEST_DE48_MAX_BYTES =
            STRUCTURAL_DE48_MAX_BYTES - RESPONSE_SUFFIX_RESERVE_BYTES;

    static {
        if (NEW_REQUEST_DE48_MAX_BYTES <= 0
                || NEW_REQUEST_DE48_MAX_BYTES >= STRUCTURAL_DE48_MAX_BYTES) {
            throw new ExceptionInInitializerError(
                    "derived exchange budget is not a usable positive bound");
        }
    }

    private Iso8583ExchangeBudgetValidator() {
    }

    /**
     * Apply the exchange budget for one trusted admission mode.
     *
     * @param requestDe48Bytes structurally accepted request DE48 length, 1..999
     * @param mode             trusted server-side mode; never a wire field
     * @return accepted, rejected or not applicable
     * @throws NullPointerException     when the server mode is missing
     * @throws IllegalArgumentException when a NEW request length is outside the
     *                                  structural 1..999 range; such a length can
     *                                  never reach this phase through the parser
     */
    public static Iso8583ExchangeBudgetDecision evaluate(
            int requestDe48Bytes, Iso8583AdmissionMode mode) {
        Objects.requireNonNull(mode, "admission mode must be supplied by the server");
        if (mode != Iso8583AdmissionMode.NEW_FIRST_DISPATCH) {
            // The persisted original request is authoritative. Do not read,
            // validate, re-encode or resend a fresh length for replay/late.
            return Iso8583ExchangeBudgetDecision.NOT_APPLICABLE;
        }
        if (requestDe48Bytes < 1 || requestDe48Bytes > STRUCTURAL_DE48_MAX_BYTES) {
            throw new IllegalArgumentException(
                    "request DE48 must be within the structurally accepted 1..999 octets");
        }
        return requestDe48Bytes <= NEW_REQUEST_DE48_MAX_BYTES
                ? Iso8583ExchangeBudgetDecision.ACCEPTED
                : Iso8583ExchangeBudgetDecision.REJECTED;
    }

    private static int responseTailKeyOverheadBytes() {
        int bytes = 0;
        for (String key : Iso8583De48ResponseParser.RESPONSE_KEYS) {
            bytes += 1 + key.length() + 1;
        }
        return bytes;
    }

    /**
     * Probe the approved matrix rather than restating its rows: only accepted
     * combinations may contribute to the worst-case metadata length.
     */
    private static int longestSupportedMetadataBytes() {
        int longest = 0;
        for (String actor : ACTORS) {
            for (String stage : STAGES) {
                for (String de39 : DE39_CODES) {
                    for (String result : RESULTS) {
                        for (String reason : REASONS) {
                            if (!isApprovedCombination(actor, stage, de39, result, reason)) {
                                continue;
                            }
                            int bytes = actor.length() + stage.length()
                                    + result.length() + reason.length();
                            if (bytes > longest) {
                                longest = bytes;
                            }
                        }
                    }
                }
            }
        }
        return longest;
    }

    private static boolean isApprovedCombination(
            String actor, String stage, String de39, String result, String reason) {
        try {
            Iso8583P4MatrixValidator.validate(actor, stage, de39, result, reason);
            return true;
        } catch (Iso8583P4MatrixException e) {
            return false;
        }
    }
}
