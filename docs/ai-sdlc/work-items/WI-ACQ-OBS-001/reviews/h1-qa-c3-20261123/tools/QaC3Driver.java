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
import com.fasterxml.jackson.databind.node.ObjectNode;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.*;

/**
 * Independent QA driver — D-6 C-3 malformed-pagination taxonomy and failure order
 * (ai-sdlc-qa 2026-11-23, commission reviews/h1-qa-c3-20261123).
 *
 * Oracle: C-3.1–C-3.4 and the C-3.3/§7 order of
 * design/contract-deltas/d6-clarifications-rev2-20261123/d6-clarifications.md
 * (SHA-256 3aaac88a…9643a3), bodies byte-checked against
 * continuation-rev3-20261123/wire-fixtures.json historyErrors.
 * Runs only against compiled main classes and QA-owned fakes. Never a product test.
 */
public class QaC3Driver {

    static int pass = 0, fail = 0;
    static final List<String> failures = new ArrayList<>();

    static final ObjectMapper MAPPER = new ObjectMapper();

    static final Instant T0 = Instant.parse("2026-11-23T22:00:00Z");
    static final String ISSUER = "https://auth.example.invalid/realms/acq-DEMO";
    static final String ENV = "DEMO", ACQ = "ACQ-A", MER = "MERCHANT-A", TXN = "TX-001";
    static final byte[] KEY32 = "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8);

    // Hardcoded expected bodies; independently re-derived from the fixture file at start-up (S0).
    static final String B400 = "{\"version\":1,\"error\":\"INVALID_CURSOR\",\"action\":null}";
    static final String B401 = "{\"version\":1,\"error\":\"UNAUTHENTICATED\",\"action\":null}";
    static final String B403 = "{\"version\":1,\"error\":\"CONTEXT_DENIED\",\"action\":null}";
    static final String B404 = "{\"version\":1,\"error\":\"NOT_FOUND\",\"action\":null}";
    static final String B410 = "{\"version\":1,\"error\":\"RESYNC_REQUIRED\",\"action\":\"RESYNC_REQUIRED\"}";
    static final String B503 = "{\"version\":1,\"error\":\"AUTHORIZATION_UNAVAILABLE\",\"action\":null}";
    static final Map<Integer, String> FIXTURE = new HashMap<>();

    public static void main(String[] args) throws Exception {
        fixtureLoad();
        orderSuite();
        System.out.println();
        System.out.println("QA-C3-DRIVER RESULT: pass=" + pass + " fail=" + fail);
        for (String f : failures) System.out.println("FAILED: " + f);
        if (fail > 0) System.exit(1);
    }

    // ------------------------------------------------------------- fixture oracle (S0)

    static void fixtureLoad() throws Exception {
        File f = new File("docs/ai-sdlc/work-items/WI-ACQ-OBS-001/design/contract-deltas/"
                + "continuation-rev3-20261123/wire-fixtures.json");
        JsonNode he = MAPPER.readTree(f).get("historyErrors");
        for (String status : List.of("400", "401", "403", "404", "410", "503")) {
            JsonNode e = he.get(status);
            StringBuilder b = new StringBuilder("{\"version\":").append(e.get("version")).append(",\"error\":");
            b.append(MAPPER.writeValueAsString(e.get("error").asText())).append(",\"action\":");
            JsonNode a = e.get("action");
            b.append(a.isNull() ? "null" : MAPPER.writeValueAsString(a.asText())).append('}');
            FIXTURE.put(Integer.parseInt(status), b.toString());
        }
        check("S0-fixture-bodies-match-reviewed-packet",
                FIXTURE.get(400).equals(B400) && FIXTURE.get(401).equals(B401)
                        && FIXTURE.get(403).equals(B403) && FIXTURE.get(404).equals(B404)
                        && FIXTURE.get(410).equals(B410) && FIXTURE.get(503).equals(B503),
                "parsed=" + FIXTURE);
    }

