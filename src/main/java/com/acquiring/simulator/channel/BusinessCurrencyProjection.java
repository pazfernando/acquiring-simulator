package com.acquiring.simulator.channel;

/**
 * The only approved business-currency ↔ ISO 4217 numeric projection used at the signed-evidence
 * receipt cross-check.
 *
 * <p>The approved contracts define two representations of the same simulator currency:</p>
 * <ul>
 *   <li>{@code channel-contract.md} C1 fixes the channel/business currency field to {@code "USD"}
 *       (and the requirements HD-01 baseline is USD-only).</li>
 *   <li>{@code iso8583-profile.md} P1 fixes DE49 to the three digits {@code 840} as the required
 *       request/response echo. P5 requires the sealed evidence {@code currency} to equal the
 *       persisted instruction/response {@code DE49}.</li>
 * </ul>
 *
 * <p>Neither contract permits any other currency in v1 and neither says the persisted business
 * currency must be rewritten to the wire value. A persisted record may legitimately carry either
 * approved representation of that one currency (the business token {@code USD} from the service path
 * or the ISO {@code 840} already used by the signed fixtures), so {@link #isoNumeric} accepts both
 * and normalizes to {@code 840}. Every other value is refused. This class never changes a persisted
 * record, a signed attestation or an ISO field; it only lets the receipt composer compare the ISO
 * projection of the persisted record against the signed ISO currency without relaxing the equality.</p>
 */
public final class BusinessCurrencyProjection {

    /** Approved channel/business currency token. */
    public static final String BUSINESS_USD = "USD";

    /** Approved ISO 4217 numeric currency code carried in DE49. */
    public static final String ISO_USD = "840";

    private BusinessCurrencyProjection() {
    }

    /**
     * Normalize either approved representation of the simulator currency to ISO {@code 840}.
     * Accepting the ISO form makes the projection idempotent for records that already persist it;
     * it does not admit any additional currency.
     *
     * @throws IllegalArgumentException for any currency other than the approved USD pair
     */
    public static String isoNumeric(String currency) {
        if (BUSINESS_USD.equals(currency) || ISO_USD.equals(currency)) {
            return ISO_USD;
        }
        throw new IllegalArgumentException("unsupported business currency");
    }

    /**
     * @throws IllegalArgumentException for any ISO code other than the approved USD pair
     */
    public static String businessCurrency(String isoNumeric) {
        if (ISO_USD.equals(isoNumeric)) {
            return BUSINESS_USD;
        }
        throw new IllegalArgumentException("unsupported ISO currency");
    }
}
