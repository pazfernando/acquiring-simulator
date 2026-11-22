package com.acquiring.simulator.presenter;

import com.acquiring.simulator.channel.C3Response;
import com.acquiring.simulator.channel.C3ResponseBuilder;
import com.acquiring.simulator.channel.ChannelAuthorizationRequest;
import com.acquiring.simulator.channel.ChannelRequestValidationException;
import com.acquiring.simulator.channel.ChannelRequestValidator;
import com.acquiring.simulator.domain.event.EventContext;
import com.acquiring.simulator.domain.event.EventHead;
import com.acquiring.simulator.domain.event.RecordedEvent;
import com.acquiring.simulator.domain.model.AuthorizationRecord;
import com.acquiring.simulator.domain.model.AuthorizationResult;
import com.acquiring.simulator.domain.model.AuthorizationState;
import com.acquiring.simulator.domain.model.AuthorizedContext;
import com.acquiring.simulator.domain.model.Outcome;
import com.acquiring.simulator.domain.model.PurchaseRequest;
import com.acquiring.simulator.domain.model.Route;
import com.acquiring.simulator.domain.port.EventHistoryReader;
import com.acquiring.simulator.domain.port.EventStore;
import com.acquiring.simulator.domain.port.TransactionLister;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Local loopback relay for the private presenter page (WI-ACQ-OBS-001 T6 slice).
 *
 * <p>It serves the existing presenter assets, an opaque HttpOnly session and one genuine
 * authorization submission that runs the real channel validator, the real atomic claim /
 * dispatch-intent path, the local signed issuer and the real signed-evidence gateway. It serves
 * event catch-up from the same event rows the append path wrote.</p>
 *
 * <p>The live route is a real loopback WebSocket: the same listener performs the RFC 6455 upgrade
 * after the loopback Host/Origin and session-cookie checks, and hands the connection to
 * {@link PresenterLiveHub}. That hub is an in-process fixture for Streams plus notifier; it is
 * notification-only and never takes part in a payment decision.</p>
 */
public final class PresenterRelayServer implements AutoCloseable {

    private static final String SESSION_COOKIE = "PRESENTER_SESSION";
    private static final int MAX_REQUEST_BYTES = ChannelRequestValidator.MAX_BODY_BYTES;

    private final PresenterRelayConfig config;
    private final PresenterSessions sessions;
    private final PresenterAccessPolicy accessPolicy;
    private final com.acquiring.simulator.domain.AuthorizationService service;
    private final com.acquiring.simulator.adapter.aws.DynamoAuthorizationRepository repository;
    private final EventStore store;
    private final EventHistoryReader history;
    private final TransactionLister lister;
    private final Clock clock;
    private final ObjectMapper json;
    private final LocalRelayHttpServer server;
    private final PresenterLiveHub live;
    private final byte[] indexHtml;
    private final byte[] appJs;
    private final byte[] appCss;
    private final AtomicLong submissions = new AtomicLong();

    public PresenterRelayServer(PresenterRelayConfig config,
            com.acquiring.simulator.domain.AuthorizationService service,
            com.acquiring.simulator.adapter.aws.DynamoAuthorizationRepository repository,
            EventStore store, EventHistoryReader history, TransactionLister lister, Clock clock)
            throws IOException {
        this.config = config;
        this.service = service;
        this.repository = repository;
        this.store = store;
        this.history = history;
        this.lister = lister;
        this.clock = clock;
        this.sessions = new PresenterSessions(clock, config.sessionLifetime(),
                config.demoSubject(), config.demoAccessCode());
        this.json = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        this.indexHtml = asset("index.html");
        this.appJs = asset("app.js");
        this.appCss = asset("app.css");

        InetSocketAddress address = new InetSocketAddress(
                InetAddress.getLoopbackAddress(), config.port());
        this.live = new PresenterLiveHub(store, history, json, config.environmentId());
        this.server = new LocalRelayHttpServer(address, this::handle);
        this.accessPolicy = new PresenterAccessPolicy(server.port());
        server.setUpgradeHandler(this::upgradeLive);
    }

    public void start() {
        server.start();
    }

    public int port() {
        return server.port();
    }

    public long submissionCount() {
        return submissions.get();
    }

    /** Observation seam for the local runtime: registered on the event store after construction. */
    PresenterLiveHub liveHub() {
        return live;
    }

    @Override
    public void close() {
        live.close();
        server.close();
    }

    /* ------------------------------ routing ------------------------------ */

