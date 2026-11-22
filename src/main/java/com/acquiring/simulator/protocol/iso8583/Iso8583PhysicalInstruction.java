package com.acquiring.simulator.protocol.iso8583;

import java.util.Locale;
import java.util.Objects;

/**
 * Immutable physical-data-element projection of one persisted original
 * SIM-ISO87A-v1 {@code 0100} instruction.
 *
 * <p>The caller copies these values from the exact original {@code 0100}
 * octets that were claimed and dispatched. This type never re-encodes a
 * request, never rebuilds a message from a replay payload and never reads a
 * response. Wire width and alphabet are enforced by {@link Iso8583Scanner} for
 * both sides; this record only requires presence and the 12-digit DE4 bound, so
 * an already-scanned value is projected without normalisation.</p>
 *
 * <p>Scope: the eleven physical elements whose response echo this slice
 * compares. The amount range, USD policy, Gregorian calendar, DE7/{@code sent}
 * consistency, fixture/registry resolution and every other domain rule are
 * deliberately absent and remain later layers.</p>
 *
 * @param de3         DE3 processing code, original value
 * @param amountMinor DE4 minor-unit amount, original value
 * @param de7         DE7 transmission date and time, original value
 * @param stan        DE11 system trace audit number, original value
 * @param de22        DE22 POS entry-mode code, original value
 * @param de25        DE25 POS condition code, original value
 * @param de32        DE32 acquirer alias, original value
 * @param rrn         DE37 retrieval reference number, original value
 * @param de41        DE41 terminal alias, original value
 * @param de42        DE42 merchant alias, original value
 * @param currency    DE49 currency code, original value
 */
public record Iso8583PhysicalInstruction(
        String de3,
        long amountMinor,
        String de7,
        String stan,
        String de22,
        String de25,
        String de32,
        String rrn,
        String de41,
        String de42,
        String currency) {

    /** DE4 width; the profile's encoder zero-formats minor units to this width. */
    public static final int AMOUNT_WIDTH = 12;

    /** Exclusive upper bound of a 12-digit minor-unit amount. */
    public static final long AMOUNT_LIMIT = 1_000_000_000_000L;

    public Iso8583PhysicalInstruction {
        Objects.requireNonNull(de3, "de3");
        Objects.requireNonNull(de7, "de7");
        Objects.requireNonNull(stan, "stan");
        Objects.requireNonNull(de22, "de22");
        Objects.requireNonNull(de25, "de25");
        Objects.requireNonNull(de32, "de32");
        Objects.requireNonNull(rrn, "rrn");
        Objects.requireNonNull(de41, "de41");
        Objects.requireNonNull(de42, "de42");
        Objects.requireNonNull(currency, "currency");
        if (amountMinor < 0 || amountMinor >= AMOUNT_LIMIT) {
            throw new IllegalArgumentException(
                    "amountMinor must fit " + AMOUNT_WIDTH + " digits: " + amountMinor);
        }
    }

    /** @return DE4 as the profile's zero-formatted 12-digit projection */
    public String de4() {
        return String.format(Locale.ROOT, "%0" + AMOUNT_WIDTH + "d", amountMinor);
    }
}
