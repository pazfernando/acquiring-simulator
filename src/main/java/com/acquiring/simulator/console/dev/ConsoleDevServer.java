package com.acquiring.simulator.console.dev;

import com.acquiring.simulator.channel.ChannelRequestValidator;
import com.acquiring.simulator.console.ConsoleAuthorizationRoute;
import com.acquiring.simulator.console.ConsoleHttpRequest;
import com.acquiring.simulator.console.ConsoleHttpResponse;
import com.acquiring.simulator.console.ConsoleIntegrationEntryPoint;
import com.acquiring.simulator.console.ConsoleOriginVerificationGate;
import com.acquiring.simulator.console.ConsoleRequestIdentity;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Loopback-only HTTP harness for the static console and the verified console route.
 *
 * <p>The listener binds the IPv4 loopback address explicitly and serves nothing else. It performs
 * four jobs and no others:</p>
 * <ol>
 *   <li>serve the static {@code console-ui} files (a browser-visible screen);</li>
 *   <li>serve {@code /config.json} describing the dev-stub mode and the trusted demo context;</li>
 *   <li>mint an unsigned development token at {@code /dev/v1/stub-token};</li>
 *   <li>mount the real {@link ConsoleAuthorizationRoute} at
 *       {@code POST /console/v1/authorizations} behind the real
 *       {@link ConsoleIntegrationEntryPoint}, so a browser submission passes the same
 *       origin-verification gate the deployed Lambda uses rather than a parallel or ungated
 *       branch.</li>
 * </ol>
 *
 * <p>This harness is the local stand-in for the CloudFront edge for that route: it removes any
 * caller-supplied {@code X-Origin-Verify} (whatever the header-name case) and injects the configured
 * value, exactly as the edge overwrites a viewer-supplied origin header. The gate itself is
 * untouched and mandatory; the value never appears in {@code /config.json}, a log line or a
 * response.</p>
 *
 * <p>It adds no CORS headers (the page and API share one origin), requires a loopback {@code Host}
 * and, when a browser sends {@code Origin}, an exact same-origin value. Static paths are resolved
 * under the configured root and only a closed extension list is served. Request bodies are bounded
 * before they reach the route. The harness never logs a request line, header, token or body.</p>
 *
 * <p>This is a development entry point, not a deployment: there is no TLS, no API Gateway, no
 * CloudFront, no WAF, no Keycloak, no mTLS and no AWS call beyond the local DynamoDB endpoint the
 * caller supplies.</p>
 */
public final class ConsoleDevServer implements AutoCloseable {

    /** The only host this harness binds. */
    public static final String BIND_HOST = "127.0.0.1";

    private static final String STUB_TOKEN_PATH = "/dev/v1/stub-token";
    private static final String CONFIG_PATH = "/config.json";
    private static final Set<String> STATIC_EXTENSIONS = Set.of(
            "html", "css", "js", "mjs", "json", "svg", "png", "ico", "webmanifest", "map");
    private static final int BODY_READ_LIMIT = ChannelRequestValidator.MAX_BODY_BYTES + 1;

    private final ConsoleDevConfig config;
    private final Path staticRoot;
    private final ConsoleIntegrationEntryPoint entryPoint;
    private final String edgeOriginVerify;
    private final DevStubTokenIssuer tokenIssuer;
    private final ObjectMapper json = new ObjectMapper();
    private final HttpServer server;
    private final ExecutorService executor;
    private final int port;

    public ConsoleDevServer(ConsoleDevConfig config, Path staticRoot,
            ConsoleIntegrationEntryPoint entryPoint, String edgeOriginVerify,
            DevStubTokenIssuer tokenIssuer) throws IOException {
        this.config = Objects.requireNonNull(config, "config");
        this.staticRoot = Objects.requireNonNull(staticRoot, "staticRoot")
                .toAbsolutePath().normalize();
        this.entryPoint = Objects.requireNonNull(entryPoint, "entryPoint");
        this.edgeOriginVerify = Objects.requireNonNull(edgeOriginVerify, "edgeOriginVerify");
        if (edgeOriginVerify.isBlank()) {
            throw new IllegalArgumentException("the local edge requires the origin verify value");
        }
        this.tokenIssuer = Objects.requireNonNull(tokenIssuer, "tokenIssuer");

        InetSocketAddress address = new InetSocketAddress(
                InetAddress.getByName(BIND_HOST), config.port());
        this.server = HttpServer.create(address, 0);
        this.port = server.getAddress().getPort();
        this.executor = Executors.newFixedThreadPool(4, runnable -> {
            Thread thread = new Thread(runnable, "console-dev-http");
            thread.setDaemon(true);
            return thread;
        });
        server.setExecutor(executor);
        server.createContext("/", this::handle);
    }

    /** Begin accepting loopback connections. Binding already happened in the constructor. */
    public void start() {
        server.start();
    }