    private void handle(HttpExchange exchange) throws IOException {
        try {
            securityHeaders(exchange);
            String host = exchange.getRequestHeaders().getFirst("Host");
            if (!accessPolicy.hostAllowed(host)) {
                sendJson(exchange, 421, Map.of("error", "HOST_NOT_ALLOWED"));
                return;
            }
            String path = exchange.getRequestURI().getPath();
            String method = exchange.getRequestMethod();

            if ("GET".equals(method) && ("/".equals(path) || "/index.html".equals(path))) {
                sendAsset(exchange, "text/html; charset=utf-8", indexHtml);
                return;
            }
            if ("GET".equals(method) && "/app.js".equals(path)) {
                sendAsset(exchange, "text/javascript; charset=utf-8", appJs);
                return;
            }
            if ("GET".equals(method) && "/app.css".equals(path)) {
                sendAsset(exchange, "text/css; charset=utf-8", appCss);
                return;
            }
            if ("/presenter/v1/session".equals(path)) {
                handleSession(exchange, method);
                return;
            }
            if ("/console/v1/authorizations".equals(path) && "POST".equals(method)) {
                handleSubmission(exchange);
                return;
            }
            if (path.startsWith("/console/v1/transactions/") && path.endsWith("/events")
                    && "GET".equals(method)) {
                handleEvents(exchange, path);
                return;
            }
            if ("GET".equals(method) && "/console/v1/transactions".equals(path)) {
                handleHistoryList(exchange);
                return;
            }
            if ("GET".equals(method) && path.startsWith("/console/v1/transactions/")) {
                handleHistoryDetail(exchange, path);
                return;
            }
            if ("/presenter/v1/live".equals(path)) {
                exchange.getResponseHeaders().set("Upgrade", "websocket");
                sendJson(exchange, 426, Map.of("code", "UPGRADE_REQUIRED",
                        "detail", "the live route is served only as a loopback WebSocket upgrade"));
                return;
            }
            sendJson(exchange, 404, Map.of("error", "NOT_FOUND"));
        } catch (RuntimeException failure) {
            failure.printStackTrace();
            try {
                sendJson(exchange, 500, Map.of("error", "INTERNAL_ERROR"));
            } catch (IOException | RuntimeException ignored) {
                // The response may already be committed; the listener closes the exchange anyway.
            }
        }
    }

    /* ------------------------------- live -------------------------------- */

    /**
     * RFC 6455 upgrade for {@code /presenter/v1/live}. The same loopback listener already parsed
     * the handshake, so this method enforces method, version, key shape, Host/Origin and the
     * HttpOnly presenter session before any frame is exchanged. A rejected handshake is an
     * ordinary HTTP response; the socket is closed by the listener when this method returns.
     */
    private boolean upgradeLive(LocalRelayHttpServer.UpgradeRequest request) throws IOException {
        if (!"/presenter/v1/live".equals(request.path())) {
            return false;
        }
        if (!"GET".equals(request.method())) {
            rejectUpgrade(request.output(), 405, "Method Not Allowed");
            return true;
        }
        if (!"13".equals(trim(request.headers().getFirst("Sec-WebSocket-Version")))) {
            rejectUpgrade(request.output(), 426, "Upgrade Required",
                    "Sec-WebSocket-Version: 13");
            return true;
        }
        String key = request.headers().getFirst("Sec-WebSocket-Key");
        if (!validWebSocketKey(key)) {
            rejectUpgrade(request.output(), 400, "Bad Request");
            return true;
        }
        if (!accessPolicy.webSocketAllowed(request.headers().getFirst("Host"),
                request.headers().getFirst("Origin"))) {
            rejectUpgrade(request.output(), 403, "Forbidden");
            return true;
        }
        if (sessionFromHeaders(request.headers()).isEmpty()) {
            rejectUpgrade(request.output(), 401, "Unauthorized");
            return true;
        }

        UpgradeFrames frames = new UpgradeFrames(request.output());
        // The 101 response must be the first bytes on the socket; the hub's ready frame follows.
        WebSocketFrames.writeHandshake(request.output(), key);
        Optional<PresenterLiveHub.LiveSession> opened = live.open(frames);
        if (opened.isEmpty()) {
            frames.close(1011, "live capacity unavailable");
            return true;
        }
        PresenterLiveHub.LiveSession session = opened.get();
        try {
            serveLive(session, request.input(), frames);
        } finally {
            session.close();
        }
        return true;
    }