    // ------------------------------------------------------------- fakes

    static final class MutableClock extends Clock {
        volatile Instant now = T0;
        @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId z) { return this; }
        @Override public Instant instant() { return now; }
    }

    static final class FakeStore implements com.acquiring.simulator.domain.port.EventStore {
        volatile EventHead head;
        volatile RuntimeException headFailure;
        int headCalls;
        @Override public com.acquiring.simulator.domain.event.EventAppendResult append(
                com.acquiring.simulator.domain.event.EventSubmission s) {
            throw new AssertionError("history must not append");
        }
        @Override public Optional<com.acquiring.simulator.domain.event.StoredEvent> find(
                com.acquiring.simulator.domain.event.EventDedupeKey k) {
            throw new AssertionError("history must not dedupe-probe");
        }
        @Override public Optional<EventHead> head(EventContext c) {
            headCalls++;
            if (headFailure != null) throw headFailure;
            return Optional.ofNullable(head);
        }
    }

    static final class Reg implements ConsoleContextRegistry {
        volatile AuthorizedContext mandate =
                new AuthorizedContext(ACQ, MER, "demo-caller", Set.of(Route.DOMESTIC));
        volatile AuthorizedContext secondMandate; // returned from the 2nd call onward (post-read)
        volatile RuntimeException failure;
        int calls;
        @Override public AuthorizedContext contextFor(ConsolePrincipal p) {
            calls++;
            if (failure != null) throw failure;
            if (calls >= 2 && secondMandate != null) return secondMandate;
            return mandate;
        }
    }

    static final class FakeReader implements EventHistoryReader {
        final Map<Long, RecordedEvent> rows = new TreeMap<>();
        int calls;
        @Override public List<RecordedEvent> readRange(EventContext c, long after, long through, int limit) {
            calls++;
            List<RecordedEvent> out = new ArrayList<>();
            for (Map.Entry<Long, RecordedEvent> e : new TreeMap<>(rows).entrySet()) {
                long s = e.getKey();
                if (s > after && s <= through && out.size() < limit) out.add(e.getValue());
            }
            return out;
        }
    }

    record Outcome(int status, String body, int headDelta, int readDelta, int regDelta) {}

    static Outcome run(ConsoleHistoryRoute route, ConsoleHttpRequest req,
            FakeStore st, FakeReader rd, Reg rg) {
        int h0 = st.headCalls, r0 = rd.calls, g0 = rg.calls;
        ConsoleHttpResponse resp = route.handle(req);
        return new Outcome(resp.status(), body(resp), st.headCalls - h0, rd.calls - r0, rg.calls - g0);
    }

    static String body(ConsoleHttpResponse r) { return new String(r.body(), StandardCharsets.UTF_8); }

    // ------------------------------------------------------------- scenario builders

    static ConsoleHistoryRoute route(MutableClock clock, FakeStore st, FakeReader rd, Reg rg,
            ConsoleHistoryCursorCodec codec) {
        ConsoleAuthorizer auth = (token, now) -> {
            if (token == null || token.isBlank()) throw new ConsoleAuthenticationException(
                    ConsoleAuthenticationException.Reason.MISSING_TOKEN);
            return new ConsolePrincipal(ISSUER, "DEMO-USER", "console-demo", "console-api",
                    Set.of("console.demo"));
        };
        return new ConsoleHistoryRoute(auth, rg, st, rd, codec, ENV, 900, clock);
    }

    static ConsoleHttpRequest get(Map<String, List<String>> query) {
        return new ConsoleHttpRequest("GET",
                "/console/v1/transactions/" + TXN + "/events",
                Map.of("Authorization", List.of("Bearer demo-token")), new byte[0], query);
    }

    static EventContext ctx() { return new EventContext(ENV, ACQ, MER, TXN); }

    static EventHead head(long h, long r, long f) {
        return new EventHead(ctx(), h, r, f, "1", "2026-11-23T22:00:00Z");
    }

    static RecordedEvent row(long seq) {
        EventSourceWrapper w = new EventSourceWrapper(EventDurability.CRITICAL_COMMITTED,
                "sha256-" + seq, 1L, "2026-11-23T22:00:00Z", "2026-11-23T22:00:01Z",
                seq, "2026-11-23T22:00:02Z", "AUTHORIZED");
        return new RecordedEvent(w, "{\"seq\":" + seq + "}");
    }

    static ConsoleHistoryCursorCodec codec() { return new ConsoleHistoryCursorCodec("qa-key-1", KEY32); }

    /** A state where a fresh snapshot yields a next cursor: head 250, rows 1..260. */
    static void primeContinuationSource(FakeStore st, FakeReader rd) {
        st.head = head(250, 1, 1);
        rd.rows.clear();
        for (long s = 1; s <= 260; s++) rd.rows.put(s, row(s));
    }

    static String mintCursor(ConsoleHistoryRoute route) {
        ConsoleHttpResponse p = route.handle(get(Map.of()));
        if (p.status() != 200) throw new AssertionError("page mint failed: " + p.status() + " " + body(p));
        JsonNode n = null;
        try { n = MAPPER.readTree(body(p)).get("nextCursor"); }
        catch (Exception e) { throw new RuntimeException(e); }
        if (n == null || n.isNull()) throw new AssertionError("no next cursor minted");
        return n.asText();
    }

    static ObjectNode payloadOf(String cursor) throws Exception {
        byte[] b = Base64.getUrlDecoder().decode(cursor.split("\\.")[0]);
        return (ObjectNode) MAPPER.readTree(b);
    }

    static String reSign(ObjectNode node) throws Exception {
        byte[] bytes = MAPPER.writeValueAsBytes(node);
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(KEY32, "HmacSHA256"));
        Base64.Encoder e = Base64.getUrlEncoder().withoutPadding();
        return e.encodeToString(bytes) + "." + e.encodeToString(mac.doFinal(bytes));
    }

    static String reSign(String rawBytes) throws Exception {
        byte[] bytes = rawBytes.getBytes(StandardCharsets.UTF_8);
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(KEY32, "HmacSHA256"));
        Base64.Encoder e = Base64.getUrlEncoder().withoutPadding();
        return e.encodeToString(bytes) + "." + e.encodeToString(mac.doFinal(bytes));
    }

    static void check(String id, boolean ok, String detail) {
        if (ok) { pass++; System.out.println("PASS " + id); }
        else { fail++; failures.add(id + " :: " + detail); System.out.println("FAIL " + id + " :: " + detail); }
    }

    // ------------------------------------------------------------- order suite

    static void orderSuite() throws Exception {
        MutableClock clock = new MutableClock();
        FakeStore st = new FakeStore();
        FakeReader rd = new FakeReader();
        Reg rg = new Reg();
        ConsoleHistoryRoute route = route(clock, st, rd, rg, codec());

        // ---- Step 1: 401 still precedes the request-form step (C-2.3)
        Outcome oEmpty = run(route, new ConsoleHttpRequest("GET",
                "/console/v1/transactions/" + TXN + "/events", Map.of(), new byte[0],
                Map.of("after", List.of("abc"))), st, rd, rg);
        check("U-missing-token-with-malformed-form-is-401-first",
                oEmpty.status() == 401 && oEmpty.body().equals(B401) && oEmpty.regDelta() == 0
                        && oEmpty.headDelta() == 0 && oEmpty.readDelta() == 0,
                "status=" + oEmpty.status() + " body=" + oEmpty.body() + " reg=" + oEmpty.regDelta());
        Outcome oDup = run(route, new ConsoleHttpRequest("GET",
                "/console/v1/transactions/" + TXN + "/events",
                Map.of("Authorization", List.of("Bearer a", "Bearer b")), new byte[0],
                Map.of("after", List.of("abc"))), st, rd, rg);
        check("U-duplicated-token-with-malformed-form-is-401-first",
                oDup.status() == 401 && oDup.body().equals(B401) && oDup.regDelta() == 0,
                "status=" + oDup.status() + " body=" + oDup.body());

        // ---- Step 2: request-form 400, syntactic only, before any registry/store call
        st.head = head(3, 1, 1);
        for (long s = 1; s <= 3; s++) rd.rows.put(s, row(s));

        List<Map<String, List<String>>> formCases = List.of(
            Map.of("snapshot", List.of("old")),
            Map.of("snapshot", List.of("nope")),
            Map.of("after", List.of("abc")),
            Map.of("after", List.of("-1")),
            Map.of("after", List.of("")),
            Map.of("after", List.of("9999999999999")),
            Map.of("snapshot", List.of("new"), "bogus", List.of("1")),
            Map.of("after", List.of("1", "2")),
            Map.of("cursor", List.of("")),
            Map.of("cursor", List.of("abc"), "after", List.of("1")),
            Map.of("cursor", List.of("abc"), "snapshot", List.of("new"))
        );
        String[] formIds = { "snapshot=old", "snapshot=nope", "after=abc", "after=-1", "after=empty",
                "after=13digits", "unknown-param", "after-repeated", "cursor-empty",
                "cursor+after", "cursor+snapshot" };
        int i = 0;
        for (Map<String, List<String>> q : formCases) {
            Outcome o = run(route, get(q), st, rd, rg);
            check("F-" + formIds[i] + "-400-form-step-zero-calls",
                    o.status() == 400 && o.body().equals(B400)
                            && o.regDelta() == 0 && o.headDelta() == 0 && o.readDelta() == 0,
                    "status=" + o.status() + " body=" + o.body()
                            + " reg=" + o.regDelta() + " head=" + o.headDelta() + " read=" + o.readDelta());
            i++;
        }
        // A long but syntactically valid after (12 digits > head 3) must NOT be a form refusal:
        // it needs the head, so it must be judged at the C-3.4 step (checked at H-after>H).
        Outcome o12 = run(route, get(Map.of("after", List.of("100000000000"))), st, rd, rg);
        check("F-after-12digits-not-a-form-refusal",
                o12.status() == 400 && o12.body().equals(B400) && o12.headDelta() == 1 && o12.readDelta() == 0,
                "status=" + o12.status() + " head=" + o12.headDelta() + " read=" + o12.readDelta());

        // form step wins over a registry outage (resolution comes later)
        rg.failure = new RuntimeException("registry down");
        Outcome oFo = run(route, get(Map.of("after", List.of("abc"))), st, rd, rg);
        rg.failure = null;
        check("F-form-wins-over-registry-outage",
                oFo.status() == 400 && oFo.body().equals(B400) && oFo.regDelta() == 0
                        && oFo.headDelta() == 0 && oFo.readDelta() == 0,
                "status=" + oFo.status() + " reg=" + oFo.regDelta());

        // ---- Step 3: resolution 503 / 403, before any cursor parse and before the store
        rg.failure = new RuntimeException("registry down");
        Outcome o503 = run(route, get(Map.of()), st, rd, rg);
        Outcome o503c = run(route, get(Map.of("cursor", List.of("garbage-no-dot"))), st, rd, rg);
        rg.failure = null;
        check("R-registry-outage-is-503-before-store-and-cursor",
                o503.status() == 503 && o503.body().equals(B503) && o503.headDelta() == 0
                        && o503.readDelta() == 0,
                "status=" + o503.status() + " body=" + o503.body() + " head=" + o503.headDelta());
        check("R-resolution-precedes-malformed-cursor-parse",
                o503c.status() == 503 && o503c.body().equals(B503),
                "status=" + o503c.status() + " body=" + o503c.body());

        rg.mandate = null;
        Outcome o403 = run(route, get(Map.of()), st, rd, rg);
        Outcome o403c = run(route, get(Map.of("cursor", List.of("garbage-no-dot"))), st, rd, rg);
        rg.mandate = new AuthorizedContext(ACQ, MER, "demo-caller", Set.of(Route.DOMESTIC));
        check("R-missing-mandate-is-403-before-store-and-cursor",
                o403.status() == 403 && o403.body().equals(B403) && o403.headDelta() == 0
                        && o403.readDelta() == 0,
                "status=" + o403.status() + " body=" + o403.body() + " head=" + o403.headDelta());
        check("R-resolution-precedes-malformed-cursor-parse-403",
                o403c.status() == 403 && o403c.body().equals(B403),
                "status=" + o403c.status() + " body=" + o403c.body());

        // ---- Mint a real cursor and its payload for the cursor-refusal steps
        primeContinuationSource(st, rd);
        String base = mintCursor(route);
        ObjectNode payload = payloadOf(base);

        // ---- Step 4: malformed payload / bad signature 400
        Outcome om = run(route, get(Map.of("cursor", List.of(reSign("this is not json")))), st, rd, rg);
        check("M-non-json-signed-payload-is-400-zero-calls",
                om.status() == 400 && om.body().equals(B400) && om.headDelta() == 0 && om.readDelta() == 0,
                "status=" + om.status() + " body=" + om.body() + " head=" + om.headDelta());

        ObjectNode wrongType = payload.deepCopy().put("version", "banana");
        Outcome owt = run(route, get(Map.of("cursor", List.of(reSign(wrongType)))), st, rd, rg);
        check("M-wrongly-typed-version-is-malformed-400-not-410",
                owt.status() == 400 && owt.body().equals(B400) && owt.headDelta() == 0,
                "status=" + owt.status() + " body=" + owt.body());

        int dot = base.indexOf('.');
        char firstSig = base.charAt(dot + 1);
        String tampered = base.substring(0, dot + 1)
                + (firstSig == 'A' ? 'B' : 'A') + base.substring(dot + 2);
        Outcome ot = run(route, get(Map.of("cursor", List.of(tampered))), st, rd, rg);
        check("M-tampered-signature-is-400-zero-calls",
                ot.status() == 400 && ot.body().equals(B400) && ot.headDelta() == 0 && ot.readDelta() == 0,
                "status=" + ot.status() + " body=" + ot.body() + " head=" + ot.headDelta());

        // ---- Step 5: retired schema 410 BEFORE the context 403 (C-1.5 + C-3.3, stated precedence)
        ObjectNode retired = payload.deepCopy().put("version", 2).put("subject", "EVIL-USER");
        Outcome orr = run(route, get(Map.of("cursor", List.of(reSign(retired)))), st, rd, rg);
        check("T-retired-schema-is-410-not-403-even-with-wrong-context",
                orr.status() == 410 && orr.body().equals(B410) && orr.headDelta() == 0 && orr.readDelta() == 0,
                "status=" + orr.status() + " body=" + orr.body() + " head=" + orr.headDelta());

        ObjectNode noVersion = payload.deepCopy(); noVersion.remove("version");
        Outcome onv = run(route, get(Map.of("cursor", List.of(reSign(noVersion)))), st, rd, rg);
        check("T-missing-version-is-retired-410",
                onv.status() == 410 && onv.body().equals(B410) && onv.headDelta() == 0,
                "status=" + onv.status() + " body=" + onv.body());

        ObjectNode noIssuer = payload.deepCopy(); noIssuer.remove("issuer");
        Outcome oni = run(route, get(Map.of("cursor", List.of(reSign(noIssuer)))), st, rd, rg);
        check("T-missing-required-member-is-retired-410-C1.5",
                oni.status() == 410 && oni.body().equals(B410) && oni.headDelta() == 0,
                "status=" + oni.status() + " body=" + oni.body());

        // ---- Step 6: expired 410 BEFORE the context 403
        ObjectNode foreign = payload.deepCopy().put("transactionId", "TX-OTHER");
        String foreignCursor = reSign(foreign);
        clock.now = T0.plusSeconds(901);
        Outcome ox = run(route, get(Map.of("cursor", List.of(foreignCursor))), st, rd, rg);
        clock.now = T0;
        check("X-expired-cursor-is-410-not-403-zero-calls",
                ox.status() == 410 && ox.body().equals(B410) && ox.headDelta() == 0 && ox.readDelta() == 0,
                "status=" + ox.status() + " body=" + ox.body() + " head=" + ox.headDelta());

        // ---- Step 7: context 403 before the changed-representation 410 (pre-read)
        ObjectNode both = payload.deepCopy().put("subject", "EVIL-USER")
                .put("mandateFingerprint", "f".repeat(64));
        Outcome ob = run(route, get(Map.of("cursor", List.of(reSign(both)))), st, rd, rg);
        check("C-context-mismatch-wins-over-representation-410",
                ob.status() == 403 && ob.body().equals(B403) && ob.headDelta() == 0 && ob.readDelta() == 0,
                "status=" + ob.status() + " body=" + ob.body());

        ObjectNode fpOnly = payload.deepCopy().put("mandateFingerprint", "e".repeat(64));
        Outcome ofp = run(route, get(Map.of("cursor", List.of(reSign(fpOnly)))), st, rd, rg);
        check("C-representation-only-mismatch-is-410",
                ofp.status() == 410 && ofp.body().equals(B410) && ofp.headDelta() == 0,
                "status=" + ofp.status() + " body=" + ofp.body());

        // ---- Step 9: head 503 / 404 precede the R/F and bound-lastSequence steps
        ObjectNode changedR = payload.deepCopy().put("expiryRevision", 7);
        st.headFailure = new RuntimeException("store down");
        Outcome ohf = run(route, get(Map.of("cursor", List.of(reSign(changedR)))), st, rd, rg);
        st.headFailure = null;
        check("H-head-outage-is-503-before-RF-compare",
                ohf.status() == 503 && ohf.body().equals(B503) && ohf.readDelta() == 0 && ohf.headDelta() == 1,
                "status=" + ohf.status() + " body=" + ohf.body() + " head=" + ohf.headDelta()
                        + " read=" + ohf.readDelta());

        EventHead keep = st.head;
        st.head = null;
        ObjectNode changedR2 = payload.deepCopy().put("expiryRevision", 7).put("lastSequence", 251);
        Outcome ohn = run(route, get(Map.of("cursor", List.of(reSign(changedR2)))), st, rd, rg);
        st.head = keep;
        check("H-unknown-target-is-404-before-RF-and-lastSequence",
                ohn.status() == 404 && ohn.body().equals(B404) && ohn.readDelta() == 0 && ohn.headDelta() == 1,
                "status=" + ohn.status() + " body=" + ohn.body() + " head=" + ohn.headDelta());

        // ---- Step 10: after > H, only after exactly one head read, zero reader calls (C-3.4)
        st.head = head(3, 1, 1);
        rd.rows.clear();
        for (long s = 1; s <= 3; s++) rd.rows.put(s, row(s));
        Outcome oah = run(route, get(Map.of("after", List.of("4"))), st, rd, rg);
        check("A-after-beyond-head-400-after-one-head-read-zero-reader",
                oah.status() == 400 && oah.body().equals(B400) && oah.headDelta() == 1
                        && oah.readDelta() == 0 && oah.regDelta() >= 1,
                "status=" + oah.status() + " body=" + oah.body() + " head=" + oah.headDelta()
                        + " read=" + oah.readDelta() + " reg=" + oah.regDelta());

        st.headFailure = new RuntimeException("store down");
        Outcome oahf = run(route, get(Map.of("after", List.of("4"))), st, rd, rg);
        st.headFailure = null;
        check("A-head-outage-outranks-after-beyond-head",
                oahf.status() == 503 && oahf.body().equals(B503),
                "status=" + oahf.status() + " body=" + oahf.body());

        EventHead keep3 = st.head;
        st.head = null;
        Outcome oahu = run(route, get(Map.of("after", List.of("4"))), st, rd, rg);
        st.head = keep3;
        check("A-unknown-target-outranks-after-beyond-head",
                oahu.status() == 404 && oahu.body().equals(B404),
                "status=" + oahu.status() + " body=" + oahu.body());

        // ---- Step 11/12: R/F 410 then bound-lastSequence 400, both after the head read
        rd.rows.clear();
        ObjectNode cR = payload.deepCopy().put("expiryRevision", 7);
        Outcome orf = run(route, get(Map.of("cursor", List.of(reSign(cR)))), st, rd, rg);
        check("RF-changed-expiry-revision-is-410-after-head-read",
                orf.status() == 410 && orf.body().equals(B410) && orf.readDelta() == 0 && orf.headDelta() >= 1,
                "status=" + orf.status() + " body=" + orf.body() + " head=" + orf.headDelta());

        ObjectNode cL = payload.deepCopy().put("lastSequence", 251);
        st.head = head(250, 1, 1); // match the bound H/R/F so only the lastSequence clause can fire
        Outcome ol = run(route, get(Map.of("cursor", List.of(reSign(cL)))), st, rd, rg);
        check("L-bound-lastSequence-beyond-head-is-400-after-head-read",
                ol.status() == 400 && ol.body().equals(B400) && ol.readDelta() == 0 && ol.headDelta() >= 1,
                "status=" + ol.status() + " body=" + ol.body() + " head=" + ol.headDelta());

        // ---- Step 13: the 200 page still closes the order
        st.head = head(250, 1, 1);
        for (long s = 1; s <= 260; s++) rd.rows.put(s, row(s));
        Outcome ok200 = run(route, get(Map.of("cursor", List.of(base))), st, rd, rg);
        check("Z-continuation-still-returns-200-page",
                ok200.status() == 200 && ok200.body().contains("\"events\""),
                "status=" + ok200.status());

        // ---- Post-read order (C-1.3): context 403 before representation 410 after the read
        primeContinuationSource(st, rd);
        rg.secondMandate = null;
        String base2 = mintCursor(route);

        rg.secondMandate = new AuthorizedContext("ACQ-EVIL", MER, "other-caller", Set.of(Route.DOMESTIC));
        Outcome p1 = run(route, get(Map.of("cursor", List.of(base2))), st, rd, rg);
        rg.secondMandate = null;
        check("P-post-read-context-denied-wins-over-retired-generation",
                p1.status() == 403 && p1.body().equals(B403),
                "status=" + p1.status() + " body=" + p1.body());

        rg.secondMandate = new AuthorizedContext(ACQ, MER, "other-caller", Set.of(Route.DOMESTIC));
        Outcome p2 = run(route, get(Map.of("cursor", List.of(base2))), st, rd, rg);
        rg.secondMandate = null;
        check("P-post-read-representation-only-mismatch-is-410",
                p2.status() == 410 && p2.body().equals(B410),
                "status=" + p2.status() + " body=" + p2.body());

        // ---- Closed set: the driver's body literals remain equal to the reviewed fixture values
        check("S-closed-error-body-set",
                B400.equals(FIXTURE.get(400)) && B401.equals(FIXTURE.get(401))
                        && B403.equals(FIXTURE.get(403)) && B404.equals(FIXTURE.get(404))
                        && B410.equals(FIXTURE.get(410)) && B503.equals(FIXTURE.get(503)),
                "fixture mapping mismatch");
    }
}
