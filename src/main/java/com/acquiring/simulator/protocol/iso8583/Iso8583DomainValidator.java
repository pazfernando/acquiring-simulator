package com.acquiring.simulator.protocol.iso8583;

import java.util.Set;

/**
 * Pure SIM-ISO87A-v1 domain validator for the approved amount, currency,
 * profile-code and Gregorian {@code sent}/DE7 projections.
 *
 * <p>It consumes one already-scanned message plus the already-parsed canonical
 * DE48 extension (request or response) and applies only these rules, in the
 * fixed P6 domain order:</p>
 *
 * <ol>
 *   <li>profile codes: DE3 {@code 000000}, DE22 {@code 051}/{@code 071},
 *       DE25 {@code 00};</li>
 *   <li>DE4 minor units {@code 100..999999} inclusive, no rounding or
 *       truncation;</li>
 *   <li>DE49 exactly {@code 840}; every other currency is rejected;</li>
 *   <li>{@code sent} as a strict Gregorian UTC {@code YYYYMMDDhhmmss}
 *       timestamp, including leap days and without nearest-year inference;</li>
 *   <li>DE7 exactly the {@code MMDDhhmmss} projection of that {@code sent}.</li>
 * </ol>
 *
 * <p>For a response, {@code sent} is the echoed <em>original request</em>
 * timestamp. DE7 is therefore compared with the original {@code sent}, never
 * with the response {@code at} availability time; {@code at} is deliberately
 * not validated here beyond the schema's 14-digit form, because no approved
 * domain rule requires it. This validator does not resolve a registry fixture
 * or route, authenticate an actor, bind an original instruction or DE38, apply
 * freshness or 826 admission, or decide a financial outcome.</p>
 *
 * <p>The structural scanner and canonical DE48 schema remain strictly upstream:
 * this type assumes the message and extension were already accepted there and
 * only re-checks the domain values. Callers run this phase before instruction
 * binding so an amount/currency/date/DE7 failure wins over a later echo
 * mismatch.</p>
 */
public final class Iso8583DomainValidator {

    /** Inclusive lower bound of an approved DE4 minor-unit amount. */
    public static final long MIN_AMOUNT_MINOR = 100L;

    /** Inclusive upper bound of an approved DE4 minor-unit amount. */
    public static final long MAX_AMOUNT_MINOR = 999_999L;

    /** The single approved DE49 currency (USD). */
    public static final String USD_CURRENCY = "840";

    /** Approved DE3 private purchase processing code. */
    public static final String PROCESSING_CODE = "000000";

    /** Approved DE22 contact and contactless entry-mode codes. */
    public static final Set<String> ENTRY_MODES = Set.of("051", "071");

    /** Approved DE25 POS condition code. */
    public static final String POS_CONDITION = "00";

    private Iso8583DomainValidator() {
    }

    /**
     * Validate the domain projection of one {@code 0100} request.
     *
     * @param message scanned request message
     * @param request accepted canonical DE48 request extension
     * @return domain projection only; never admission or trust
     * @throws Iso8583DomainException on the first violated domain rule
     */
    public static Iso8583DomainDecision validateRequest(
            Iso8583Message message, Iso8583De48Request request) {
        requirePresent(message, "message");
        requirePresent(request, "request");
        return validate(message, request.value("sent"));
    }

    /**
     * Validate the domain projection of one {@code 0110} response. The echoed
     * {@code sent} is the original request timestamp; {@code at} is not used.
     *
     * @param message scanned response message
     * @param response accepted canonical DE48 response extension
     * @return domain projection only; never admission or trust
     * @throws Iso8583DomainException on the first violated domain rule
     */
    public static Iso8583DomainDecision validateResponse(
            Iso8583Message message, Iso8583De48Response response) {
        requirePresent(message, "message");
        requirePresent(response, "response");
        return validate(message, response.value("sent"));
    }

