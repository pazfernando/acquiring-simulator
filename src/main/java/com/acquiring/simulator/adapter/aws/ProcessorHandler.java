package com.acquiring.simulator.adapter.aws;

import com.amazonaws.services.lambda.runtime.*;
import com.acquiring.simulator.domain.AuthorizationService;
import com.acquiring.simulator.domain.model.*;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.function.Function;

/** API Gateway HTTP API payload v2. Identity comes ONLY from its validated JWT authorizer. */
public final class ProcessorHandler implements RequestStreamHandler {
    private final java.util.function.Supplier<AuthorizationService> service;
    private final Function<String, AuthorizedContext> callers;
    public ProcessorHandler() { this(AwsRuntime::processor, AwsRuntime::caller); }
    public ProcessorHandler(java.util.function.Supplier<AuthorizationService> service, Function<String, AuthorizedContext> callers) {
        this.service = service; this.callers = callers;
    }
    private AuthorizationService initialized;
    private synchronized AuthorizationService service() {
        if (initialized == null) initialized = service.get();
        return initialized;
    }
    @Override public void handleRequest(InputStream input, OutputStream output, Context context) throws IOException {
        String response;
        int status;
        try {
            JsonNode event = WireJson.tree(new String(input.readNBytes(65537), StandardCharsets.UTF_8));
            if (!"2.0".equals(event.path("version").asText())
                    || !"POST".equals(event.at("/requestContext/http/method").asText())
                    || !"/authorizations".equals(event.path("rawPath").asText())) {
                status = 404; response = "{\"error\":\"not found\"}";
            } else {
                String subject = event.at("/requestContext/authorizer/jwt/claims/sub").asText("");
                AuthorizedContext caller = null;
                if (!subject.isBlank()) {
                    try { caller = callers.apply(subject); } catch (IllegalArgumentException denied) { /* fail closed */ }
                }
                if (caller == null) { status = 403; response = "{\"error\":\"forbidden\"}"; }
                else if (event.path("isBase64Encoded").asBoolean(false) || !event.path("body").isTextual()) {
                    status = 400; response = "{\"error\":\"invalid request\"}";
                } else {
                    PurchaseRequest request = WireJson.read(event.path("body").asText(), PurchaseRequest.class);
                    response = WireJson.write(service().authorize(caller, request));
                    status = 200;
                }
            }
        } catch (IllegalArgumentException badInput) {
            status = 400; response = "{\"error\":\"invalid request\"}";
        } catch (RuntimeException unavailable) {
            // Do not log payloads, credentials or internal exceptions into the caller response.
            status = 503; response = "{\"error\":\"service unavailable; retry same business reference\"}";
        }
        output.write(WireJson.write(Map.of("statusCode", status, "headers", Map.of("content-type", "application/json"),
                "body", response, "isBase64Encoded", false)).getBytes(StandardCharsets.UTF_8));
    }
}
