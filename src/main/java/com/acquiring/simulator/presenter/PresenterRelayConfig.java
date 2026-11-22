package com.acquiring.simulator.presenter;

import java.net.URI;
import java.time.Duration;
import java.util.Objects;

/**
 * Explicit local-demo configuration for the presenter relay.
 *
 * <p>This type exists so the relay cannot be started accidentally against a production boundary.
 * It fails closed unless:</p>
 * <ul>
 *   <li>{@code PRESENTER_MODE=local-demo} is set;</li>
 *   <li>the DynamoDB endpoint is a plain-{@code http} loopback address (DynamoDB Local);</li>
 *   <li>a local demo access code of at least 16 characters is configured; and</li>
 *   <li>the fixed synthetic acquirer/merchant/terminal scope is complete.</li>
 * </ul>
 *
 * <p>The access code is the local test-only stand-in for the approved demo-user sign-in. It is
 * never logged, never sent to the browser and never written to disk by this class.</p>
 */
public record PresenterRelayConfig(
        String mode,
        String environmentId,
        String acquirerRef,
        String merchantRef,
        String terminalRef,
        String demoSubject,
        String demoAccessCode,
        String journalTable,
        String eventTable,
        URI dynamoEndpoint,
        String awsRegion,
        int port,
        Duration sessionLifetime,
        Duration historyWindow) {

    public static final String LOCAL_DEMO_MODE = "local-demo";

    public PresenterRelayConfig {
        Objects.requireNonNull(mode, "mode");
        if (!LOCAL_DEMO_MODE.equals(mode)) {
            throw new IllegalArgumentException(
                    "presenter relay only runs in " + LOCAL_DEMO_MODE + " mode");
        }
        requireId("environmentId", environmentId, 32);
        requireId("acquirerRef", acquirerRef, 128);
        requireId("merchantRef", merchantRef, 128);
        requireId("terminalRef", terminalRef, 128);
        requireId("demoSubject", demoSubject, 64);
        if (demoAccessCode == null || demoAccessCode.length() < 16) {
            throw new IllegalArgumentException(
                    "a local demo access code of at least 16 characters is required");
        }
        requireId("journalTable", journalTable, 255);
        requireId("eventTable", eventTable, 255);
        Objects.requireNonNull(dynamoEndpoint, "dynamoEndpoint");
        if (!"http".equalsIgnoreCase(dynamoEndpoint.getScheme())
                || !isLoopbackHost(dynamoEndpoint.getHost())) {
            throw new IllegalArgumentException(
                    "DynamoDB Local must be reached over plain http on a loopback host");
        }
        if (awsRegion == null || awsRegion.isBlank()) {
            throw new IllegalArgumentException("awsRegion is required");
        }
        if (port < 0 || port > 65535) {
            throw new IllegalArgumentException("port must be 0..65535");
        }
        if (sessionLifetime == null || sessionLifetime.isNegative()
                || sessionLifetime.isZero() || sessionLifetime.compareTo(Duration.ofMinutes(15)) > 0) {
            throw new IllegalArgumentException("sessionLifetime must be positive and at most 15 minutes");
        }
        if (historyWindow == null || historyWindow.isNegative() || historyWindow.isZero()) {
            throw new IllegalArgumentException("historyWindow must be positive");
        }
    }

    /** Reads the configuration from the process environment; missing values fail closed. */
    public static PresenterRelayConfig fromEnvironment() {
        return new PresenterRelayConfig(
                required("PRESENTER_MODE"),
                required("PRESENTER_ENVIRONMENT"),
                required("PRESENTER_ACQUIRER"),
                required("PRESENTER_MERCHANT"),
                required("PRESENTER_TERMINAL"),
                required("PRESENTER_DEMO_SUBJECT"),
                required("PRESENTER_DEMO_ACCESS_CODE"),
                required("JOURNAL_TABLE"),
                required("EVENT_TABLE"),
                URI.create(required("DYNAMODB_ENDPOINT")),
                value("AWS_REGION", "us-east-1"),
                Integer.parseInt(value("PRESENTER_PORT", "8099")),
                Duration.ofMinutes(Long.parseLong(value("PRESENTER_SESSION_MINUTES", "15"))),
                Duration.ofDays(Long.parseLong(value("PRESENTER_HISTORY_DAYS", "7"))));
    }

    static boolean isLoopbackHost(String host) {
        return "127.0.0.1".equals(host) || "localhost".equals(host) || "::1".equals(host);
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    "missing required local-demo configuration: " + name);
        }
        return value.trim();
    }

    private static String value(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static void requireId(String name, String value, int maxLength) {
        if (value == null || value.isBlank() || value.length() > maxLength) {
            throw new IllegalArgumentException(name + " must be 1-" + maxLength + " characters");
        }
    }

    /** Never renders the access code. */
    @Override
    public String toString() {
        return "PresenterRelayConfig[mode=" + mode
                + ", environmentId=" + environmentId
                + ", acquirerRef=" + acquirerRef
                + ", merchantRef=" + merchantRef
                + ", terminalRef=" + terminalRef
                + ", demoSubject=" + demoSubject
                + ", journalTable=" + journalTable
                + ", eventTable=" + eventTable
                + ", port=" + port
                + ", sessionLifetime=" + sessionLifetime
                + ", historyWindow=" + historyWindow + "]";
    }
}
