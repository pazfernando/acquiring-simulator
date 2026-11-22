package com.acquiring.simulator.channel;

import java.math.BigDecimal;

/**
 * Exact C1 amount conversion between the approved channel string form and
 * integer minor units.
 *
 * <p>The channel body carries the amount as a string matching
 * {@code [0-9]{1,4}\.[0-9]{2}}. Conversion is pure decimal arithmetic on the
 * two explicit digits: no {@code double}/{@code float}, no {@code BigDecimal}
 * rounding mode, no truncation, padding, normalization or locale parsing. The
 * <em>syntax</em> failures are labeled {@value #CHANNEL_SCHEMA_AMOUNT}; a
 * syntactically valid value outside the approved simulator amount range is
 * labeled {@value #DOMAIN_AMOUNT}, because the 1.00 floor is the existing
 * approved domain rule, not a new channel rule.</p>
 *
 * <p>The inclusive upper bound is structural: a four-digit integral part cannot
 * exceed 9999.99. The inclusive lower bound is the approved HD-01 domain floor.
 * This type allocates no reference, resolves no fixture/route and produces no
 * ISO projection.</p>
 */
public final class ChannelAmount {

    /** First-failure label for a syntactically invalid channel amount. */
    public static final String CHANNEL_SCHEMA_AMOUNT = "CHANNEL_SCHEMA_AMOUNT";

    /** First-failure label for a schema-valid amount below the approved floor. */
    public static final String DOMAIN_AMOUNT = "DOMAIN_AMOUNT";

    /** Inclusive approved lower bound in minor units (1.00). */
    public static final long MIN_MINOR_UNITS = 100L;

    /** Inclusive structural upper bound in minor units (9999.99). */
    public static final long MAX_MINOR_UNITS = 999_999L;

    /** Maximum digits before the decimal point (amounts up to 9999.99). */
    public static final int MAX_INTEGRAL_DIGITS = 4;

    /** Exactly two fraction digits are approved. */
    public static final int FRACTION_DIGITS = 2;

    private ChannelAmount() {
    }

    /**
     * Converts one approved channel amount string to exact minor units.
     *
     * @param amount channel string; never {@code null} or a JSON number here
     * @return integer minor units in {@code 100..999999}
     * @throws ChannelRequestValidationException with {@value #CHANNEL_SCHEMA_AMOUNT}
     *         for a syntax failure, or {@value #DOMAIN_AMOUNT} below the floor
     */
    public static long toMinorUnits(String amount) {
        if (amount == null) {
            throw invalid(CHANNEL_SCHEMA_AMOUNT);
        }
        int dot = amount.indexOf('.');
        int length = amount.length();
        if (dot < 1 || dot > MAX_INTEGRAL_DIGITS
                || length - dot != FRACTION_DIGITS + 1) {
            throw invalid(CHANNEL_SCHEMA_AMOUNT);
        }
        long integral = 0L;
        for (int i = 0; i < dot; i++) {
            char digit = amount.charAt(i);
            if (digit < '0' || digit > '9') {
                throw invalid(CHANNEL_SCHEMA_AMOUNT);
            }
            integral = integral * 10L + (digit - '0');
        }
        // "0.99" is well formed; "00.99" and "01.00" are not approved.
        if (dot > 1 && amount.charAt(0) == '0') {
            throw invalid(CHANNEL_SCHEMA_AMOUNT);
        }
        int cents = 0;
        for (int i = dot + 1; i < length; i++) {
            char digit = amount.charAt(i);
            if (digit < '0' || digit > '9') {
                throw invalid(CHANNEL_SCHEMA_AMOUNT);
            }
            cents = cents * 10 + (digit - '0');
        }
        long minor = integral * 100L + cents;
        if (minor < MIN_MINOR_UNITS || minor > MAX_MINOR_UNITS) {
            throw invalid(DOMAIN_AMOUNT);
        }
        return minor;
    }

    /**
     * Exact two-decimal projection of validated minor units, for the later
     * business adaptation.
     *
     * @param minorUnits integer minor units already validated by the channel
     * @return exact decimal amount with scale 2
     */
    public static BigDecimal toAmount(long minorUnits) {
        if (minorUnits < 0) {
            throw new IllegalArgumentException("minor units must be nonnegative");
        }
        return BigDecimal.valueOf(minorUnits, FRACTION_DIGITS);
    }

    private static ChannelRequestValidationException invalid(String rule) {
        return new ChannelRequestValidationException(ChannelErrorCode.INVALID_INPUT, rule, "amount");
    }
}