    /** The actual bound port, which differs from the configured one when port 0 was requested. */
    public int port() {
        return port;
    }

    /** The exact URL a person should open. */
    public String url() {
        return "http://" + BIND_HOST + ":" + port + "/";
    }

    @Override
    public void close() {
        server.stop(0);
        executor.shutdownNow();
    }

    // ------------------------------------------------------------------ routing

    private void handle(HttpExchange exchange) throws IOException {
        try {
            addSecurityHeaders(exchange.getResponseHeaders());

            if (!loopbackHostAllowed(exchange)) {
                sendEmpty(exchange, 403);
                return;
            }

            String path = exchange.getRequestURI().getPath();
            if (path == null || path.isEmpty()) {
                sendEmpty(exchange, 400);
                return;
            }

            if (CONFIG_PATH.equals(path)) {
                if (!requireMethod(exchange, "GET")) {
                    return;
                }
                sendJson(exchange, 200, runtimeConfig());
                return;
            }

            if (STUB_TOKEN_PATH.equals(path)) {
                if (!requireMethod(exchange, "POST")) {
                    return;
                }
                if (!sameOriginAllowed(exchange)) {
                    sendEmpty(exchange, 403);
                    return;
                }
                issueStubToken(exchange);
                return;
            }

            if (ConsoleAuthorizationRoute.PATH.equals(path)) {
                if (!requireMethod(exchange, ConsoleAuthorizationRoute.METHOD)) {
                    return;
                }
                if (!sameOriginAllowed(exchange)) {
                    sendEmpty(exchange, 403);
                    return;
                }
                submit(exchange);
                return;
            }

            serveStatic(exchange, path);
        } catch (RuntimeException failure) {
            // No detail is echoed: the harness must not turn a local defect into a credential or
            // payload disclosure. The terminal has the operator's own stack trace if they want it.
            if (exchange.getResponseCode() == -1) {
                sendEmpty(exchange, 500);
            }
        } finally {
            exchange.close();
        }
    }

    private void issueStubToken(HttpExchange exchange) throws IOException {
        DevStubTokenIssuer.IssuedToken issued = tokenIssuer.issue();
        ObjectNode body = json.createObjectNode();
        body.put("accessToken", issued.accessToken());
        body.put("expiresIn", issued.expiresInSeconds());
        body.put("tokenType", "dev-stub");
        body.put("stub", true);
        sendJson(exchange, 200, body);
    }

    private void submit(HttpExchange exchange) throws IOException {
        byte[] body = readAtMost(exchange.getRequestBody(), BODY_READ_LIMIT);

        Map<String, List<String>> headers = new LinkedHashMap<>();
        exchange.getRequestHeaders().forEach((name, values) ->
                headers.put(name, List.copyOf(values)));
        // The local edge replaces any viewer-supplied origin header. The gate stays mandatory.
        headers.keySet().removeIf(name ->
                name.equalsIgnoreCase(ConsoleOriginVerificationGate.HEADER_NAME));
        headers.put(ConsoleOriginVerificationGate.HEADER_NAME, List.of(edgeOriginVerify));

        ConsoleHttpRequest request = new ConsoleHttpRequest(
                ConsoleAuthorizationRoute.METHOD,
                ConsoleAuthorizationRoute.PATH,
                headers,
                body);
        ConsoleHttpResponse response = entryPoint.handle(request, ConsoleRequestIdentity.UNKNOWN);

        Headers target = exchange.getResponseHeaders();
        response.headers().forEach((name, values) ->
                target.put(name, List.copyOf(values)));

        byte[] out = response.body();
        if (out.length == 0) {
            exchange.sendResponseHeaders(response.status(), -1);
            return;
        }
        target.set("Content-Length", Integer.toString(out.length));
        exchange.sendResponseHeaders(response.status(), out.length);
        try (OutputStream stream = exchange.getResponseBody()) {
            stream.write(out);
        }
    }

    // ------------------------------------------------------------------ static files

