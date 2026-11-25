package com.acquiring.simulator.console.dev;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Configuration for the local console development harness.
 *
 * <p>Every value is a local, synthetic default or an explicit command-line/environment override.
 * There is no deployment value, no account, no region credential and no issuer secret here; the
 * issuer string is deliberately a non-resolvable {@code .invalid} host so it cannot be mistaken
 * for a real realm.</p>
 *
 * <p>The browser receives only the same-origin {@code /config.json} view: mode, demo context and
 * the stub token endpoint. It never receives a service secret, an AWS credential or a signing
 * key because none exists in this harness.</p>
 */
public record ConsoleDevConfig(
        int port,
        String dynamoEndpoint,
        String awsRegion,
        String staticRoot,
        String environmentId,
        String issuer,
        String audience,
        String clientId,
        String scope,
        String subject,
        String acquirerRef,
        String merchantRef,
        String terminalRef,
        String currency,
        String journalTable,
        String eventTable) {

    /** Non-resolvable issuer label used only to exercise the local claim policy. */
    public static final String DEV_ISSUER = "https://auth.dev.invalid/realms/console-dev-stub";

    public ConsoleDevConfig {
        if (port < 0 || port > 65535) {
            throw new IllegalArgumentException("port must be between 0 and 65535");
        }
        Objects.requireNonNull(dynamoEndpoint, "dynamoEndpoint");
        Objects.requireNonNull(awsRegion, "awsRegion");
        Objects.requireNonNull(staticRoot, "staticRoot");
        requireText(environmentId, "environmentId");
        requireText(issuer, "issuer");
        requireText(audience, "audience");
        requireText(clientId, "clientId");
        requireText(scope, "scope");
        requireText(subject, "subject");
        requireText(acquirerRef, "acquirerRef");
        requireText(merchantRef, "merchantRef");
        requireText(terminalRef, "terminalRef");
        requireText(currency, "currency");
        requireText(journalTable, "journalTable");
        requireText(eventTable, "eventTable");
    }

    /** Defaults, then environment, then explicit {@code --key=value} arguments (last wins). */
    public static ConsoleDevConfig fromArgs(String[] args, Map<String, String> environment) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("port", environment.getOrDefault("CONSOLE_DEV_PORT", "8080"));
        values.put("dynamodb",
                environment.getOrDefault("DYNAMODB_ENDPOINT", "http://127.0.0.1:8000"));
        values.put("region", environment.getOrDefault("AWS_REGION", "us-east-1"));
        values.put("static-root", environment.getOrDefault("CONSOLE_UI_ROOT", "console-ui"));
        values.put("environment",
                environment.getOrDefault("CONSOLE_DEV_ENVIRONMENT", "SIM-LOCAL"));
        values.put("issuer", environment.getOrDefault("CONSOLE_DEV_ISSUER", DEV_ISSUER));
        values.put("audience", environment.getOrDefault("CONSOLE_DEV_AUDIENCE", "console-api"));
        values.put("client", environment.getOrDefault("CONSOLE_DEV_CLIENT", "console-demo"));
        values.put("scope", environment.getOrDefault("CONSOLE_DEV_SCOPE", "console.demo"));
        values.put("subject", environment.getOrDefault("CONSOLE_DEV_SUBJECT", "demo-user"));
        values.put("acquirer", environment.getOrDefault("CONSOLE_DEV_ACQUIRER", "ACQ-SIM-001"));
        values.put("merchant", environment.getOrDefault("CONSOLE_DEV_MERCHANT", "MERCH-SIM-001"));
        values.put("terminal", environment.getOrDefault("CONSOLE_DEV_TERMINAL", "TERM-SIM-001"));
        values.put("currency", environment.getOrDefault("CONSOLE_DEV_CURRENCY", "USD"));
        values.put("journal-table",
                environment.getOrDefault("CONSOLE_DEV_JOURNAL_TABLE", "console-dev-journal"));
        values.put("event-table",
                environment.getOrDefault("CONSOLE_DEV_EVENT_TABLE", "console-dev-events"));

        if (args != null) {
            for (String argument : args) {
                if (!argument.startsWith("--") || !argument.contains("=")) {
                    throw new IllegalArgumentException(
                            "expected --key=value, got: " + argument);
                }
                int split = argument.indexOf('=');
                String key = argument.substring(2, split);
                String value = argument.substring(split + 1);
                if (!values.containsKey(key)) {
                    throw new IllegalArgumentException("unknown option --" + key);
                }
                values.put(key, value);
            }
        }

        return new ConsoleDevConfig(
                parseInt(values.get("port")),
                values.get("dynamodb"),
                values.get("region"),
                values.get("static-root"),
                values.get("environment"),
                values.get("issuer"),
                values.get("audience"),
                values.get("client"),
                values.get("scope"),
                values.get("subject"),
                values.get("acquirer"),
                values.get("merchant"),
                values.get("terminal"),
                values.get("currency"),
                values.get("journal-table"),
                values.get("event-table"));
    }

    private static int parseInt(String value) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException invalid) {
            throw new IllegalArgumentException("port is not an integer: " + value, invalid);
        }
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("console dev config requires " + name);
        }
    }
}
