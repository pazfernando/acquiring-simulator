package com.acquiring.simulator.domain.event;

import java.util.regex.Pattern;

/**
 * Source redaction gate (observation contract O1, requirement R20). Only printable ASCII scalar
 * data is admitted; credential shapes named by the contract are rejected before persistence so a
 * raw JWT, PAN, PEM key or log body can never become event payload.
 */
public final class EventSanitizer {

    private static final Pattern JWT = Pattern.compile("^eyJ[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.");
    private static final Pattern BEARER = Pattern.compile("(?i)bearer\\s+\\S+");
    private static final Pattern PEM = Pattern.compile("-----BEGIN [A-Z ]+-----");
    private static final Pattern PAN_SHAPE = Pattern.compile("(?<!\\d)\\d{13,19}(?!\\d)");
    private static final Pattern PIN_BLOCK = Pattern.compile("(?i)pin\\s*block");

    private EventSanitizer() {
    }

    /** Reject non-scalar or credential-shaped content; the value is never echoed. */
    public static void requireSafe(String field, String value) {
        if (value == null) {
            return;
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c < 0x20 || c > 0x7E) {
                throw new EventValidationException(EventViolation.PROHIBITED_CONTENT, field,
                        "only printable ASCII scalar data is admitted");
            }
        }
        if (JWT.matcher(value).find()
                || BEARER.matcher(value).find()
                || PEM.matcher(value).find()
                || PAN_SHAPE.matcher(value).find()
                || PIN_BLOCK.matcher(value).find()) {
            throw new EventValidationException(EventViolation.PROHIBITED_CONTENT, field,
                    "credential or payment-data shape is not admitted");
        }
    }
}