    private static void serveLive(PresenterLiveHub.LiveSession session, InputStream input,
            UpgradeFrames frames) {
        try {
            while (session.isOpen()) {
                WebSocketFrames.Message message = WebSocketFrames.read(input);
                switch (message.opcode()) {
                    case WebSocketFrames.OPCODE_TEXT -> session.handle(
                            new String(message.payload(), StandardCharsets.UTF_8));
                    case WebSocketFrames.OPCODE_PING -> frames.pong(message.payload());
                    case WebSocketFrames.OPCODE_CLOSE -> {
                        frames.close(1000, null);
                        return;
                    }
                    case WebSocketFrames.OPCODE_PONG -> {
                        // Heartbeat response carries no server meaning.
                    }
                    default -> {
                        frames.close(1003, "unsupported data");
                        return;
                    }
                }
            }
        } catch (WebSocketFrames.ProtocolException violation) {
            frames.close(1002, "protocol error");
        } catch (IOException peerGone) {
            // The peer disappeared; the session close in the caller releases the hub slot.
        }
    }

    private static void rejectUpgrade(OutputStream output, int status, String reason,
            String... extraHeaders) throws IOException {
        StringBuilder head = new StringBuilder(160);
        head.append("HTTP/1.1 ").append(status).append(' ').append(reason).append("\r\n")
                .append("Content-Length: 0\r\n")
                .append("Connection: close\r\n");
        for (String extra : extraHeaders) {
            head.append(extra).append("\r\n");
        }
        head.append("\r\n");
        output.write(head.toString().getBytes(StandardCharsets.US_ASCII));
        output.flush();
    }

    private static boolean validWebSocketKey(String key) {
        if (key == null || key.isBlank()) {
            return false;
        }
        try {
            return Base64.getDecoder().decode(key.trim()).length == 16;
        } catch (IllegalArgumentException invalid) {
            return false;
        }
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }

    /** Single-writer framing wrapper: hub frames and control replies cannot interleave bytes. */
    private static final class UpgradeFrames implements PresenterLiveHub.FrameSink {
        private final OutputStream output;
        private final Object lock = new Object();
        private boolean closed;

        private UpgradeFrames(OutputStream output) {
            this.output = output;
        }

        @Override
        public void send(String text) throws IOException {
            synchronized (lock) {
                if (closed) {
                    throw new IOException("transport already closed");
                }
                WebSocketFrames.writeText(output, text);
            }
        }

        void pong(byte[] payload) throws IOException {
            synchronized (lock) {
                if (!closed) {
                    WebSocketFrames.writePong(output, payload);
                }
            }
        }

        @Override
        public void close() {
            close(1000, null);
        }

        void close(int statusCode, String reason) {
            synchronized (lock) {
                if (closed) {
                    return;
                }
                closed = true;
                try {
                    WebSocketFrames.writeClose(output, statusCode, reason);
                } catch (IOException ignored) {
                    // The peer is already gone; there is nothing to recover.
                }
            }
        }
    }

    /* ------------------------------ session ------------------------------ */

    private void handleSession(HttpExchange exchange, String method) throws IOException {
        if ("GET".equals(method)) {
            Optional<PresenterSessions.Session> session = currentSession(exchange);
            if (session.isEmpty()) {
                sendJson(exchange, 401, Map.of("error", "NO_SESSION"));
                return;
            }
            sendJson(exchange, 200, sessionPayload(session.get()));
            return;
        }
        if (!stateChangeAllowed(exchange)) {
            return;
        }
        if ("DELETE".equals(method)) {
            String cookie = cookieValue(exchange, SESSION_COOKIE);
            sessions.signOut(cookie);
            Headers headers = exchange.getResponseHeaders();
            headers.add("Set-Cookie", SESSION_COOKIE
                    + "=; Path=/; HttpOnly; SameSite=Strict; Max-Age=0");
            exchange.sendResponseHeaders(204, -1);
            return;
        }
        if ("POST".equals(method)) {
            JsonNode body = readJson(exchange, 2_048);
            String code = body != null && body.hasNonNull("accessCode")
                    ? body.get("accessCode").asText() : null;
            Optional<PresenterSessions.Session> session = sessions.signIn(code);
            if (session.isEmpty()) {
                sendJson(exchange, 401, Map.of("error", "INVALID_ACCESS_CODE"));
                return;
            }
            Headers headers = exchange.getResponseHeaders();
            headers.add("Set-Cookie", SESSION_COOKIE + "=" + session.get().id()
                    + "; Path=/; HttpOnly; SameSite=Strict");
            sendJson(exchange, 200, sessionPayload(session.get()));
            return;
        }
        sendJson(exchange, 405, Map.of("error", "METHOD_NOT_ALLOWED"));
    }

