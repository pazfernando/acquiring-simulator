package com.acquiring.simulator.console;

import java.io.PrintStream;
import java.util.Map;
import java.util.Objects;

/**
 * Writes one compact JSON object per refusal to a stream (standard output in a Lambda runtime).
 *
 * <p>It renders only the closed field set of {@link ConsoleOriginDenial}; there is no overload that
 * accepts a request, header or body, so the forbidden values cannot reach the output through this
 * type. A failure in the stream is propagated for the caller to contain, and the response is still
 * the fixed {@code 403}.</p>
 */
public final class StandardStreamOriginDenialSink implements ConsoleOriginDenialSink {

    private final PrintStream out;

    public StandardStreamOriginDenialSink(PrintStream out) {
        this.out = Objects.requireNonNull(out, "out");
    }

    @Override
    public void emit(ConsoleOriginDenial denial) {
        Objects.requireNonNull(denial, "denial");
        StringBuilder json = new StringBuilder(256).append('{');
        boolean first = true;
        for (Map.Entry<String, String> field : denial.fields().entrySet()) {
            if (!first) {
                json.append(',');
            }
            first = false;
            quoteField(json, field.getKey());
            json.append(':');
            if (field.getValue() == null) {
                json.append("null");
            } else {
                quoteField(json, field.getValue());
            }
        }
        json.append('}');
        out.println(json);
        out.flush();
    }

    private static void quoteField(StringBuilder json, String value) {
        json.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> json.append("\\\"");
                case '\\' -> json.append("\\\\");
                case '\b' -> json.append("\\b");
                case '\f' -> json.append("\\f");
                case '\n' -> json.append("\\n");
                case '\r' -> json.append("\\r");
                case '\t' -> json.append("\\t");
                default -> {
                    if (c < 0x20) {
                        json.append(String.format(java.util.Locale.ROOT, "\\u%04x", (int) c));
                    } else {
                        json.append(c);
                    }
                }
            }
        }
        json.append('"');
    }
}