    private static Iso8583DomainDecision validate(Iso8583Message message, String sent) {
        // P6 domain profile codes, then amount, then currency.
        String de3 = requireFixedDigits(message, 3, 6, "de3");
        if (!PROCESSING_CODE.equals(de3)) {
            throw new Iso8583DomainException(
                    Iso8583DomainException.Rule.PROFILE_CODE_UNSUPPORTED,
                    "de3",
                    "DE3 is not the approved profile processing code");
        }

        String de22 = requireFixedDigits(message, 22, 3, "de22");
        if (!ENTRY_MODES.contains(de22)) {
            throw new Iso8583DomainException(
                    Iso8583DomainException.Rule.PROFILE_CODE_UNSUPPORTED,
                    "de22",
                    "DE22 is not an approved profile entry-mode code");
        }

        String de25 = requireFixedDigits(message, 25, 2, "de25");
        if (!POS_CONDITION.equals(de25)) {
            throw new Iso8583DomainException(
                    Iso8583DomainException.Rule.PROFILE_CODE_UNSUPPORTED,
                    "de25",
                    "DE25 is not the approved profile POS condition code");
        }

        String de4 = requireFixedDigits(message, 4, 12, "de4");
        long amountMinor = Long.parseLong(de4);
        if (amountMinor < MIN_AMOUNT_MINOR || amountMinor > MAX_AMOUNT_MINOR) {
            throw new Iso8583DomainException(
                    Iso8583DomainException.Rule.AMOUNT_OUT_OF_RANGE,
                    "de4",
                    "DE4 minor units are outside the approved amount range");
        }

        String de49 = requireFixedDigits(message, 49, 3, "de49");
        if (!USD_CURRENCY.equals(de49)) {
            throw new Iso8583DomainException(
                    Iso8583DomainException.Rule.CURRENCY_UNSUPPORTED,
                    "de49",
                    "DE49 is not the single approved currency");
        }

        // Full original-request Gregorian timestamp, then its DE7 projection.
        requirePresent(sent, "sent");
        requireGregorian(sent);

        String de7 = requireFixedDigits(message, 7, 10, "de7");
        if (!de7.equals(sent.substring(4))) {
            throw new Iso8583DomainException(
                    Iso8583DomainException.Rule.DE7_MISMATCH,
                    "de7",
                    "DE7 is not the month/day/time projection of the original sent timestamp");
        }

        return new Iso8583DomainDecision(amountMinor, de49, sent, de7);
    }

    private static String requireFixedDigits(
            Iso8583Message message, int dataElement, int width, String component) {
        String value = message.field(dataElement);
        if (value == null || value.isEmpty()) {
            throw new Iso8583DomainException(
                    Iso8583DomainException.Rule.MISSING_FIELD,
                    component,
                    "required domain field is absent");
        }
        if (value.length() != width) {
            throw new Iso8583DomainException(
                    Iso8583DomainException.Rule.MALFORMED_FIELD,
                    component,
                    "domain field does not have the approved fixed width");
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c < '0' || c > '9') {
                throw new Iso8583DomainException(
                        Iso8583DomainException.Rule.MALFORMED_FIELD,
                        component,
                        "domain field is not ASCII digits");
            }
        }
        return value;
    }

    /**
     * Strict Gregorian UTC check. {@code sent} is a full 14-digit request year,
     * so no century is inferred. Month/day/hour/minute/second bounds are
     * deterministic and leap days use the Gregorian rule; no date or time is
     * normalized. The Gregorian calendar has no year zero, so {@code 0000} is
     * rejected rather than shifted.
     */
    private static void requireGregorian(String sent) {
        if (sent.length() != 14) {
            throw new Iso8583DomainException(
                    Iso8583DomainException.Rule.MALFORMED_FIELD,
                    "sent",
                    "sent is not a 14-digit UTC timestamp");
        }
        for (int i = 0; i < sent.length(); i++) {
            char c = sent.charAt(i);
            if (c < '0' || c > '9') {
                throw new Iso8583DomainException(
                        Iso8583DomainException.Rule.MALFORMED_FIELD,
                        "sent",
                        "sent is not ASCII digits");
            }
        }

        int year = Integer.parseInt(sent.substring(0, 4));
        int month = Integer.parseInt(sent.substring(4, 6));
        int day = Integer.parseInt(sent.substring(6, 8));
        int hour = Integer.parseInt(sent.substring(8, 10));
        int minute = Integer.parseInt(sent.substring(10, 12));
        int second = Integer.parseInt(sent.substring(12, 14));

        if (year < 1 || month < 1 || month > 12
                || hour > 23 || minute > 59 || second > 59) {
            throw invalidGregorian();
        }
        if (day < 1 || day > daysInMonth(year, month)) {
            throw invalidGregorian();
        }
    }

    private static int daysInMonth(int year, int month) {
        return switch (month) {
            case 2 -> isLeapYear(year) ? 29 : 28;
            case 4, 6, 9, 11 -> 30;
            default -> 31;
        };
    }

    private static boolean isLeapYear(int year) {
        return year % 4 == 0 && (year % 100 != 0 || year % 400 == 0);
    }

    private static Iso8583DomainException invalidGregorian() {
        return new Iso8583DomainException(
                Iso8583DomainException.Rule.INVALID_GREGORIAN_DATE,
                "sent",
                "sent is not a strict Gregorian UTC date and time");
    }

    private static void requirePresent(Object value, String component) {
        if (value == null) {
            throw new Iso8583DomainException(
                    Iso8583DomainException.Rule.MISSING_FIELD,
                    component,
                    "required domain input is absent");
        }
    }
}
