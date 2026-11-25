import com.acquiring.simulator.console.*;
import com.acquiring.simulator.domain.event.EventContext;
import com.acquiring.simulator.domain.event.EventDurability;
import com.acquiring.simulator.domain.event.EventHead;
import com.acquiring.simulator.domain.event.EventSourceWrapper;
import com.acquiring.simulator.domain.event.RecordedEvent;
import com.acquiring.simulator.domain.model.AuthorizedContext;
import com.acquiring.simulator.domain.model.Route;
import com.acquiring.simulator.domain.port.EventHistoryReader;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.*;

/**
 * Independent QA driver for the H1 slice (ai-sdlc-qa 2026-11-23).
 * Runs against compiled main classes; uses only public seams and its own fakes.
 * Normative oracles: N-02 contract-delta (gate) and rev3 wire-fixtures.json (history
 * error bodies / page schema / cursor binding field set), as amended by DR3-01.
 * RECHECK revision (ai-sdlc-qa 2026-11-23): binds issuer/terminalRef (QA-DEF-1/3 fixes),
 * byte-compares every reachable 503 site against the reviewed fixture (QA-DEF-2 fix),
 * and adds negative foreign-issuer / other-terminal continuation refusals.
 */
public class QaH1Driver {

    static int pass = 0, fail = 0;
    static final List<String> failures = new ArrayList<>();

    static final String EXPECTED_DENIAL_BODY =
            "{\"version\":1,\"error\":\"EDGE_ORIGIN_NOT_VERIFIED\",\"action\":null}";
    static final String EXPECTED_400 = "{\"version\":1,\"error\":\"INVALID_CURSOR\",\"action\":null}";
    static final String EXPECTED_401 = "{\"version\":1,\"error\":\"UNAUTHENTICATED\",\"action\":null}";
    static final String EXPECTED_403 = "{\"version\":1,\"error\":\"CONTEXT_DENIED\",\"action\":null}";
    static final String EXPECTED_404 = "{\"version\":1,\"error\":\"NOT_FOUND\",\"action\":null}";
    static final String EXPECTED_410 =
            "{\"version\":1,\"error\":\"RESYNC_REQUIRED\",\"action\":\"RESYNC_REQUIRED\"}";
    static final String FIXTURE_503 =
            "{\"version\":1,\"error\":\"AUTHORIZATION_UNAVAILABLE\",\"action\":null}";
    static final List<String> PAGE_KEYS = List.of("version", "transactionId", "snapshotHead",
            "expiryRevision", "firstRetainedSequence", "events", "coveredThrough", "expiredRanges",
            "gapRanges", "nextCursor", "completeThroughSnapshot", "retentionTruncated");
    static final Set<String> FIXTURE_CURSOR_KEYS = Set.of("version", "issuer", "azp", "subject",
            "environment", "registryMandateId", "registryVersion", "context-fields",
            "transactionId", "generation", "epoch", "H", "R", "F", "lastSequence",
            "issuedAt", "expiresAt", "keyId");

    static final Instant T0 = Instant.parse("2026-11-23T22:00:00Z");
    static final String ISSUER = "https://auth.example.invalid/realms/acq-DEMO";
    static final String TERMINAL_A = "TERMINAL-A";
    static final String ENV = "DEMO", ACQ = "ACQ-A", MER = "MERCHANT-A", TXN = "TX-001";
    static final byte[] KEY32 = "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8);

    public static void main(String[] args) throws Exception {
        gateSuite();
        historySuite();
        System.out.println();
        System.out.println("QA-DRIVER RESULT: pass=" + pass + " fail=" + fail);
        for (String f : failures) System.out.println("FAILED: " + f);
        if (fail > 0) System.exit(1);
    }

    // ------------------------------------------------------------------ helpers

    static void check(String id, boolean ok, String detail) {
        if (ok) { pass++; System.out.println("PASS " + id); }
        else { fail++; failures.add(id + " :: " + detail); System.out.println("FAIL " + id + " :: " + detail); }
    }

    static ConsoleHttpRequest req(String method, String path,
            Map<String, List<String>> headers, Map<String, List<String>> query) {
        return new ConsoleHttpRequest(method, path, headers, new byte[0], query);
    }

    static String body(ConsoleHttpResponse r) {
        return new String(r.body(), StandardCharsets.UTF_8);
    }

