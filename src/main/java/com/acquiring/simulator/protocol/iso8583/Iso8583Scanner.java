package com.acquiring.simulator.protocol.iso8583;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Strict structural scanner for one SIM-ISO87A-v1 body.
 *
 * <p>Assembles the existing header parser and field cursor in the P6 structural
 * order: header (MTI + primary bitmap), required/forbidden presence for the MTI,
 * ascending bitmap-driven field framing/alphabet, then exact end-of-body. It
 * produces an immutable {@link Iso8583Message}.</p>
 *
 * <p>Presence comes from the P1 matrix: the 0100 request requires
 * DE3/4/7/11/22/25/32/37/41/42/48/49 and forbids DE38/DE39; the 0110 response
 * requires DE3/4/7/11/22/25/32/37/39/41/42/48/49. DE38 on a 0110 is conditional
 * on trusted issuer evidence, so it is structurally allowed and never required
 * here; a caller-supplied presence flag cannot decide it.</p>
 *
 * <p>Scope: framing, bounds, per-field alphabet, body exactness. No DE48
 * canonical key=value schema, no DE38 evidence semantics, no amount, date,
 * currency, fixture, freshness, 826/999 admission, binding, dispatch or response
 * construction. Those checks belong to later layers and must not be inferred
 * from a successful scan.</p>
 */
public final class Iso8583Scanner {

    /** DEs required in a 0100 request body. */
    public static final Set<Integer> REQUEST_REQUIRED_FIELDS =
            Set.of(3, 4, 7, 11, 22, 25, 32, 37, 41, 42, 48, 49);

    /** DEs forbidden in a 0100 request body. */
    public static final Set<Integer> REQUEST_FORBIDDEN_FIELDS = Set.of(38, 39);

    /** DEs required in a 0110 response body; DE38 stays conditional. */
    public static final Set<Integer> RESPONSE_REQUIRED_FIELDS =
            Set.of(3, 4, 7, 11, 22, 25, 32, 37, 39, 41, 42, 48, 49);

    /** DEs forbidden in a 0110 response body. */
    public static final Set<Integer> RESPONSE_FORBIDDEN_FIELDS = Set.of();

    private Iso8583Scanner() {
    }

    /**
     * Scan a complete ASCII body.
     *
     * @throws Iso8583HeaderParseException on MTI/bitmap/disabled-field failures
     * @throws Iso8583PresenceException on the MTI presence matrix
     * @throws Iso8583FieldException on field framing, bounds, alphabet or
     *         trailing octets
     */
    public static Iso8583Message scan(String body) {
        Iso8583Header header = Iso8583HeaderParser.parse(body);
        requirePresence(header);

        Iso8583FieldReader reader =
                Iso8583FieldReader.startingAt(body, header.headerLength());
        Map<Integer, String> fields = new LinkedHashMap<>();
        for (int de : header.bitmap().setFields()) {
            fields.put(de, Iso8583DataElement.forDe(de).read(reader));
        }
        reader.requireEnd();

        return new Iso8583Message(
                header.mti(), header.bitmap(), header.headerLength(), body.length(), fields);
    }

    private static void requirePresence(Iso8583Header header) {
        Set<Integer> required = requiredFields(header.mti());
        Set<Integer> forbidden = forbiddenFields(header.mti());

        for (int de = 1; de <= 64; de++) {
            if (required.contains(de) && !header.bitmap().isSet(de)) {
                throw new Iso8583PresenceException(
                        Iso8583PresenceException.Rule.MISSING_REQUIRED_FIELD,
                        de,
                        "MTI " + header.mti() + " requires DE" + de);
            }
        }
        for (int de = 1; de <= 64; de++) {
            if (forbidden.contains(de) && header.bitmap().isSet(de)) {
                throw new Iso8583PresenceException(
                        Iso8583PresenceException.Rule.FORBIDDEN_FIELD_PRESENT,
                        de,
                        "MTI " + header.mti() + " forbids DE" + de);
            }
        }
    }

    private static Set<Integer> requiredFields(String mti) {
        return switch (mti) {
            case "0100" -> REQUEST_REQUIRED_FIELDS;
            case "0110" -> RESPONSE_REQUIRED_FIELDS;
            default -> throw new IllegalStateException(
                    "header parser accepted an unsupported MTI: " + mti);
        };
    }

    private static Set<Integer> forbiddenFields(String mti) {
        return switch (mti) {
            case "0100" -> REQUEST_FORBIDDEN_FIELDS;
            case "0110" -> RESPONSE_FORBIDDEN_FIELDS;
            default -> throw new IllegalStateException(
                    "header parser accepted an unsupported MTI: " + mti);
        };
    }
}
