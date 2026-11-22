package com.acquiring.simulator.protocol.iso8583;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.Objects;

/**
 * Pure NEW-request freshness validator for the approved 300-second window.
 *
 * <p>Approved source rule ({@code iso8583-profile.md} P2): <em>"New first
 * dispatch sent must be within 300 seconds of server receipt; authorized
 * historical replay/late evidence uses persisted request, not this freshness
 * check."</em> This class compares the full Gregorian {@code sent} instant with
 * the supplied server receipt instant. A distance of exactly 300.000 seconds is
 * inside ({@code FRESH}); 300.001 or 301 seconds is outside ({@code STALE}).
 * There is no padding, truncation, nearest-year inference or allowance beyond
 * the same 300-second window. The approved cross-year example is two distinct
 * full instants, so no month/day-only comparison is made.</p>
 *
 * <p>The mode is a trusted server-side seam, never decoded from the wire. For
 * {@link Iso8583AdmissionMode#AUTHORIZED_HISTORICAL_REPLAY} and
 * {@link Iso8583AdmissionMode#LATE_EVIDENCE} this method returns
 * {@link Iso8583FreshnessDecision#NOT_APPLICABLE} without reading {@code sent}
 * or {@code receivedAt}: the persisted original request stays authoritative, is
 * not re-evaluated or re-encoded, and this check must not dispatch again.</p>
 *
 * <p>It does not authenticate, resolve a fixture/mandate/route, bind an
 * instruction or DE38, apply the 826 exchange budget, decide duplicate/conflict
 * semantics or claim any financial outcome.</p>
 */
public final class Iso8583FreshnessValidator {

    /** Approved inclusive distance between {@code sent} and server receipt. */
    public static final Duration MAX_SENT_DISTANCE = Duration.ofSeconds(300);

    /** First-failure label from the approved {@code D-stale} vector. */
    public static final String FAILURE_RULE = "ADMISSION_FRESHNESS";

    private static final DateTimeFormatter SENT_FORMAT =
            DateTimeFormatter.ofPattern("uuuuMMddHHmmss").withResolverStyle(ResolverStyle.STRICT);

    private static final int SENT_LENGTH = 14;

    private Iso8583FreshnessValidator() {
    }

    /**
     * Evaluate the approved freshness rule for one admission mode.
     *
     * @param sent       already domain-validated {@code sent}, {@code uuuuMMddHHmmss} UTC
     * @param receivedAt server receipt instant, not a client value
     * @param mode       trusted server-side mode; never a wire field
     * @return fresh/stale for a new first dispatch, otherwise not applicable
     */
    public static Iso8583FreshnessDecision evaluate(
            String sent, Instant receivedAt, Iso8583AdmissionMode mode) {
        Objects.requireNonNull(mode, "admission mode must be supplied by the server");
        if (mode != Iso8583AdmissionMode.NEW_FIRST_DISPATCH) {
            return Iso8583FreshnessDecision.NOT_APPLICABLE;
        }
        Objects.requireNonNull(sent, "sent is required for a new first dispatch");
        Objects.requireNonNull(receivedAt, "server receipt is required for a new first dispatch");

        Instant sentAt = parseSent(sent);
        Duration distance = Duration.between(sentAt, receivedAt).abs();
        return distance.compareTo(MAX_SENT_DISTANCE) <= 0
                ? Iso8583FreshnessDecision.FRESH
                : Iso8583FreshnessDecision.STALE;
    }

    /**
     * Strict UTC parse of an already domain-validated {@code sent}. The domain
     * phase owns the approved Gregorian rule; this helper only converts the
     * accepted value to an instant and fails closed on a malformed direct call.
     */
    private static Instant parseSent(String sent) {
        if (sent.length() != SENT_LENGTH) {
            throw new IllegalArgumentException("sent must be a 14-digit UTC timestamp");
        }
        try {
            LocalDateTime local = LocalDateTime.parse(sent, SENT_FORMAT);
            if (local.getYear() < 1) {
                throw new IllegalArgumentException("sent year zero is not Gregorian");
            }
            return local.toInstant(ZoneOffset.UTC);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("sent must be a strict Gregorian UTC timestamp", e);
        }
    }
}