    static final class MutableClock extends Clock {
        volatile Instant now;
        MutableClock(Instant start) { this.now = start; }
        @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId z) { return this; }
        @Override public Instant instant() { return now; }
        void advanceSeconds(long s) { now = now.plusSeconds(s); }
    }

    static final class CountingApp implements java.util.function.Function<ConsoleHttpRequest, ConsoleHttpResponse> {
        int calls;
        ConsoleHttpResponse reply;
        @Override public ConsoleHttpResponse apply(ConsoleHttpRequest r) { calls++; return reply; }
    }

    static final class CountingSink implements ConsoleOriginDenialSink {
        int emits; RuntimeException boom;
        List<ConsoleOriginDenial> emitted = new ArrayList<>();
        @Override public void emit(ConsoleOriginDenial d) {
            emits++;
            if (boom != null) throw boom;
            emitted.add(d);
        }
    }

    static Map<String, List<String>> h(String name, String... values) {
        return Map.of(name, List.of(values));
    }

    static String verifyValue() {
        return "V".repeat(24) + "abcdefgh12345678"; // 40 chars, token alphabet
    }

    static ConsoleOriginVerificationGate gate() {
        return ConsoleOriginVerificationGate.configured(verifyValue());
    }

    static ConsoleHttpRequest rejectionProbe(Map<String, List<String>> headers) {
        return req("POST", "/console/v1/authorizations", headers, Map.of());
    }

    static ConsoleHttpResponse deny(ConsoleOriginVerificationGate g, ConsoleHttpRequest r,
            CountingSink sink) {
        CountingApp app = new CountingApp();
        ConsoleIntegrationEntryPoint ep = new ConsoleIntegrationEntryPoint(g, app, sink,
                Clock.fixed(T0, ZoneOffset.UTC), "h1-qa");
        ConsoleHttpResponse resp = ep.handle(r, new ConsoleRequestIdentity("req-1", "10.0.0.1"));
        if (app.calls != 0) throw new AssertionError("application was invoked on refusal");
        return resp;
    }

    // ------------------------------------------------------------------ gate suite

    static void gateSuite() {
        // G1..G4 initialization failures
        for (String[] c : new String[][]{
                {"G1-init-absent", null},
                {"G2-init-empty", ""},
                {"G3-init-short", "abc"},
                {"G4-init-bad-alphabet", "a".repeat(31) + "!"}}) {
            boolean threw;
            try { ConsoleOriginVerificationGate.configured(c[1]); threw = false; }
            catch (RuntimeException e) { threw = true; }
            check(c[0], threw, "expected initialization failure");
        }
        // G5 minimum-length valid value initializes
        boolean ok;
        try { ConsoleOriginVerificationGate.configured("a".repeat(32)); ok = true; }
        catch (RuntimeException e) { ok = false; }
        check("G5-init-minimum-valid", ok, "32-char value should initialize");

        ConsoleOriginVerificationGate g = gate();
        String v = verifyValue();

        // Refusal classification and fixed bytes
        record Case(String id, Map<String, List<String>> headers, String reason) {}
        List<Case> cases = List.of(
            new Case("G6-absent", Map.of(), "ABSENT"),
            new Case("G7-empty", h("X-Origin-Verify", ""), "EMPTY"),
            new Case("G8-whitespace-only", h("X-Origin-Verify", "   "), "EMPTY"),
            new Case("G9-duplicate-two-values", h("X-Origin-Verify", v, v), "DUPLICATE"),
            new Case("G10-duplicate-case-variant-names",
                    new LinkedHashMap<>(Map.of("x-origin-verify", List.of(v),
                            "X-ORIGIN-VERIFY", List.of(v))), "DUPLICATE"),
            new Case("G11-duplicate-correct-then-wrong", h("X-Origin-Verify", v, v + "x"), "DUPLICATE"),
            new Case("G12-comma-coalesced-correct-first", h("X-Origin-Verify", v + "," + v + "x"), "DUPLICATE"),
            new Case("G13-comma-coalesced-wrong-first", h("X-Origin-Verify", "zzz," + v), "DUPLICATE"),
            new Case("G14-mismatch-one-char", h("X-Origin-Verify", v.substring(0, 39) + "W"), "MISMATCH"),
            new Case("G15-mismatch-case-transformed", h("X-Origin-Verify", v.toLowerCase()), "MISMATCH"));

        List<String> bodies = new ArrayList<>();
        for (Case c : cases) {
            ConsoleHttpResponse r = deny(g, rejectionProbe(c.headers()), new CountingSink());
            boolean exact = r.status() == 403 && body(r).equals(EXPECTED_DENIAL_BODY);
            bodies.add(body(r));
            boolean noEcho = !body(r).contains(v) && !body(r).toUpperCase().contains(v.toUpperCase());
            boolean ct = r.headers().get("Content-Type") != null
                    && r.headers().get("Content-Type").equals(List.of("application/json"));
            boolean noCors = r.headers().keySet().stream().noneMatch(k -> k.startsWith("Access-Control"));
            check(c.id, exact && noEcho && ct && noCors,
                    "status=" + r.status() + " body=" + body(r) + " headers=" + r.headers().keySet());
        }
        check("G16-all-refusals-byte-identical",
                bodies.stream().distinct().count() == 1 && bodies.get(0).equals(EXPECTED_DENIAL_BODY),
                "bodies=" + bodies);

        // lowercase header name must still be matched (case-insensitive occurrence)
        ConsoleHttpResponse rLow = deny(g, rejectionProbe(h("x-origin-verify", v + "x")), new CountingSink());
        check("G17-name-match-case-insensitive",
                rLow.status() == 403 && body(rLow).equals(EXPECTED_DENIAL_BODY), "status=" + rLow.status());

        // G18 verified passes to application unchanged; app is the credential seam then
        CountingSink sink = new CountingSink();
        CountingApp app = new CountingApp();
        app.reply = ConsoleHttpResponse.json(401, EXPECTED_401);
        ConsoleIntegrationEntryPoint ep = new ConsoleIntegrationEntryPoint(g, app, sink,
                Clock.fixed(T0, ZoneOffset.UTC), "h1-qa");
        ConsoleHttpRequest verified = rejectionProbe(h("X-Origin-Verify", v));
        ConsoleHttpResponse rv = ep.handle(verified, ConsoleRequestIdentity.UNKNOWN);
        check("G18-verified-reaches-application",
                app.calls == 1 && rv.status() == 401 && body(rv).equals(EXPECTED_401),
                "appCalls=" + app.calls + " status=" + rv.status() + " body=" + body(rv));
        check("G19-verified-request-passed-unchanged", verified.method().equals("POST"),
                "request mutated");
        check("G20-verified-no-denial-record", sink.emits == 0, "emits=" + sink.emits);

        // G21 sink failure contained; response unchanged
        CountingSink bad = new CountingSink();
        bad.boom = new RuntimeException("sink down");
        ConsoleHttpResponse rBad = deny(g, rejectionProbe(h("X-Origin-Verify", "")), bad);
        check("G21-sink-failure-contained",
                bad.emits == 1 && rBad.status() == 403 && body(rBad).equals(EXPECTED_DENIAL_BODY),
                "emits=" + bad.emits + " status=" + rBad.status());

        // G22 closed denial record field set, correct reason, no echo
        CountingSink rec = new CountingSink();
        deny(g, rejectionProbe(h("X-Origin-Verify", v + "x")), rec);
        ConsoleOriginDenial d = rec.emitted.get(0);
        List<String> fields = List.of("event", "reason", "requestId", "sourceIp", "host",
                "httpMethod", "path", "timestamp", "appVersion");
        check("G22-denial-closed-field-set", new ArrayList<>(d.fields().keySet()).equals(fields),
                "fields=" + d.fields().keySet());
        check("G23-denial-reason-and-event",
                ConsoleOriginDenial.EVENT.equals("console_origin_verification_denied")
                        && d.reason().equals("MISMATCH"), "reason=" + d.reason());
        boolean leak = d.toString().contains(v) || d.fields().values().stream()
                .anyMatch(x -> x != null && x.contains(v));
        check("G24-denial-no-header-echo", !leak, "denial echoes value");

        // G25 gate precedes the application's own 404/405 handling
        CountingApp routeLike = new CountingApp();
        routeLike.reply = ConsoleHttpResponse.empty(404);
        ConsoleIntegrationEntryPoint ep2 = new ConsoleIntegrationEntryPoint(g, routeLike, sink,
                Clock.fixed(T0, ZoneOffset.UTC), "h1-qa");
        ConsoleHttpResponse r404 = ep2.handle(
                req("GET", "/console/v1/unknown", Map.of(), Map.of()),
                ConsoleRequestIdentity.UNKNOWN);
        check("G25-gate-before-404-405",
                routeLike.calls == 0 && r404.status() == 403
                        && body(r404).equals(EXPECTED_DENIAL_BODY),
                "appCalls=" + routeLike.calls + " status=" + r404.status());

        // G26 entry point requires the gate (OV-GATE-REQUIRED)
        boolean required;
        try { new ConsoleIntegrationEntryPoint(null, app, sink, Clock.systemUTC(), "v"); required = false; }
        catch (RuntimeException e) { required = true; }
        check("G26-entry-point-requires-gate", required, "null gate accepted");
    }

    // ------------------------------------------------------------------ history fakes

    static final class FakeStore implements com.acquiring.simulator.domain.port.EventStore {
        volatile EventHead head; // null = unknown target
        volatile RuntimeException headFailure;
        int headCalls, appendCalls, findCalls;
        @Override public com.acquiring.simulator.domain.event.EventAppendResult append(
                com.acquiring.simulator.domain.event.EventSubmission s) { appendCalls++; throw new AssertionError("history must not append"); }
        @Override public Optional<com.acquiring.simulator.domain.event.StoredEvent> find(
                com.acquiring.simulator.domain.event.EventDedupeKey k) { findCalls++; throw new AssertionError("history must not dedupe-probe"); }
        @Override public Optional<EventHead> head(EventContext c) {
            headCalls++;
            if (headFailure != null) throw headFailure;
            return Optional.ofNullable(head);
        }
    }

    static final class CountingRegistry implements ConsoleContextRegistry {
        volatile AuthorizedContext mandate = new AuthorizedContext(ACQ, MER, "demo-caller", Set.of(Route.DOMESTIC));
        volatile RuntimeException failure; // thrown on the Nth call
        int failureOnCall = Integer.MAX_VALUE, calls;
        @Override public AuthorizedContext contextFor(ConsolePrincipal p) {
            calls++;
            if (calls >= failureOnCall && failure != null) throw failure;
            return mandate;
        }
    }

    // ------------------------------------------------------------------ history suite

    static EventContext ctx() { return new EventContext(ENV, ACQ, MER, TXN); }

    static RecordedEvent row(long seq) {
        EventSourceWrapper w = new EventSourceWrapper(EventDurability.CRITICAL_COMMITTED,
                "sha256-" + seq, 1L, "2026-11-23T22:00:00Z", "2026-11-23T22:00:01Z",
                seq, "2026-11-23T22:00:02Z", "AUTHORIZED");
        return new RecordedEvent(w, "{\"seq\":" + seq + "}");
    }

    static final class FakeReader implements EventHistoryReader {
        Map<Long, RecordedEvent> rows = new TreeMap<>();
        volatile RuntimeException failure;
        int calls;
        @Override public List<RecordedEvent> readRange(EventContext c, long after, long through, int limit) {
            calls++;
            if (failure != null) throw failure;
            List<RecordedEvent> out = new ArrayList<>();
            for (Map.Entry<Long, RecordedEvent> e : new TreeMap<>(rows).entrySet()) {
                long s = e.getKey();
                if (s > after && s <= through && out.size() < limit) out.add(e.getValue());
            }
            return out;
        }
    }

    static ConsoleHistoryRoute route(MutableClock clock, FakeStore store, FakeReader reader,
            CountingRegistry registry, ConsoleHistoryCursorCodec codec) {
        ConsoleAuthorizer auth = (token, now) -> {
            if (token == null || token.isBlank()) throw new ConsoleAuthenticationException(
                    ConsoleAuthenticationException.Reason.MISSING_TOKEN);
            return new ConsolePrincipal(ISSUER, "DEMO-USER", "console-demo", "console-api",
                    Set.of("console.demo"));
        };
        return new ConsoleHistoryRoute(auth, registry, store, reader, codec, ENV, 900, clock);
    }

    static ConsoleHttpRequest authGet(String pathAndQuery) {
        return new ConsoleHttpRequest("GET", pathAndQuery,
                Map.of("Authorization", List.of("Bearer demo-token")), new byte[0], Map.of());
    }

    static long num(Map<String, Object> page, String key) {
        return ((Number) page.get(key)).longValue();
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> page(String json) throws Exception {
        ObjectMapper m = new ObjectMapper();
        return m.readValue(json, Map.class);
    }

    static void historySuite() throws Exception {
        MutableClock clock = new MutableClock(T0);
        FakeStore store = new FakeStore();
        FakeReader reader = new FakeReader();
        CountingRegistry registry = new CountingRegistry();
        ConsoleHistoryCursorCodec codec = new ConsoleHistoryCursorCodec("qa-key-1", KEY32);
        ConsoleHistoryRoute route = route(clock, store, reader, registry, codec);

        // H1 complete snapshot, exact O6 page schema and key order
        store.head = new EventHead(ctx(), 3, 1, 1, "1", "2026-11-23T22:00:00Z");
        for (long s = 1; s <= 3; s++) reader.rows.put(s, row(s));
        ConsoleHttpResponse p1 = route.handle(authGet("/console/v1/transactions/" + TXN + "/events"));
        Map<String, Object> page = page(body(p1));
        ObjectMapper m = new ObjectMapper();
        List<String> keyOrder = new ArrayList<>();
        m.readTree(body(p1)).fieldNames().forEachRemaining(keyOrder::add);
        check("H1-status-and-page-schema", p1.status() == 200 && keyOrder.equals(PAGE_KEYS)
                && page.get("completeThroughSnapshot").equals(true)
                && page.get("retentionTruncated").equals(false)
                && page.get("nextCursor") == null
                && page.get("coveredThrough").equals(3)
                && ((List<Object>) page.get("events")).size() == 3,
                "status=" + p1.status() + " keys=" + keyOrder + " page=" + body(p1));

        // H2 logically expired prefix reported, not skipped
        store.head = new EventHead(ctx(), 5, 3, 1, "1", "2026-11-23T22:00:00Z");
        reader.rows.clear();
        for (long s = 3; s <= 5; s++) reader.rows.put(s, row(s));
        Map<String, Object> p2 = page(body(route.handle(authGet("/console/v1/transactions/" + TXN + "/events"))));
        check("H2-expired-prefix-range",
                p2.get("expiredRanges").toString().equals("[[1, 2]]")
                        && Boolean.TRUE.equals(p2.get("retentionTruncated")) && Boolean.TRUE.equals(p2.get("completeThroughSnapshot")),
                "expiredRanges=" + p2.get("expiredRanges"));
        store.head = new EventHead(ctx(), 3, 1, 1, "1", "2026-11-23T22:00:00Z");
        reader.rows.clear();
        for (long s = 1; s <= 3; s++) reader.rows.put(s, row(s));

        // H3 pagination: frozen snapshot, bounded page, continuation after source growth
        store.head = new EventHead(ctx(), 250, 1, 1, "1", "2026-11-23T22:00:00Z");
        for (long s = 1; s <= 260; s++) reader.rows.put(s, row(s));
        ConsoleHttpResponse q1 = route.handle(authGet("/console/v1/transactions/" + TXN + "/events"));
        Map<String, Object> pg1 = page(body(q1));
        check("H3a-page-capped-at-100", ((List<Object>) pg1.get("events")).size() == 100
                && num(pg1, "coveredThrough") == 100,
                "events=" + ((List<Object>) pg1.get("events")).size());
        String cursor1 = (String) pg1.get("nextCursor");
        check("H3b-next-cursor-issued", cursor1 != null && cursor1.length() > 0, "cursor=" + cursor1);

        // Inspect the signed cursor payload (QA-side decode)
        String[] parts = cursor1.split("\\.");
        byte[] payload = Base64.getUrlDecoder().decode(parts[0]);
        JsonNode cur = m.readTree(payload);
        Set<String> cursorKeys = new HashSet<>();
        cur.fieldNames().forEachRemaining(cursorKeys::add);
        long ttl = cur.get("expiresAt").asLong() - cur.get("issuedAt").asLong();
        check("H3c-cursor-ttl-bound-900", ttl == 900, "ttl=" + ttl);
        Set<String> allowed = Set.of("version", "keyId", "issuer", "subject", "authorizedParty",
                "audience", "environment", "mandateFingerprint", "generation", "acquirerRef",
                "merchantRef", "terminalRef", "transactionId", "snapshotHead", "expiryRevision",
                "firstRetainedSequence", "lastSequence", "passedEvents", "passedBytes",
                "issuedAt", "expiresAt");
        check("H3d-cursor-payload-no-extra-fields", cursorKeys.equals(allowed), "keys=" + cursorKeys);
        check("H3e-cursor-binds-principal-env-mandate-generation",
                cur.get("subject").asText().equals("DEMO-USER")
                        && cur.get("authorizedParty").asText().equals("console-demo")
                        && cur.get("audience").asText().equals("console-api")
                        && cur.get("environment").asText().equals(ENV)
                        && cur.get("acquirerRef").asText().equals(ACQ)
                        && cur.get("merchantRef").asText().equals(MER)
                        && cur.get("transactionId").asText().equals(TXN)
                        && cur.get("snapshotHead").asLong() == 250
                        && cur.get("expiryRevision").asLong() == 1
                        && cur.get("firstRetainedSequence").asLong() == 1
                        && cur.get("issuer").asText().equals(ISSUER)
                        && cur.get("terminalRef").isNull(),
                "payload=" + cur);
        if (!cursorKeys.contains("issuer")) {
            fail++;
            failures.add("DEFECT QA-DEF-1 :: signed cursor payload does not bind the token issuer; "
                    + "D-6 text and wire-fixtures.json cursorUnsignedHarnessBinding both bind issuer. "
                    + "Payload keys=" + cursorKeys);
            System.out.println("DEFECT-CONFIRMED QA-DEF-1 cursor-missing-issuer-binding");
        } else { pass++; System.out.println("PASS H3f-cursor-binds-issuer"); }
        String decoded = payloadToString(payload); boolean noTokenLike = !decoded.contains("eyJ");
        check("H3g-cursor-reveals-no-token-material",
                noTokenLike && cursorKeys.stream().noneMatch(k -> k.matches("(?i)token|jwt")),
                "cursor payload may leak token-like material");

        // Source grows: head moves to 260; continuation must keep frozen H=250
        store.head = new EventHead(ctx(), 260, 1, 1, "1", "2026-11-23T22:00:00Z");
        ConsoleHttpResponse q2 = route.handle(authGet(
                "/console/v1/transactions/" + TXN + "/events?cursor=" + cursor1));
        Map<String, Object> pg2 = page(body(q2));
        check("H3h-continuation-freezes-snapshot-head",
                num(pg2, "snapshotHead") == 250 && num(pg2, "coveredThrough") == 200
                        && ((List<Object>) pg2.get("events")).size() == 100,
                "page=" + body(q2));
        String cursor2 = (String) pg2.get("nextCursor");
        ConsoleHttpResponse q3 = route.handle(authGet(
                "/console/v1/transactions/" + TXN + "/events?cursor=" + cursor2));
        Map<String, Object> pg3 = page(body(q3));
        check("H3i-pagination-completes-through-frozen-head",
                num(pg3, "snapshotHead") == 250 && num(pg3, "coveredThrough") == 250
                        && Boolean.TRUE.equals(pg3.get("completeThroughSnapshot"))
                        && pg3.get("nextCursor") == null,
                "page=" + body(q3));
        boolean beyondFrozen = false;
        for (Object ev : (List<Object>) pg3.get("events")) {
            String s = ev.toString();
            if (s.contains("\"seq\":25") && s.contains("\"seq\":251")) beyondFrozen = true;
        }
        check("H3j-no-rows-beyond-frozen-head", !beyondFrozen, "rows beyond H disclosed");
        boolean sawSeq251 = body(q2).contains("\"seq\":25") && body(q3).contains("\"seq\":251");
        check("H3k-events-251-260-not-disclosed-in-pass", !sawSeq251, "frozen snapshot leaked growth");

        // H4 tampered cursor
        String tampered = cursor1.substring(0, cursor1.length() - 2)
                + (cursor1.endsWith("AA") ? "BB" : "AA");
        ConsoleHttpResponse t = route.handle(authGet(
                "/console/v1/transactions/" + TXN + "/events?cursor=" + tampered));
        check("H4-tampered-cursor-400", t.status() == 400 && body(t).equals(EXPECTED_400),
                "status=" + t.status() + " body=" + body(t));

        // H5 expired cursor -> 410 with action
        clock.advanceSeconds(901);
        ConsoleHttpResponse e = route.handle(authGet(
                "/console/v1/transactions/" + TXN + "/events?cursor=" + cursor2));
        check("H5-expired-cursor-410", e.status() == 410 && body(e).equals(EXPECTED_410),
                "status=" + e.status() + " body=" + body(e));
        clock.now = T0;

        // cursor from the future -> invalid 400
        ConsoleHistoryCursorCodec.Binding future = new ConsoleHistoryCursorCodec.Binding(
                ISSUER, "DEMO-USER", "console-demo", "console-api", ENV, "f", "g1", ACQ, MER,
                null, TXN, 3, 1, 1, 2, 1, 10, T0.plusSeconds(10000), T0.plusSeconds(10900));
        String futureCursor = codec.mint(future, T0.plusSeconds(10000), 900);
        ConsoleHttpResponse f = route.handle(authGet(
                "/console/v1/transactions/" + TXN + "/events?cursor=" + futureCursor));
        check("H6-future-cursor-400", f.status() == 400 && body(f).equals(EXPECTED_400),
                "status=" + f.status() + " body=" + body(f));

        // H7 changed R after mint -> 410
        ConsoleHttpResponse q1b = route.handle(authGet("/console/v1/transactions/" + TXN + "/events"));
        String c1 = (String) page(body(q1b)).get("nextCursor");
        store.head = new EventHead(ctx(), 250, 1, 2, "1", "2026-11-23T22:00:00Z");
        ConsoleHttpResponse r7 = route.handle(authGet(
                "/console/v1/transactions/" + TXN + "/events?cursor=" + c1));
        check("H7-changed-expiry-revision-410",
                r7.status() == 410 && body(r7).equals(EXPECTED_410),
                "status=" + r7.status() + " body=" + body(r7));
        store.head = new EventHead(ctx(), 250, 1, 1, "1", "2026-11-23T22:00:00Z");

        // H8 truncated F after mint -> 410
        String c1b = (String) page(body(route.handle(
                authGet("/console/v1/transactions/" + TXN + "/events")))).get("nextCursor");
        store.head = new EventHead(ctx(), 250, 5, 1, "1", "2026-11-23T22:00:00Z");
        ConsoleHttpResponse r8 = route.handle(authGet(
                "/console/v1/transactions/" + TXN + "/events?cursor=" + c1b));
        check("H8-changed-first-retained-410", r8.status() == 410 && body(r8).equals(EXPECTED_410),
                "status=" + r8.status() + " body=" + body(r8));
        store.head = new EventHead(ctx(), 250, 1, 1, "1", "2026-11-23T22:00:00Z");

        // H9 retired mandate generation (registry mandate content changes) -> 410
        String c1c = (String) page(body(route.handle(
                authGet("/console/v1/transactions/" + TXN + "/events")))).get("nextCursor");
        registry.mandate = new AuthorizedContext(ACQ, MER, "demo-caller",
                Set.of(Route.DOMESTIC, Route.INTERNATIONAL));
        ConsoleHttpResponse r9 = route.handle(authGet(
                "/console/v1/transactions/" + TXN + "/events?cursor=" + c1c));
        check("H9-retired-mandate-410-no-payload",
                r9.status() == 410 && body(r9).equals(EXPECTED_410),
                "status=" + r9.status() + " body=" + body(r9));
        registry.mandate = new AuthorizedContext(ACQ, MER, "demo-caller", Set.of(Route.DOMESTIC));

        // H10 cursor bound to another subject -> 403
        ConsoleHistoryCursorCodec.Binding otherSubject = new ConsoleHistoryCursorCodec.Binding(
                ISSUER, "OTHER-USER", "console-demo", "console-api", ENV, "fff", "g1", ACQ, MER,
                null, TXN, 3, 1, 1, 1, 0, 0, T0, T0.plusSeconds(900));
        ConsoleHttpResponse r10 = route.handle(authGet("/console/v1/transactions/" + TXN
                + "/events?cursor=" + codec.mint(otherSubject, T0, 900)));
        check("H10-wrong-subject-403", r10.status() == 403 && body(r10).equals(EXPECTED_403),
                "status=" + r10.status() + " body=" + body(r10));

        // H11 cursor bound to another transaction -> 403
        ConsoleHistoryCursorCodec.Binding otherTxn = new ConsoleHistoryCursorCodec.Binding(
                ISSUER, "DEMO-USER", "console-demo", "console-api", ENV, "fff", "g1", ACQ, MER,
                null, "TX-999", 3, 1, 1, 1, 0, 0, T0, T0.plusSeconds(900));
        ConsoleHttpResponse r11 = route.handle(authGet("/console/v1/transactions/" + TXN
                + "/events?cursor=" + codec.mint(otherTxn, T0, 900)));
        check("H11-wrong-transaction-403", r11.status() == 403 && body(r11).equals(EXPECTED_403),
                "status=" + r11.status() + " body=" + body(r11));

        // H11b foreign-issuer cursor (same signing key) refused as CONTEXT_DENIED before the read
        ConsoleHistoryCursorCodec.Binding foreign = new ConsoleHistoryCursorCodec.Binding(
                "https://auth.example.invalid/realms/other", "DEMO-USER", "console-demo",
                "console-api", ENV, ConsoleHistoryCursorCodec.mandateFingerprint("demo-caller",
                        ACQ, MER, Set.of("DOMESTIC")), "g1", ACQ, MER, null, TXN,
                3, 1, 1, 1, 0, 0, T0, T0.plusSeconds(900));
        int headBeforeForeign = store.headCalls;
        ConsoleHttpResponse r11b = route.handle(authGet("/console/v1/transactions/" + TXN
                + "/events?cursor=" + codec.mint(foreign, T0, 900)));
        check("H11b-wrong-issuer-403-before-store-read",
                r11b.status() == 403 && body(r11b).equals(EXPECTED_403)
                        && store.headCalls == headBeforeForeign,
                "status=" + r11b.status() + " body=" + body(r11b)
                        + " headDelta=" + (store.headCalls - headBeforeForeign));

        // H11c wrong terminal record in the cursor refused as CONTEXT_DENIED before the read
        ConsoleHistoryCursorCodec.Binding otherTerminal = new ConsoleHistoryCursorCodec.Binding(
                ISSUER, "DEMO-USER", "console-demo", "console-api", ENV,
                ConsoleHistoryCursorCodec.mandateFingerprint("demo-caller", ACQ, MER,
                        Set.of("DOMESTIC")), "g1", ACQ, MER, "TERMINAL-B", TXN,
                3, 1, 1, 1, 0, 0, T0, T0.plusSeconds(900));
        int headBeforeTerminal = store.headCalls;
        ConsoleHttpResponse r11c = route.handle(authGet("/console/v1/transactions/" + TXN
                + "/events?cursor=" + codec.mint(otherTerminal, T0, 900)));
        check("H11c-wrong-terminal-403-before-store-read",
                r11c.status() == 403 && body(r11c).equals(EXPECTED_403)
                        && store.headCalls == headBeforeTerminal,
                "status=" + r11c.status() + " body=" + body(r11c)
                        + " headDelta=" + (store.headCalls - headBeforeTerminal));

        // H11d terminal-specific registry: cursor binds the terminal and the matching terminal passes
        CountingRegistry terminalRegistry = new CountingRegistry();
        terminalRegistry.mandate = new AuthorizedContext(ACQ, MER, "demo-caller", TERMINAL_A,
                Set.of(Route.DOMESTIC));
        FakeStore terminalStore = new FakeStore();
        terminalStore.head = new EventHead(ctx(), 250, 1, 1, "1", "2026-11-23T22:00:00Z");
        ConsoleHistoryRoute terminalRoute = route(clock, terminalStore, reader, terminalRegistry,
                codec);
        ConsoleHttpResponse tp1 = terminalRoute.handle(
                authGet("/console/v1/transactions/" + TXN + "/events"));
        String tCursor = (String) page(body(tp1)).get("nextCursor");
        JsonNode tPayload = m.readTree(Base64.getUrlDecoder()
                .decode(tCursor.split("\\.")[0]));
        check("H11d-terminal-bound-in-payload",
                tPayload.get("terminalRef").asText().equals(TERMINAL_A)
                        && tPayload.get("issuer").asText().equals(ISSUER),
                "payload=" + tPayload);
        ConsoleHttpResponse tp2 = terminalRoute.handle(authGet("/console/v1/transactions/" + TXN
                + "/events?cursor=" + tCursor));
        check("H11d-matching-terminal-and-issuer-continue",
                tp2.status() == 200 && !body(tp2).contains("\"error\"")
                        && terminalStore.appendCalls == 0,
                "status=" + tp2.status() + " body=" + body(tp2));

        // H12 gap halts coverage, suppresses next cursor; exact fixture shape
        store.head = new EventHead(ctx(), 5, 1, 1, "1", "2026-11-23T22:00:00Z");
        reader.rows.clear();
        for (long s : new long[]{1, 2, 3, 5}) reader.rows.put(s, row(s));
        ConsoleHttpResponse g = route.handle(authGet("/console/v1/transactions/" + TXN + "/events"));
        Map<String, Object> gap = page(body(g));
        check("H12-gap-halts-and-suppresses-cursor",
                gap.get("gapRanges").toString().equals("[[4, 4]]")
                        && num(gap, "coveredThrough") == 3
                        && gap.get("nextCursor") == null
                        && Boolean.FALSE.equals(gap.get("completeThroughSnapshot")),
                "page=" + body(g));

        // H13 unknown target -> 404 versioned body
        store.head = null;
        ConsoleHttpResponse r13 = route.handle(authGet("/console/v1/transactions/TX-404/events"));
        check("H13-unknown-target-404", r13.status() == 404 && body(r13).equals(EXPECTED_404),
                "status=" + r13.status() + " body=" + body(r13));
        store.head = new EventHead(ctx(), 5, 1, 1, "1", "2026-11-23T22:00:00Z");
        reader.rows.put(4L, row(4));

        // H14 store failure -> 503 exact reviewed-fixture bytes everywhere it can occur
        store.headFailure = new RuntimeException("dynamo down");
        ConsoleHttpResponse r14 = route.handle(authGet("/console/v1/transactions/" + TXN + "/events"));
        check("H14-store-failure-503-no-payload", r14.status() == 503,
                "status=" + r14.status() + " body=" + body(r14));
        check("H14b-503-code-matches-reviewed-fixture", body(r14).equals(FIXTURE_503),
                "observed " + body(r14));
        store.headFailure = null;

        // H14c read-range failure -> same exact fixture bytes
        reader.failure = new RuntimeException("read range down");
        ConsoleHttpResponse r14c = route.handle(authGet("/console/v1/transactions/" + TXN + "/events"));
        check("H14c-readrange-failure-503-fixture-bytes",
                r14c.status() == 503 && body(r14c).equals(FIXTURE_503),
                "status=" + r14c.status() + " body=" + body(r14c));
        reader.failure = null;

        // H15 registry outage before store read -> 503 fixture bytes
        registry.failure = new RuntimeException("registry down");
        registry.failureOnCall = 1;
        ConsoleHttpResponse r15 = route.handle(authGet("/console/v1/transactions/" + TXN + "/events"));
        check("H15-registry-outage-503-no-payload", r15.status() == 503
                        && body(r15).equals(FIXTURE_503) && !body(r15).contains("events"),
                "status=" + r15.status() + " body=" + body(r15));
        registry.failureOnCall = Integer.MAX_VALUE;
        registry.failure = null;

        // H16 registry outage after store read -> 503, no payload
        final int baseline = registry.calls;
        registry.failure = new RuntimeException("registry down");
        registry.failureOnCall = baseline + 2; // second resolution (after the store read)
        ConsoleHttpResponse r16 = route.handle(authGet("/console/v1/transactions/" + TXN + "/events"));
        check("H16-registry-outage-after-read-503", r16.status() == 503
                        && body(r16).equals(FIXTURE_503) && !body(r16).contains("\"events\""),
                "status=" + r16.status() + " body=" + body(r16));
        registry.failure = null;

        // H17 token expiry between the two authentications -> 401 no payload
        MutableClock clock2 = new MutableClock(T0);
        final int[] authCalls = {0};
        ConsoleAuthorizer expiring = (tok, now) -> {
            authCalls[0]++;
            if (authCalls[0] >= 2) throw new ConsoleAuthenticationException(
                    ConsoleAuthenticationException.Reason.EXPIRED);
            return new ConsolePrincipal(ISSUER, "DEMO-USER", "console-demo", "console-api",
                    Set.of("console.demo"));
        };
        FakeStore store2 = new FakeStore();
        store2.head = new EventHead(ctx(), 3, 1, 1, "1", "2026-11-23T22:00:00Z");
        ConsoleHistoryRoute route2 = new ConsoleHistoryRoute(expiring, registry, store2, reader,
                codec, ENV, 900, clock2);
        ConsoleHttpResponse r17 = route2.handle(authGet("/console/v1/transactions/" + TXN + "/events"));
        check("H17-token-expiry-during-read-401", r17.status() == 401
                        && body(r17).equals(EXPECTED_401) && !body(r17).contains("\"events\"")
                        && store2.headCalls >= 1,
                "status=" + r17.status() + " body=" + body(r17) + " headCalls=" + store2.headCalls);

        // H18 missing / duplicated credential refused before the store read
        FakeStore store3 = new FakeStore();
        store3.head = new EventHead(ctx(), 3, 1, 1, "1", "2026-11-23T22:00:00Z");
        ConsoleHistoryRoute route3 = route(clock, store3, reader, registry, codec);
        ConsoleHttpResponse r18a = route3.handle(req("GET",
                "/console/v1/transactions/" + TXN + "/events", Map.of(), Map.of()));
        int headBefore = store3.headCalls;
        check("H18a-missing-token-401-before-store-read",
                r18a.status() == 401 && body(r18a).equals(EXPECTED_401) && store3.headCalls == 0,
                "status=" + r18a.status() + " body=" + body(r18a) + " headCalls=" + store3.headCalls);
        ConsoleHttpResponse r18b = route3.handle(req("GET",
                "/console/v1/transactions/" + TXN + "/events",
                Map.of("Authorization", List.of("Bearer a", "Bearer b")), Map.of()));
        check("H18b-duplicated-token-401",
                r18b.status() == 401 && store3.headCalls == headBefore,
                "status=" + r18b.status() + " headCalls delta=" + (store3.headCalls - headBefore));

        // H19 wrong method -> 405 empty; unknown path -> empty 404 (transport class)
        ConsoleHttpResponse r19a = route3.handle(req("POST",
                "/console/v1/transactions/" + TXN + "/events",
                Map.of("Authorization", List.of("Bearer demo-token")), Map.of()));
        check("H19a-wrong-method-405", r19a.status() == 405 && body(r19a).isEmpty(),
                "status=" + r19a.status() + " body=" + body(r19a));
        ConsoleHttpResponse r19b = route3.handle(authGet("/console/v1/unknown"));
        check("H19b-unknown-path-empty-404", r19b.status() == 404 && body(r19b).isEmpty(),
                "status=" + r19b.status() + " body=" + body(r19b));

        // H20 malformed pagination inputs -> provisional 400 INVALID_CURSOR mapping
        long checks = 0; long good = 0;
        for (String q : List.of("snapshot=egg", "after=abc", "after=12345678901234567890",
                "bogus=1", "cursor=", "snapshot=new&snapshot=new&after=1")) {
            ConsoleHttpResponse r = route3.handle(authGet(
                    "/console/v1/transactions/" + TXN + "/events?" + q));
            checks++;
            if (r.status() == 400 && body(r).equals(EXPECTED_400)) good++;
        }
        check("H20-malformed-pagination-400", good == checks, good + "/" + checks + " mapped 400");

        // H20b cursor plus after silently ignores after (observation, not pinned by D-6)
        ConsoleHttpResponse r20b = route3.handle(authGet("/console/v1/transactions/" + TXN
                + "/events?cursor=" + c1c + "&after=999"));
        check("H20b-cursor-with-after-continues-or-400", r20b.status() == 400 || r20b.status() == 200
                        || r20b.status() == 410, "status=" + r20b.status());

        // H21 after beyond durable head -> 400, not clamped
        ConsoleHttpResponse r21 = route3.handle(authGet(
                "/console/v1/transactions/" + TXN + "/events?after=999999"));
        check("H21-after-beyond-head-400", r21.status() == 400 && body(r21).equals(EXPECTED_400),
                "status=" + r21.status() + " body=" + body(r21));

        // H22 no store write/dedupe interaction at all (payment side-effect audit)
        check("H22-no-payment-side-effect-store-writes",
                store.appendCalls == 0 && store.findCalls == 0 && store2.appendCalls == 0
                        && store2.findCalls == 0 && terminalStore.appendCalls == 0,
                "appendCalls=" + store.appendCalls + "/" + store2.appendCalls
                        + "/" + terminalStore.appendCalls
                        + " findCalls=" + store.findCalls + "/" + store2.findCalls);

        // H3m open contract questions must not be silently implemented (implementation must stay
        // at the disclosed provisional behaviour until Requirements settles them):
        // (1) no registryMandateId/registryVersion or epoch key in the payload beyond the
        //     disclosed fingerprint representation; (2) cursor combined with after still lets
        //     the cursor win with after silently ignored.
        check("H3m-cursor-open-items-unchanged",
                !cursorKeys.contains("registryMandateId") && !cursorKeys.contains("registryVersion")
                        && !cursorKeys.contains("epoch"),
                "cursorKeys=" + cursorKeys);
        FakeStore store4 = new FakeStore();
        store4.head = new EventHead(ctx(), 250, 1, 1, "1", "2026-11-23T22:00:00Z");
        for (long s = 6; s <= 260; s++) reader.rows.put(s, row(s));
        ConsoleHistoryRoute route4 = route(clock, store4, reader, registry, codec);
        String freshCursor = (String) page(body(route4.handle(
                authGet("/console/v1/transactions/" + TXN + "/events")))).get("nextCursor");
        ConsoleHttpResponse rCombine = route4.handle(authGet("/console/v1/transactions/" + TXN
                + "/events?cursor=" + freshCursor + "&after=999"));
        check("H3n-cursor-combined-with-after-still-cursor-wins",
                freshCursor != null && rCombine.status() == 200
                        && page(body(rCombine)).get("coveredThrough").equals(200),
                "status=" + rCombine.status() + " body=" + body(rCombine));

        // H23 route refuses a cursor TTL above the contract bound
        boolean ttlRefused;
        try { new ConsoleHistoryRoute((tok, now) -> null, registry, store, reader, codec, ENV, 901, clock);
            ttlRefused = false; }
        catch (RuntimeException ttlErr) { ttlRefused = true; }
        check("H23-cursor-ttl-capped-at-900", ttlRefused, "ttl=901 accepted");
    }

    static String payloadToString(byte[] payload) {
        return new String(payload, StandardCharsets.UTF_8);
    }
}