    private void serveStatic(HttpExchange exchange, String path) throws IOException {
        String method = exchange.getRequestMethod();
        if (!"GET".equals(method) && !"HEAD".equals(method)) {
            sendEmpty(exchange, 405);
            return;
        }

        String relative = "/".equals(path) ? "index.html" : path.substring(1);
        if (relative.isEmpty()
                || relative.startsWith(".")
                || relative.contains("..")
                || relative.indexOf('\0') >= 0
                || relative.indexOf('\\') >= 0) {
            sendEmpty(exchange, 404);
            return;
        }

        Path candidate = staticRoot.resolve(relative).normalize();
        if (!candidate.startsWith(staticRoot) || !Files.isRegularFile(candidate)) {
            sendEmpty(exchange, 404);
            return;
        }

        String contentType = contentTypeOf(relative);
        if (contentType == null) {
            sendEmpty(exchange, 404);
            return;
        }

        byte[] bytes = Files.readAllBytes(candidate);
        Headers headers = exchange.getResponseHeaders();
        headers.set("Content-Type", contentType);
        if ("HEAD".equals(method)) {
            exchange.sendResponseHeaders(200, -1);
            return;
        }
        headers.set("Content-Length", Integer.toString(bytes.length));
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream stream = exchange.getResponseBody()) {
            stream.write(bytes);
        }
    }

    private static String contentTypeOf(String relative) {
        int dot = relative.lastIndexOf('.');
        if (dot < 0) {
            return null;
        }
        String extension = relative.substring(dot + 1).toLowerCase(Locale.ROOT);
        if (!STATIC_EXTENSIONS.contains(extension)) {
            return null;
        }
        return switch (extension) {
            case "html" -> "text/html; charset=utf-8";
            case "css" -> "text/css; charset=utf-8";
            case "js", "mjs" -> "text/javascript; charset=utf-8";
            case "json", "map" -> "application/json; charset=utf-8";
            case "svg" -> "image/svg+xml";
            case "png" -> "image/png";
            case "ico" -> "image/x-icon";
            case "webmanifest" -> "application/manifest+json";
            default -> null;
        };
    }

    // ------------------------------------------------------------------ config view

    private ObjectNode runtimeConfig() {
        ObjectNode root = json.createObjectNode();
        root.put("mode", "dev-stub");
        root.put("stub", true);
        root.put("issuer", config.issuer());
        root.put("clientId", config.clientId());
        root.put("audience", config.audience());
        root.put("scope", config.scope());
        root.put("redirectUri", url());
        root.put("tokenEndpoint", STUB_TOKEN_PATH);

        ObjectNode context = root.putObject("demoContext");
        context.put("acquirerRef", config.acquirerRef());
        context.put("merchantRef", config.merchantRef());
        context.put("terminalRef", config.terminalRef());
        context.put("currency", config.currency());

        ObjectNode history = root.putObject("history");
        history.put("mode", "session-only");
        history.put("serverReadImplemented", false);

        return root;
    }

    // ------------------------------------------------------------------ guards

    private boolean loopbackHostAllowed(HttpExchange exchange) {
        List<String> hosts = exchange.getRequestHeaders().get("Host");
        if (hosts == null || hosts.size() != 1) {
            return false;
        }
        String host = hosts.get(0);
        return host.equals(BIND_HOST + ":" + port) || host.equals("localhost:" + port);
    }

    /**
     * A browser POST is same-origin when {@code Origin} equals this exact loopback origin. A
     * non-browser client that omits the header is allowed only because loopback binding and the
     * {@code Host} check already scope the listener; no CORS response is ever emitted.
     */
    private boolean sameOriginAllowed(HttpExchange exchange) {
        List<String> origins = exchange.getRequestHeaders().get("Origin");
        if (origins == null || origins.isEmpty()) {
            return true;
        }
        if (origins.size() != 1) {
            return false;
        }
        String origin = origins.get(0);
        return origin.equals("http://" + BIND_HOST + ":" + port)
                || origin.equals("http://localhost:" + port);
    }

    private static boolean requireMethod(HttpExchange exchange, String method) throws IOException {
        if (method.equals(exchange.getRequestMethod())) {
            return true;
        }
        exchange.getResponseHeaders().set("Allow", method);
        exchange.sendResponseHeaders(405, -1);
        return false;
    }

    private static void addSecurityHeaders(Headers headers) {
        headers.set("Content-Security-Policy",
                "default-src 'none'; script-src 'self'; style-src 'self'; img-src 'self' data:; "
                        + "connect-src 'self'; form-action 'none'; base-uri 'none'; "
                        + "frame-ancestors 'none'");
        headers.set("X-Content-Type-Options", "nosniff");
        headers.set("X-Frame-Options", "DENY");
        headers.set("Referrer-Policy", "no-referrer");
        headers.set("Cross-Origin-Opener-Policy", "same-origin");
        headers.set("Cross-Origin-Resource-Policy", "same-origin");
        headers.set("Cache-Control", "no-store");
    }

    private static void sendJson(HttpExchange exchange, int status, ObjectNode body)
            throws IOException {
        byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
        Headers headers = exchange.getResponseHeaders();
        headers.set("Content-Type", "application/json; charset=utf-8");
        headers.set("Content-Length", Integer.toString(bytes.length));
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream stream = exchange.getResponseBody()) {
            stream.write(bytes);
        }
    }

    private static void sendEmpty(HttpExchange exchange, int status) throws IOException {
        exchange.sendResponseHeaders(status, -1);
    }

    private static byte[] readAtMost(InputStream input, int limit) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(Math.min(limit, 4096));
        byte[] buffer = new byte[1024];
        int total = 0;
        while (total < limit) {
            int read = input.read(buffer, 0, Math.min(buffer.length, limit - total));
            if (read < 0) {
                break;
            }
            out.write(buffer, 0, read);
            total += read;
        }
        return out.toByteArray();
    }
}
