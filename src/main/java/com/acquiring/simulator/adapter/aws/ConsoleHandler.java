package com.acquiring.simulator.adapter.aws;

import com.acquiring.simulator.console.ConsoleHttpRequest;
import com.acquiring.simulator.console.ConsoleHttpResponse;
import com.acquiring.simulator.console.ConsoleIntegrationEntryPoint;
import com.acquiring.simulator.console.ConsoleRequestIdentity;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestStreamHandler;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * The deployed console Lambda: API Gateway HTTP API payload v2 in, the N-02 origin-verification
 * gate first, an HTTP payload v2 response out.
 *
 * <p>The handler class path is the one the authored Terraform already pins
 * ({@code com.acquiring.simulator.adapter.aws.ConsoleHandler::handleRequest}). Construction resolves
 * {@link ConsoleRuntime}, which reads {@code EDGE_ORIGIN_VERIFY} through
 * {@link com.acquiring.simulator.console.ConsoleOriginVerificationGate#fromEnvironment()}. A
 * missing, empty, short or out-of-alphabet value throws here, so the function never serves a
 * request unverified; that failure is a platform 5xx, which is the alertable signal the contract
 * delta requires. There is no constructor, environment value or request field that bypasses the
 * gate.</p>
 *
 * <p>The adapter only translates transport. It does not authenticate, does not decide on a path
 * before the gate and adds no route: method, path, headers, query and body are mapped onto
 * {@link ConsoleHttpRequest}, and every response is whatever
 * {@link ConsoleIntegrationEntryPoint#handle} returned. Platform request id and source IP are read
 * only for the denial record; a missing value stays {@code null} rather than being invented.</p>
 */
public final class ConsoleHandler implements RequestStreamHandler {

    private final ConsoleIntegrationEntryPoint entryPoint;

    /** Lambda entry point. Fails initialization when the gate configuration is unusable. */
    public ConsoleHandler() {
        this(() -> ConsoleRuntime.consoleEntryPoint());
    }

    /** Resolver seam: the same cold start, with the environment read from an explicit source. */
    ConsoleHandler(Function<String, String> environment) {
        this(() -> ConsoleRuntime.consoleEntryPoint(environment));
    }

    /** Explicit composition, used by tests and by a future adapter that already holds one. */
    ConsoleHandler(ConsoleIntegrationEntryPoint entryPoint) {
        this.entryPoint = Objects.requireNonNull(entryPoint, "console entry point");
    }

    private ConsoleHandler(java.util.function.Supplier<ConsoleIntegrationEntryPoint> composition) {
        this.entryPoint = Objects.requireNonNull(composition.get(), "console entry point");
    }

    @Override
    public void handleRequest(InputStream input, OutputStream output, Context context)
            throws IOException {
        JsonNode event;
        ConsoleHttpRequest request;
        try {
            event = WireJson.tree(new String(input.readAllBytes(), StandardCharsets.UTF_8));
            request = requestOf(event);
        } catch (IllegalArgumentException malformed) {
            // Transport-level refusal before a request can be formed. No detail is echoed.
            write(output, ConsoleHttpResponse.empty(400));
            return;
        }
        ConsoleHttpResponse response;
        try {
            response = entryPoint.handle(request, identityOf(event));
        } catch (RuntimeException unavailable) {
            // A construction or application failure is a 5xx, never a permit. No payload is echoed,
            // and the fixed response does not depend on any exception text.
            response = ConsoleHttpResponse.empty(500);
        }
        write(output, response);
    }

    // ------------------------------------------------------------------ request mapping

    private static ConsoleHttpRequest requestOf(JsonNode event) {
        JsonNode http = event.path("requestContext").path("http");
        String method = http.path("method").asText(null);
        String path = event.path("rawPath").asText(null);
        if (method == null || method.isBlank() || path == null || path.isBlank()) {
            throw new IllegalArgumentException("not an HTTP API payload v2 request");
        }
        return new ConsoleHttpRequest(method, path, headersOf(event.path("headers")),
                bodyOf(event), queryOf(event));
    }

    /** The v2 {@code headers} object; an array value is not a v2 shape and is refused. */
    private static Map<String, List<String>> headersOf(JsonNode headers) {
        Map<String, List<String>> mapped = new LinkedHashMap<>();
        if (headers == null || !headers.isObject()) {
            return mapped;
        }
        headers.fields().forEachRemaining(field -> {
            JsonNode value = field.getValue();
            if (!value.isTextual()) {
                throw new IllegalArgumentException("a v2 header value is textual");
            }
            mapped.put(field.getKey(), List.of(value.asText()));
        });
        return mapped;
    }

    private static byte[] bodyOf(JsonNode event) {
        JsonNode body = event.path("body");
        if (body.isMissingNode() || body.isNull()) {
            return new byte[0];
        }
        if (!body.isTextual()) {
            throw new IllegalArgumentException("a v2 body is textual or absent");
        }
        String text = body.asText();
        if (!event.path("isBase64Encoded").asBoolean(false)) {
            return text.getBytes(StandardCharsets.UTF_8);
        }
        try {
            return Base64.getDecoder().decode(text);
        } catch (IllegalArgumentException notBase64) {
            throw new IllegalArgumentException("declared base64 body is not base64", notBase64);
        }
    }

    /**
     * The v2 raw query string, parsed without percent-decoding and with duplicates preserved so
     * the history route's own closed validation sees exactly what the caller sent. The route's
     * 4096-byte bound is applied here too rather than accepting an unbounded target.
     */
    private static Map<String, List<String>> queryOf(JsonNode event) {
        String raw = event.path("rawQueryString").asText("");
        Map<String, List<String>> query = new LinkedHashMap<>();
        if (raw.isEmpty()) {
            return query;
        }
        if (raw.length() > 4096) {
            throw new IllegalArgumentException("query target exceeds the accepted bound");
        }
        for (String pair : raw.split("&", -1)) {
            if (pair.isEmpty()) {
                // An empty pair is malformed input the route refuses; keep it visible.
                throw new IllegalArgumentException("empty query pair");
            }
            int equals = pair.indexOf('=');
            String name = equals < 0 ? pair : pair.substring(0, equals);
            String value = equals < 0 ? "" : pair.substring(equals + 1);
            query.computeIfAbsent(name, ignored -> new ArrayList<>()).add(value);
        }
        return query;
    }

    private static ConsoleRequestIdentity identityOf(JsonNode event) {
        return new ConsoleRequestIdentity(
                event.path("requestContext").path("requestId").asText(null),
                event.path("requestContext").path("http").path("sourceIp").asText(null));
    }

    // ------------------------------------------------------------------ response mapping

    private static void write(OutputStream output, ConsoleHttpResponse response)
            throws IOException {
        Map<String, String> headers = new LinkedHashMap<>();
        response.headers().forEach((name, values) ->
                headers.put(name, String.join(", ", values)));
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("statusCode", response.status());
        payload.put("headers", headers);
        payload.put("body", new String(response.body(), StandardCharsets.UTF_8));
        payload.put("isBase64Encoded", false);
        output.write(WireJson.write(payload).getBytes(StandardCharsets.UTF_8));
    }
}
