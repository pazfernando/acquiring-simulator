package com.acquiring.simulator.console;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * The single structured application record emitted for one refused origin-verification request.
 *
 * <p>The closed field set is exactly the one named by the N-02 contract. It deliberately holds no
 * received value, expected value, length, hash, comparison artifact, {@code Authorization} value or
 * request body. {@code host}, {@code httpMethod} and {@code path} are caller-controlled, so they are
 * length-bounded and character-checked before they are placed here; an unusable value is replaced by
 * {@link #INVALID}.</p>
 */
public record ConsoleOriginDenial(
        String event,
        String reason,
        String requestId,
        String sourceIp,
        String host,
        String httpMethod,
        String path,
        String timestamp,
        String appVersion) {

    /** The fixed event name that the metric filter turns into ConsoleOriginVerificationDenied. */
    public static final String EVENT = "console_origin_verification_denied";

    /** Replacement for a caller-controlled value that fails validation. */
    public static final String INVALID = "invalid";

    private static final int MAX_HOST = 255;
    private static final int MAX_METHOD = 16;
    private static final int MAX_PATH = 2048;

    public ConsoleOriginDenial {
        if (!EVENT.equals(event)) {
            throw new IllegalArgumentException("event must be " + EVENT);
        }
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(timestamp, "timestamp");
        Objects.requireNonNull(appVersion, "appVersion");
    }

    /** Build the record from one refused request and adapter-supplied identity. */
    public static ConsoleOriginDenial of(ConsoleOriginVerificationGate.Reason reason,
            ConsoleRequestIdentity identity, String host, String httpMethod, String path,
            String timestamp, String appVersion) {
        Objects.requireNonNull(reason, "reason");
        ConsoleRequestIdentity requestIdentity = identity == null
                ? ConsoleRequestIdentity.UNKNOWN : identity;
        return new ConsoleOriginDenial(EVENT, reason.name(), requestIdentity.requestId(),
                requestIdentity.sourceIp(), bounded(host, MAX_HOST), bounded(httpMethod, MAX_METHOD),
                bounded(path, MAX_PATH), timestamp, appVersion);
    }

    /**
     * Accept only printable ASCII up to {@code limit} characters. Anything else is replaced, never
     * partially echoed: these values originate from the caller.
     */
    private static String bounded(String value, int limit) {
        if (value == null || value.isEmpty() || value.length() > limit) {
            return INVALID;
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c < 0x20 || c > 0x7e) {
                return INVALID;
            }
        }
        return value;
    }

    /** Field order used by the standard-stream sink. */
    public Map<String, String> fields() {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("event", event);
        fields.put("reason", reason);
        fields.put("requestId", requestId);
        fields.put("sourceIp", sourceIp);
        fields.put("host", host);
        fields.put("httpMethod", httpMethod);
        fields.put("path", path);
        fields.put("timestamp", timestamp);
        fields.put("appVersion", appVersion);
        return fields;
    }
}