    private Map<String, Object> sessionPayload(PresenterSessions.Session session) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("acquirerRef", config.acquirerRef());
        context.put("merchantRef", config.merchantRef());
        context.put("terminalRef", config.terminalRef());
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("identityMode", "LOCAL_TEST_FIXTURE");
        payload.put("subject", session.subject());
        payload.put("environmentId", config.environmentId());
        payload.put("context", context);
        payload.put("expiresAt", session.expiresAt().toString());
        return payload;
    }

    /* ---------------------------- submission ----------------------------- */

    private void handleSubmission(HttpExchange exchange) throws IOException {
        Optional<PresenterSessions.Session> session = currentSession(exchange);
        if (session.isEmpty()) {
            sendJson(exchange, 401, Map.of("error", "UNAUTHENTICATED"));
            return;
        }
        if (!stateChangeAllowed(exchange)) {
            return;
        }
        byte[] body = readBody(exchange, MAX_REQUEST_BYTES);
        if (body == null) {
            sendC3(exchange, C3ResponseBuilder.build(preAdmission(
                    C3ResponseBuilder.PreAdmissionFailure.BODY_TOO_LARGE, null)));
            return;
        }

        ChannelAuthorizationRequest channel;
        try {
            channel = ChannelRequestValidator.validate(body);
        } catch (ChannelRequestValidationException rejected) {
            C3ResponseBuilder.PreAdmissionFailure failure =
                    rejected.errorCode() == com.acquiring.simulator.channel.ChannelErrorCode
                            .BODY_TOO_LARGE
                            ? C3ResponseBuilder.PreAdmissionFailure.BODY_TOO_LARGE
                            : C3ResponseBuilder.PreAdmissionFailure.INVALID_INPUT;
            sendC3(exchange, C3ResponseBuilder.build(preAdmission(failure, correlationOf(body))));
            return;
        }

        // The relay fixes the business context: a caller may only echo it.
        if (!config.acquirerRef().equals(channel.acquirerRef())
                || !config.merchantRef().equals(channel.merchantRef())
                || !config.terminalRef().equals(channel.terminalRef())) {
            sendC3(exchange, C3ResponseBuilder.build(denial(
                    C3ResponseBuilder.Denial.CONTEXT_DENIED, channel.correlationId())));
            return;
        }

        AuthorizedContext caller = new AuthorizedContext(config.acquirerRef(),
                config.merchantRef(), session.get().subject(), Set.of(Route.DOMESTIC));
        PurchaseRequest purchase = new PurchaseRequest(
                config.merchantRef(),
                config.terminalRef(),
                channel.paymentRef(),
                channel.amount(),
                channel.currency(),
                channel.credentialFixtureRef(),
                channel.entryMode(),
                channel.cvm(),
                channel.cryptogramPresent(),
                channel.risk(),
                channel.priorAuthorizationId());

        AuthorizationResult result;
        try {
            result = service.authorize(caller, purchase);
        } catch (RuntimeException failure) {
            failure.printStackTrace();
            sendC3(exchange, C3ResponseBuilder.build(unreadable(
                    C3ResponseBuilder.InvocationIdentifiers.none(channel.correlationId()))));
            return;
        }

        submissions.incrementAndGet();
        if (result.reference() == null) {
            if (result.outcome() == Outcome.PROCESSOR_REJECTED) {
                // The domain rejected this attempt before any business admission. C3 row for
                // pre-admission validation/refusal is 400 REJECTED, never a 503 unsent claim.
                sendC3(exchange, C3ResponseBuilder.build(preAdmission(
                        C3ResponseBuilder.PreAdmissionFailure.INVALID_INPUT,
                        channel.correlationId())));
            } else {
                sendC3(exchange, C3ResponseBuilder.build(noClaim(channel.correlationId())));
            }
            return;
        }

        String authorizationId = result.reference();
        String attemptId = "ATT-" + authorizationId;
        AuthorizationRecord record = repository.findById(authorizationId).orElse(null);
        if (record == null || record.dispatchCorrelationId() == null) {
            sendC3(exchange, C3ResponseBuilder.build(unreadable(
                    new C3ResponseBuilder.InvocationIdentifiers(authorizationId,
                            channel.paymentRef(), attemptId, channel.correlationId()))));
            return;
        }
        Long committedVersion = repository.readVersionEvidence(authorizationId)
                .committedJournalVersion();
        AuthorizationState state = record.state();
        if (committedVersion == null || !wireExpressible(state)) {
            sendC3(exchange, C3ResponseBuilder.build(unreadable(
                    new C3ResponseBuilder.InvocationIdentifiers(authorizationId,
                            channel.paymentRef(), attemptId, channel.correlationId()))));
            return;
        }

        C3ResponseBuilder.Identifiers identifiers = new C3ResponseBuilder.Identifiers(
                authorizationId, channel.paymentRef(), attemptId, channel.correlationId(),
                record.dispatchCorrelationId());
        C3ResponseBuilder.AsyncHealth health = C3ResponseBuilder.AsyncHealth.healthy();
        C3ResponseBuilder.Evidence evidence = new C3ResponseBuilder.Evidence.ConfirmedAuthorization(
                state, committedVersion, identifiers, health);
        C3ResponseBuilder.Facts facts = new C3ResponseBuilder.Facts(
                C3ResponseBuilder.Denial.NONE, null, false, evidence,
                C3ResponseBuilder.Origin.SUBMISSION,
                new C3ResponseBuilder.AttemptIdentifiers(attemptId, channel.correlationId()),
                clock.instant());
        sendC3(exchange, C3ResponseBuilder.build(facts));
    }

    private static boolean wireExpressible(AuthorizationState state) {
        return state == AuthorizationState.APPROVED
                || state == AuthorizationState.DECLINED
                || state == AuthorizationState.NETWORK_REJECTED;
    }

    private C3ResponseBuilder.Facts preAdmission(C3ResponseBuilder.PreAdmissionFailure failure,
            String correlationId) {
        return new C3ResponseBuilder.Facts(C3ResponseBuilder.Denial.NONE, failure, false,
                new C3ResponseBuilder.Evidence.NoClaimUnsent(
                        C3ResponseBuilder.UnsentCause.ADMISSION_UNAVAILABLE),
                C3ResponseBuilder.Origin.SUBMISSION,
                new C3ResponseBuilder.AttemptIdentifiers(null, correlationId), clock.instant());
    }

    private C3ResponseBuilder.Facts denial(C3ResponseBuilder.Denial denial,
            String correlationId) {
        return new C3ResponseBuilder.Facts(denial, null, false,
                new C3ResponseBuilder.Evidence.UnreadableStore(
                        C3ResponseBuilder.InvocationIdentifiers.none(correlationId)),
                C3ResponseBuilder.Origin.SUBMISSION,
                new C3ResponseBuilder.AttemptIdentifiers(null, correlationId), clock.instant());
    }

    private C3ResponseBuilder.Facts noClaim(String correlationId) {
        return new C3ResponseBuilder.Facts(C3ResponseBuilder.Denial.NONE, null, false,
                new C3ResponseBuilder.Evidence.NoClaimUnsent(
                        C3ResponseBuilder.UnsentCause.ADMISSION_UNAVAILABLE),
                C3ResponseBuilder.Origin.SUBMISSION,
                new C3ResponseBuilder.AttemptIdentifiers(null, correlationId), clock.instant());
    }

    private C3ResponseBuilder.Facts unreadable(C3ResponseBuilder.InvocationIdentifiers ids) {
        return new C3ResponseBuilder.Facts(C3ResponseBuilder.Denial.NONE, null, false,
                new C3ResponseBuilder.Evidence.UnreadableStore(ids),
                C3ResponseBuilder.Origin.SUBMISSION,
                new C3ResponseBuilder.AttemptIdentifiers(ids.attemptId(), ids.correlationId()),
                clock.instant());
    }

    /* ------------------------------ events ------------------------------- */

    private void handleEvents(HttpExchange exchange, String path) throws IOException {
        if (currentSession(exchange).isEmpty()) {
            sendJson(exchange, 401, Map.of("error", "UNAUTHENTICATED"));
            return;
        }
        String transactionId = path.substring("/console/v1/transactions/".length(),
                path.length() - "/events".length());
        if (transactionId.isBlank() || transactionId.contains("/")) {
            sendJson(exchange, 404, Map.of("error", "NOT_FOUND"));
            return;
        }
        Map<String, String> query = query(exchange.getRequestURI().getRawQuery());
        long after;
        try {
            after = query.containsKey("after") ? Long.parseLong(query.get("after")) : 0L;
        } catch (NumberFormatException invalid) {
            sendJson(exchange, 400, Map.of("code", "INVALID_CURSOR"));
            return;
        }
        if (after < 0) {
            sendJson(exchange, 400, Map.of("code", "INVALID_CURSOR"));
            return;
        }
        EventContext context = new EventContext(config.environmentId(), config.acquirerRef(),
                config.merchantRef(), transactionId);
        Optional<EventHead> head = store.head(context);
        long snapshotHead = head.map(EventHead::lastSequence).orElse(0L);
        long expiryRevision = head.map(EventHead::expiryRevision).orElse(1L);
        long firstRetained = head.map(EventHead::firstRetainedSequence).orElse(1L);
        List<RecordedEvent> rows = snapshotHead > after
                ? history.readRange(context, after, snapshotHead, 100) : List.of();

        ObjectNode page = json.createObjectNode();
        page.put("version", 1);
        page.put("transactionId", transactionId);
        page.put("snapshotHead", snapshotHead);
        page.put("expiryRevision", expiryRevision);
        page.put("firstRetainedSequence", firstRetained);
        ArrayNode events = page.putArray("events");
        long coveredThrough = after;
        for (RecordedEvent row : rows) {
            ObjectNode item = events.addObject();
            item.put("sourceSequence", row.sourceSequence());
            item.put("deliveryClass", row.wrapper().deliveryClass() == null
                    ? "CRITICAL_COMMITTED" : row.wrapper().deliveryClass().name());
            item.put("eventSha256", row.wrapper().eventSha256());
            if (row.wrapper().journalVersion() == null) {
                item.putNull("journalVersion");
            } else {
                item.put("journalVersion", row.wrapper().journalVersion());
            }
            item.put("committedAt", row.wrapper().committedAt());
            item.put("collectedAt", row.wrapper().collectedAt());
            item.put("storedAt", row.wrapper().storedAt());
            item.put("stateAfter", row.wrapper().stateAfter());
            JsonNode record = json.readTree(row.canonicalJson());
            item.set("record", record);
            coveredThrough = Math.max(coveredThrough, row.sourceSequence());
        }
        page.put("coveredThrough", coveredThrough);
        page.putArray("expiredRanges");
        page.putArray("gapRanges");
        page.putNull("nextCursor");
        page.put("completeThroughSnapshot", coveredThrough >= snapshotHead);
        page.put("retentionTruncated", false);
        send(exchange, 200, page);
    }

    /* ------------------------------ history ------------------------------ */

    /**
     * Bounded seven-day history list over the same journal rows. It reads no second payload copy
     * and reports only what the journal can prove: an unreadable version stays explicit.
     */
    private void handleHistoryList(HttpExchange exchange) throws IOException {
        if (currentSession(exchange).isEmpty()) {
            sendJson(exchange, 401, Map.of("error", "UNAUTHENTICATED"));
            return;
        }
        Map<String, String> query = query(exchange.getRequestURI().getRawQuery());
        int limit = 20;
        if (query.containsKey("limit")) {
            try {
                limit = Integer.parseInt(query.get("limit"));
            } catch (NumberFormatException invalid) {
                sendJson(exchange, 400, Map.of("code", "INVALID_LIMIT"));
                return;
            }
            if (limit < 1 || limit > 50) {
                sendJson(exchange, 400, Map.of("code", "INVALID_LIMIT"));
                return;
            }
        }
        Instant since = clock.instant().minus(config.historyWindow());
        List<AuthorizationRecord> records;
        try {
            records = lister.recentAuthorizations(since, 200).stream()
                    .filter(record -> config.acquirerRef().equals(record.acquirerRef())
                            && config.merchantRef().equals(record.merchantRef()))
                    .sorted((left, right) -> right.firstReceivedAt()
                            .compareTo(left.firstReceivedAt()))
                    .limit(limit)
                    .toList();
        } catch (RuntimeException unreadable) {
            sendJson(exchange, 503, Map.of("code", "EVENT_HISTORY_UNAVAILABLE",
                    "detail", "the local journal list is not readable"));
            return;
        }

        ObjectNode page = json.createObjectNode();
        page.put("window", "7d");
        page.put("retentionTruncated", false);
        page.putNull("nextCursor");
        ArrayNode transactions = page.putArray("transactions");
        for (AuthorizationRecord record : records) {
            ObjectNode item = transactions.addObject();
            item.put("authorizationId", record.id());
            item.put("transactionId", record.channelPaymentRef());
            item.put("channelPaymentRef", record.channelPaymentRef());
            item.put("amount", java.math.BigDecimal
                    .valueOf(record.amountMinor(), 2).toPlainString());
            item.put("currency", record.currency());
            String outcome = wireOutcome(record.state());
            if (outcome == null) {
                item.putNull("outcome");
            } else {
                item.put("outcome", outcome);
            }
            Long version = null;
            boolean readable = true;
            try {
                version = repository.readVersionEvidence(record.id()).committedJournalVersion();
            } catch (RuntimeException unreadable) {
                readable = false;
            }
            if (version == null) {
                item.putNull("businessVersion");
            } else {
                item.put("businessVersion", version);
            }
            item.put("asOf", clock.instant().toString());
            item.put("serverFreshness", readable ? "CURRENT" : "UNKNOWN");
            item.put("journalReadable", readable);
            item.put("receivedAt", record.firstReceivedAt().toString());
        }
        send(exchange, 200, page);
    }

    /** One journal-authoritative detail with the event watermark the same partition exposes. */
    private void handleHistoryDetail(HttpExchange exchange, String path) throws IOException {
        if (currentSession(exchange).isEmpty()) {
            sendJson(exchange, 401, Map.of("error", "UNAUTHENTICATED"));
            return;
        }
        String authorizationId = path.substring("/console/v1/transactions/".length());
        if (authorizationId.isBlank() || authorizationId.contains("/")) {
            sendJson(exchange, 404, Map.of("error", "NOT_FOUND"));
            return;
        }
        Optional<AuthorizationRecord> found;
        try {
            found = repository.findById(authorizationId);
        } catch (RuntimeException unreadable) {
            sendJson(exchange, 503, Map.of("code", "BUSINESS_JOURNAL_UNAVAILABLE"));
            return;
        }
        if (found.isEmpty()
                || !config.acquirerRef().equals(found.get().acquirerRef())
                || !config.merchantRef().equals(found.get().merchantRef())) {
            sendJson(exchange, 404, Map.of("code", "NOT_FOUND"));
            return;
        }
        AuthorizationRecord record = found.get();

        Long version = null;
        boolean journalReadable = true;
        try {
            version = repository.readVersionEvidence(record.id()).committedJournalVersion();
        } catch (RuntimeException unreadable) {
            journalReadable = false;
        }

        EventContext context = new EventContext(config.environmentId(), config.acquirerRef(),
                config.merchantRef(), record.channelPaymentRef());
        long knownHead = 0L;
        long firstRetained = 1L;
        String lastEventId = null;
        try {
            Optional<EventHead> head = store.head(context);
            if (head.isPresent()) {
                knownHead = head.get().lastSequence();
                firstRetained = head.get().firstRetainedSequence();
                if (knownHead > 0) {
                    List<RecordedEvent> last = history.readRange(
                            context, knownHead - 1, knownHead, 1);
                    if (!last.isEmpty()) {
                        JsonNode event = json.readTree(last.get(0).canonicalJson());
                        lastEventId = event.path("eventId").asText(null);
                    }
                }
            }
        } catch (RuntimeException unreadable) {
            journalReadable = false;
        }

        ObjectNode detail = json.createObjectNode();
        detail.put("authority", "JOURNAL_AND_EVENTS");
        detail.put("authorizationId", record.id());
        detail.put("transactionId", record.channelPaymentRef());
        detail.put("journalReadable", journalReadable);
        String outcome = wireOutcome(record.state());
        if (outcome == null) {
            detail.putNull("journalOutcome");
        } else {
            detail.put("journalOutcome", outcome);
        }
        if (version == null) {
            detail.putNull("journalVersion");
        } else {
            detail.put("journalVersion", version);
        }
        detail.put("journalAsOf", clock.instant().toString());
        if (lastEventId == null) {
            detail.putNull("lastRecordedEventId");
        } else {
            detail.put("lastRecordedEventId", lastEventId);
        }
        detail.put("knownHead", knownHead);
        detail.put("coveredThrough", knownHead);
        detail.put("retentionTruncated", firstRetained > 1);
        detail.putArray("expiredRanges");
        detail.putArray("taskGaps");
        send(exchange, 200, detail);
    }

    private static String wireOutcome(AuthorizationState state) {
        return switch (state) {
            case APPROVED -> "APPROVED";
            case DECLINED -> "DECLINED";
            case NETWORK_REJECTED -> "PROCESSOR_REJECTED";
            case AWAITING, NOT_CONFIRMED -> null;
        };
    }

    /* ---------------------------- transport ------------------------------ */

    private boolean stateChangeAllowed(HttpExchange exchange) throws IOException {
        String origin = exchange.getRequestHeaders().getFirst("Origin");
        String requestHeader = exchange.getRequestHeaders().getFirst(
                PresenterAccessPolicy.REQUEST_HEADER);
        if (!accessPolicy.sameOriginStateChange(origin, requestHeader)) {
            sendJson(exchange, 403, Map.of("error", "SAME_ORIGIN_REQUIRED"));
            return false;
        }
        return true;
    }

    private Optional<PresenterSessions.Session> currentSession(HttpExchange exchange) {
        return sessions.resolve(cookieValue(exchange, SESSION_COOKIE));
    }

    private Optional<PresenterSessions.Session> sessionFromHeaders(Headers headers) {
        return sessions.resolve(cookieValue(headers, SESSION_COOKIE));
    }

    private static String cookieValue(HttpExchange exchange, String name) {
        return cookieValue(exchange.getRequestHeaders(), name);
    }

    private static String cookieValue(Headers headers, String name) {
        List<String> cookies = headers.get("Cookie");
        if (cookies == null) {
            return null;
        }
        for (String header : cookies) {
            for (String part : header.split(";")) {
                String value = part.trim();
                int equals = value.indexOf('=');
                if (equals > 0 && name.equals(value.substring(0, equals))) {
                    return value.substring(equals + 1);
                }
            }
        }
        return null;
    }

    private JsonNode readJson(HttpExchange exchange, int limit) throws IOException {
        byte[] body = readBody(exchange, limit);
        if (body == null || body.length == 0) {
            return null;
        }
        try {
            return json.readTree(body);
        } catch (IOException invalid) {
            return null;
        }
    }

    private static byte[] readBody(HttpExchange exchange, int limit) throws IOException {
        try (InputStream input = exchange.getRequestBody()) {
            byte[] body = input.readNBytes(limit + 1);
            return body.length > limit ? null : body;
        }
    }

    private static String correlationOf(byte[] body) {
        if (body == null) {
            return null;
        }
        try {
            JsonNode node = new ObjectMapper().readTree(body);
            JsonNode correlation = node.get("correlationId");
            return correlation == null || !correlation.isTextual() ? null : correlation.asText();
        } catch (IOException invalid) {
            return null;
        }
    }

    private static Map<String, String> query(String rawQuery) {
        Map<String, String> values = new LinkedHashMap<>();
        if (rawQuery == null || rawQuery.isBlank()) {
            return values;
        }
        for (String pair : rawQuery.split("&")) {
            int equals = pair.indexOf('=');
            if (equals > 0) {
                values.put(pair.substring(0, equals), pair.substring(equals + 1));
            }
        }
        return values;
    }

    private void sendC3(HttpExchange exchange, C3Response response) throws IOException {
        byte[] body = response.envelope().toCanonicalJson().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(response.httpStatus().code(), body.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(body);
        }
    }

    private void sendJson(HttpExchange exchange, int status, Object payload) throws IOException {
        byte[] body = json.writeValueAsBytes(payload);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, body.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(body);
        }
    }

    private void send(HttpExchange exchange, int status, JsonNode payload) throws IOException {
        byte[] body = json.writeValueAsBytes(payload);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, body.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(body);
        }
    }

    private static void sendAsset(HttpExchange exchange, String contentType, byte[] body)
            throws IOException {
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(200, body.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(body);
        }
    }

    private void securityHeaders(HttpExchange exchange) {
        Headers headers = exchange.getResponseHeaders();
        String ws = "'self' ws://127.0.0.1:" + port() + " ws://localhost:" + port();
        headers.set("Content-Security-Policy", "default-src 'self'; script-src 'self'; "
                + "style-src 'self'; img-src 'self' data:; connect-src " + ws + "; "
                + "frame-ancestors 'none'; base-uri 'none'; form-action 'none'; "
                + "object-src 'none'");
        headers.set("X-Content-Type-Options", "nosniff");
        headers.set("Referrer-Policy", "no-referrer");
        headers.set("Cache-Control", "no-store");
        headers.set("X-Frame-Options", "DENY");
    }

    private static byte[] asset(String name) throws IOException {
        try (InputStream input = PresenterRelayServer.class.getResourceAsStream(
                "/presenter/" + name)) {
            if (input == null) {
                throw new IllegalStateException("missing presenter asset: " + name);
            }
            return input.readAllBytes();
        }
    }

    /** Never render the demo access code or a session identifier. */
    @Override
    public String toString() {
        return "PresenterRelayServer[port=" + port() + ", environmentId="
                + config.environmentId() + "]";
    }
}
