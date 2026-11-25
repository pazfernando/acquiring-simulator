package com.acquiring.simulator.console;

import com.acquiring.simulator.domain.event.EventContext;
import com.acquiring.simulator.domain.event.EventHead;
import com.acquiring.simulator.domain.event.EventTableKeys;
import com.acquiring.simulator.domain.event.RecordedEvent;
import com.acquiring.simulator.domain.model.AuthorizedContext;
import com.acquiring.simulator.domain.port.EventHistoryReader;
import com.acquiring.simulator.domain.port.EventStore;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Read-only HTTP history route: {@code GET /console/v1/transactions/{id}/events}.
 *
 * <p>It reads the existing functional event source only. There is no append, no projection, no
 * transaction creation, no TTL or compaction, no WebSocket dependency and no payment gateway or
 * dispatch port in its construction: a page is observation, never payment authority.</p>
 *
 * <h2>Fixed snapshot</h2>
 * <p>A new read resolves a strong HEAD for the environment-bound business partition and freezes
 * {@code H} (last sequence), {@code R} (expiry revision) and {@code F} (first retained sequence).
 * Every page continues that same snapshot from a signed opaque cursor; an append that arrives later
 * does not extend a page. A changed {@code R} or {@code F}, a snapshot head that no longer exists or
 * a retired mandate generation returns {@code 410 RESYNC_REQUIRED}.</p>
 *
 * <h2>Re-authentication</h2>
 * <p>The route authenticates and resolves the current mandate before the store read and again after
 * it, before any payload is disclosed. A token that expires during the read is refused with
 * {@code 401}; an unavailable registry is {@code 503} with no page payload.</p>
 *
 * <h2>Coverage</h2>
 * <p>{@code expiredRanges} reports the retained prefix that is logically expired ({@code 1..F-1}).
 * A missing retained row is a {@code gapRanges} hole that halts {@code coveredThrough} and ends the
 * pass; the route never skips a hole and never infers completeness.</p>
 */
public final class ConsoleHistoryRoute {

    /** Route method. */
    public static final String METHOD = "GET";

    /** Route path prefix. */
    public static final String PATH_PREFIX = "/console/v1/transactions/";

    /** Route path suffix. */
    public static final String PATH_SUFFIX = "/events";

    /** Maximum events in one page (D-6). */
    public static final int MAX_EVENTS_PER_PAGE = 100;

    /** Maximum bytes in one page (D-6). */
    public static final int MAX_PAGE_BYTES = 256 * 1024;

    /** Maximum events in one pass (D-6). */
    public static final int MAX_PASS_EVENTS = 1000;

    /** Maximum bytes in one pass (D-6). */
    public static final int MAX_PASS_BYTES = 1024 * 1024;

    /** Maximum cursor lifetime (D-6). */
    public static final int MAX_CURSOR_TTL_SECONDS = 900;

    private final ConsoleAuthorizer authorizer;
    private final ConsoleContextRegistry contexts;
    private final EventStore store;
    private final EventHistoryReader reader;
    private final ConsoleHistoryCursorCodec cursors;
    private final String environment;
    private final int cursorTtlSeconds;
    private final Clock clock;

    public ConsoleHistoryRoute(ConsoleAuthorizer authorizer, ConsoleContextRegistry contexts,
            EventStore store, EventHistoryReader reader, ConsoleHistoryCursorCodec cursors,
            String environment, int cursorTtlSeconds, Clock clock) {
        this.authorizer = Objects.requireNonNull(authorizer,
                "a history route requires the token authorizer seam");
        this.contexts = Objects.requireNonNull(contexts,
                "a history route requires the trusted context registry seam");
        this.store = Objects.requireNonNull(store, "a history route requires the event store head");
        this.reader = Objects.requireNonNull(reader, "a history route requires the event reader");
        this.cursors = Objects.requireNonNull(cursors, "a history route requires the cursor codec");
        if (environment == null || environment.isBlank()) {
            throw new IllegalArgumentException(
                    "history route requires the trusted deployment environment");
        }
        if (cursorTtlSeconds < 1 || cursorTtlSeconds > MAX_CURSOR_TTL_SECONDS) {
            throw new IllegalArgumentException(
                    "cursor ttl must be 1.." + MAX_CURSOR_TTL_SECONDS + " seconds");
        }
        this.environment = environment;
        this.cursorTtlSeconds = cursorTtlSeconds;
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Handle one history request. */
    public ConsoleHttpResponse handle(ConsoleHttpRequest request) {
        Objects.requireNonNull(request, "request");
        String transactionId = transactionIdOf(request.path());
        if (transactionId == null) {
            return ConsoleHttpResponse.empty(404);
        }
        if (!METHOD.equals(request.method())) {
            return ConsoleHttpResponse.empty(405);
        }

        Instant now = clock.instant();
        ConsolePrincipal principal;
        try {
            principal = authenticate(request, now);
        } catch (ConsoleAuthenticationException refused) {
            return error(401, "UNAUTHENTICATED", null);
        }

        ConsoleHistoryQuery query;
        try {
            query = queryOf(request);
        } catch (InvalidRequest invalid) {
            // C-3.1: a syntactically unusable pagination form is 400 INVALID_CURSOR, null action.
            // This step is before mandate resolution and any store read.
            return error(400, "INVALID_CURSOR", null);
        }

        AuthorizedContext mandate;
        try {
            mandate = requireMandate(principal);
        } catch (RegistryUnavailable unavailable) {
            return error(503, "AUTHORIZATION_UNAVAILABLE", null);
        }
        if (mandate == null) {
            return error(403, "CONTEXT_DENIED", null);
        }

        try {
            return switch (query) {
                case ConsoleHistoryQuery.NewSnapshot snapshot ->
                        newSnapshot(request, principal, mandate, transactionId, snapshot, now);
                case ConsoleHistoryQuery.Continue continuation ->
                        continuation(request, principal, mandate, transactionId, continuation, now);
            };
        } catch (HistoryRefusal refusal) {
            return refusal.response();
        }
    }

    // ------------------------------------------------------------------ new snapshot

    private ConsoleHttpResponse newSnapshot(ConsoleHttpRequest request, ConsolePrincipal principal,
            AuthorizedContext mandate, String transactionId, ConsoleHistoryQuery.NewSnapshot query,
            Instant now) {
        EventContext context = contextOf(mandate, transactionId);
        EventHead head = requireHead(context);
        if (head == null) {
            // No retained event exists for the mandated partition: unknown target, nothing disclosed.
            return error(404, "NOT_FOUND", null);
        }
        // C-3.4: `after > H` is the one head-dependent pagination refusal. It MUST stay here —
        // after the head read and its 503/404 step, and before any page/range read. Refusing it in
        // the request-form step would require knowing the frozen head before it is read (and would
        // let a malformed form bypass the head outage 503); refusing it later would let a reader
        // call run on a position the contract does not define.
        if (query.afterSequence() > head.lastSequence()) {
            // The client claims coverage beyond the durable head; that cannot be true.
            return error(400, "INVALID_CURSOR", null);
        }

        String fingerprint = fingerprint(mandate);
        ConsoleHistoryCursorCodec.Binding binding = new ConsoleHistoryCursorCodec.Binding(
                principal.issuer(), principal.subject(), principal.authorizedParty(),
                principal.audience(), environment, fingerprint, UUID.randomUUID().toString(),
                mandate.acquirerRef(), mandate.merchantRef(), mandate.terminalRef(), transactionId,
                head.lastSequence(), head.expiryRevision(), head.firstRetainedSequence(),
                query.afterSequence(), 0, 0, now, now.plusSeconds(cursorTtlSeconds));

        return page(request, principal, mandate, context, binding, query.afterSequence(), now);
    }

    // ------------------------------------------------------------------ continuation

    private ConsoleHttpResponse continuation(ConsoleHttpRequest request, ConsolePrincipal principal,
            AuthorizedContext mandate, String transactionId,
            ConsoleHistoryQuery.Continue query, Instant now) {
        ConsoleHistoryCursorCodec.Binding binding;
        try {
            binding = cursors.open(query.opaqueCursor(), now);
        } catch (ConsoleHistoryCursorCodec.CursorException refused) {
            // C-3.3 order: malformed/tampered handle is 400; a signature-valid retired schema is
            // 410 here, BEFORE any context comparison, because a retired payload may not carry the
            // current context members and cannot be interpreted under the schema now in force. The
            // expired 410 also precedes the context 403. Only after open() succeeds is the
            // well-formed, current-schema, unexpired cursor compared with the current context.
            return switch (refused.kind()) {
                case RETIRED, EXPIRED -> error(410, "RESYNC_REQUIRED", "RESYNC_REQUIRED");
                case INVALID -> error(400, "INVALID_CURSOR", null);
            };
        }

        // C-3.3 context step: issuer, azp, subject, audience, environment, transaction and the
        // mandate context triple. This is checked before the changed-representation 410 below.
        if (!principal.issuer().equals(binding.issuer())
                || !environment.equals(binding.environment())
                || !principal.subject().equals(binding.subject())
                || !principal.authorizedParty().equals(binding.authorizedParty())
                || !principal.audience().equals(binding.audience())
                || !transactionId.equals(binding.transactionId())
                || !mandate.acquirerRef().equals(binding.acquirerRef())
                || !mandate.merchantRef().equals(binding.merchantRef())
                || !Objects.equals(mandate.terminalRef(), binding.terminalRef())) {
            return error(403, "CONTEXT_DENIED", null);
        }
        // C-3.3 changed-representation step, after the context 403 (C-1.3).
        if (!fingerprint(mandate).equals(binding.mandateFingerprint())) {
            // The mandate changed since the cursor was minted: this generation is retired.
            return error(410, "RESYNC_REQUIRED", "RESYNC_REQUIRED");
        }

        // C-3.3 head step: a store outage is 503 and an unknown authorized target is 404, both
        // before the retention (R/F) and bound-lastSequence comparisons.
        EventContext context = contextOf(mandate, transactionId);
        EventHead head = requireHead(context);
        if (head == null) {
            return error(404, "NOT_FOUND", null);
        }
        // C-3.3 changed-R/F (and head-regression) step.
        if (head.expiryRevision() != binding.expiryRevision()
                || head.firstRetainedSequence() != binding.firstRetainedSequence()
                || head.lastSequence() < binding.snapshotHead()) {
            return error(410, "RESYNC_REQUIRED", "RESYNC_REQUIRED");
        }
        // C-3.3 bound-lastSequence step, last before the page.
        if (binding.lastSequence() > binding.snapshotHead()) {
            return error(400, "INVALID_CURSOR", null);
        }

        return page(request, principal, mandate, context, binding, binding.lastSequence(), now);
    }

    // ------------------------------------------------------------------ page assembly

    private ConsoleHttpResponse page(ConsoleHttpRequest request, ConsolePrincipal principal,
            AuthorizedContext mandate, EventContext context,
            ConsoleHistoryCursorCodec.Binding binding, long afterSequence, Instant now) {
        List<RecordedEvent> rows = readRange(context, afterSequence, binding.snapshotHead());

        // Re-authenticate and re-resolve the mandate AFTER the store read, before disclosure.
        reauthenticateAfterRead(request, principal, mandate, binding);

        // Concurrent retention/expiry must not produce a stale "complete" page.
        EventHead afterRead = requireHead(context);
        if (afterRead == null) {
            return error(404, "NOT_FOUND", null);
        }
        if (afterRead.expiryRevision() != binding.expiryRevision()
                || afterRead.firstRetainedSequence() != binding.firstRetainedSequence()
                || afterRead.lastSequence() < binding.snapshotHead()) {
            return error(410, "RESYNC_REQUIRED", "RESYNC_REQUIRED");
        }

        PageBuild page = buildPage(context, binding, afterSequence, rows, now);
        return ConsoleHttpResponse.json(200, page.json());
    }

    private PageBuild buildPage(EventContext context, ConsoleHistoryCursorCodec.Binding binding,
            long afterSequence, List<RecordedEvent> rows, Instant now) {
        long head = binding.snapshotHead();
        long firstRetained = binding.firstRetainedSequence();
        long coveredThrough = Math.max(afterSequence, Math.max(0L, firstRetained - 1));

        List<String> entries = new ArrayList<>();
        List<long[]> gapRanges = new ArrayList<>();
        long bytesOnPage = 0;
        long expected = coveredThrough + 1;
        boolean gap = false;

        for (RecordedEvent row : rows) {
            long sequence = row.sourceSequence();
            if (sequence < expected) {
                continue;
            }
            if (sequence > expected) {
                gapRanges.add(new long[] {expected, sequence - 1});
                gap = true;
                break;
            }
            String entry = entry(context, row);
            long entryBytes = entry.getBytes(StandardCharsets.UTF_8).length + (entries.isEmpty() ? 0 : 1);
            if (!entries.isEmpty() && bytesOnPage + entryBytes > MAX_PAGE_BYTES) {
                break;
            }
            entries.add(entry);
            bytesOnPage += entryBytes;
            coveredThrough = sequence;
            expected = sequence + 1;
        }
        if (!gap && rows.size() < MAX_EVENTS_PER_PAGE && coveredThrough < head) {
            // Fewer rows than the page bound and the head is not reached: a retained row is missing.
            gapRanges.add(new long[] {coveredThrough + 1, head});
            gap = true;
        }

        boolean complete = coveredThrough >= head && gapRanges.isEmpty();
        long totalEvents = binding.passedEvents() + entries.size();
        long totalBytes = binding.passedBytes() + bytesOnPage;
        boolean passExhausted = totalEvents >= MAX_PASS_EVENTS || totalBytes >= MAX_PASS_BYTES;
        boolean progress = coveredThrough > afterSequence;

        String nextCursor = null;
        if (!complete && !gap && !passExhausted && progress) {
            ConsoleHistoryCursorCodec.Binding next = new ConsoleHistoryCursorCodec.Binding(
                    binding.issuer(), binding.subject(), binding.authorizedParty(),
                    binding.audience(), binding.environment(), binding.mandateFingerprint(),
                    binding.generation(), binding.acquirerRef(), binding.merchantRef(),
                    binding.terminalRef(), binding.transactionId(),
                    binding.snapshotHead(), binding.expiryRevision(),
                    binding.firstRetainedSequence(), coveredThrough, totalEvents, totalBytes,
                    now, now.plusSeconds(cursorTtlSeconds));
            nextCursor = cursors.mint(next, now, cursorTtlSeconds);
        }

        long[] expired = firstRetained > 1 ? new long[] {1, firstRetained - 1} : null;
        String json = render(context, binding, entries, coveredThrough, expired, gapRanges,
                nextCursor, complete, firstRetained > 1);
        return new PageBuild(json);
    }

    private static String entry(EventContext context, RecordedEvent row) {
        StringBuilder json = new StringBuilder(row.canonicalJson().length() + 256);
        json.append('{');
        stringField(json, "eventKey", "ENV:" + context.environment()
                + "/" + context.acquirerRef()
                + "/" + context.merchantRef()
                + "/" + context.transactionId()
                + "/" + EventTableKeys.eventSortKey(row.sourceSequence()));
        json.append(',');
        stringField(json, "deliveryClass", row.wrapper().deliveryClass().name());
        json.append(",\"event\":").append(row.canonicalJson());
        json.append(',');
        stringField(json, "eventSha256", row.wrapper().eventSha256());
        json.append(',');
        numberField(json, "journalVersion", row.wrapper().journalVersion());
        json.append(',');
        numberField(json, "sourceSequence", row.wrapper().sourceSequence());
        json.append(',');
        stringField(json, "committedAt", row.wrapper().committedAt());
        json.append(',');
        stringField(json, "collectedAt", row.wrapper().collectedAt());
        json.append(',');
        stringField(json, "storedAt", row.wrapper().storedAt());
        json.append(',');
        stringField(json, "stateAfter", row.wrapper().stateAfter());
        return json.append('}').toString();
    }

    private static String render(EventContext context, ConsoleHistoryCursorCodec.Binding binding,
            List<String> entries, long coveredThrough, long[] expiredRange, List<long[]> gapRanges,
            String nextCursor, boolean complete, boolean retentionTruncated) {
        StringBuilder json = new StringBuilder(512);
        json.append("{\"version\":1,\"transactionId\":");
        quote(json, context.transactionId());
        json.append(",\"snapshotHead\":").append(binding.snapshotHead());
        json.append(",\"expiryRevision\":").append(binding.expiryRevision());
        json.append(",\"firstRetainedSequence\":").append(binding.firstRetainedSequence());
        json.append(",\"events\":[");
        json.append(String.join(",", entries));
        json.append(']');
        json.append(",\"coveredThrough\":").append(coveredThrough);
        json.append(",\"expiredRanges\":");
        ranges(json, expiredRange == null ? List.of() : List.of(expiredRange));
        json.append(",\"gapRanges\":");
        ranges(json, gapRanges);
        json.append(",\"nextCursor\":");
        if (nextCursor == null) {
            json.append("null");
        } else {
            quote(json, nextCursor);
        }
        json.append(",\"completeThroughSnapshot\":").append(complete);
        json.append(",\"retentionTruncated\":").append(retentionTruncated);
        return json.append('}').toString();
    }

    private static void ranges(StringBuilder json, List<long[]> ranges) {
        json.append('[');
        for (int i = 0; i < ranges.size(); i++) {
            if (i > 0) {
                json.append(',');
            }
            long[] range = ranges.get(i);
            json.append('[').append(range[0]).append(',').append(range[1]).append(']');
        }
        json.append(']');
    }

    private static void stringField(StringBuilder json, String name, String value) {
        quote(json, name);
        json.append(':');
        if (value == null) {
            json.append("null");
        } else {
            quote(json, value);
        }
    }

    private static void numberField(StringBuilder json, String name, Long value) {
        quote(json, name);
        json.append(':');
        if (value == null) {
            json.append("null");
        } else {
            json.append(value.longValue());
        }
    }

    private static void numberField(StringBuilder json, String name, long value) {
        quote(json, name);
        json.append(':').append(value);
    }

    private static void quote(StringBuilder json, String value) {
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
                        json.append(String.format(Locale.ROOT, "\\u%04x", (int) c));
                    } else {
                        json.append(c);
                    }
                }
            }
        }
        json.append('"');
    }

    // ------------------------------------------------------------------ collaborators

    /** Exactly one credential is accepted; a duplicated header is refused as unauthenticated. */
    private ConsolePrincipal authenticate(ConsoleHttpRequest request, Instant now) {
        List<String> values = request.headerValues("Authorization");
        if (values.size() > 1) {
            throw new ConsoleAuthenticationException(
                    ConsoleAuthenticationException.Reason.MISSING_TOKEN);
        }
        return authorizer.authorize(values.isEmpty() ? null : values.get(0), now);
    }

    private AuthorizedContext requireMandate(ConsolePrincipal principal) {
        AuthorizedContext context;
        try {
            context = contexts.contextFor(principal);
        } catch (RuntimeException registryFailure) {
            throw new RegistryUnavailable();
        }
        return context;
    }

    private void reauthenticateAfterRead(ConsoleHttpRequest request, ConsolePrincipal before,
            AuthorizedContext mandateBefore, ConsoleHistoryCursorCodec.Binding binding) {
        Instant now = clock.instant();
        ConsolePrincipal after;
        try {
            after = authenticate(request, now);
        } catch (ConsoleAuthenticationException refused) {
            throw new HistoryRefusal(error(401, "UNAUTHENTICATED", null));
        }
        if (!after.issuer().equals(before.issuer())
                || !after.subject().equals(before.subject())
                || !after.authorizedParty().equals(before.authorizedParty())
                || !after.audience().equals(before.audience())) {
            throw new HistoryRefusal(error(401, "UNAUTHENTICATED", null));
        }
        AuthorizedContext mandateAfter;
        try {
            mandateAfter = requireMandate(after);
        } catch (RegistryUnavailable unavailable) {
            throw new HistoryRefusal(error(503, "AUTHORIZATION_UNAVAILABLE", null));
        }
        if (mandateAfter == null) {
            throw new HistoryRefusal(error(403, "CONTEXT_DENIED", null));
        }
        // C-1.3: the context triple is checked first. A mandate that now points at another
        // acquirer/merchant/terminal is a context mismatch (403), even when its fingerprint also
        // differs from the cursor's retired generation; the same order holds on continuation.
        if (!mandateAfter.acquirerRef().equals(mandateBefore.acquirerRef())
                || !mandateAfter.merchantRef().equals(mandateBefore.merchantRef())
                || !Objects.equals(mandateAfter.terminalRef(), mandateBefore.terminalRef())) {
            throw new HistoryRefusal(error(403, "CONTEXT_DENIED", null));
        }
        if (!fingerprint(mandateAfter).equals(binding.mandateFingerprint())) {
            throw new HistoryRefusal(error(410, "RESYNC_REQUIRED", "RESYNC_REQUIRED"));
        }
    }

    private EventHead requireHead(EventContext context) {
        try {
            return store.head(context).orElse(null);
        } catch (RuntimeException unavailable) {
            throw new HistoryRefusal(error(503, "AUTHORIZATION_UNAVAILABLE", null));
        }
    }

    private List<RecordedEvent> readRange(EventContext context, long afterSequence, long through) {
        try {
            return reader.readRange(context, afterSequence, through, MAX_EVENTS_PER_PAGE);
        } catch (RuntimeException unavailable) {
            throw new HistoryRefusal(error(503, "AUTHORIZATION_UNAVAILABLE", null));
        }
    }

    private EventContext contextOf(AuthorizedContext mandate, String transactionId) {
        try {
            return new EventContext(environment, mandate.acquirerRef(), mandate.merchantRef(),
                    transactionId);
        } catch (RuntimeException unknownTarget) {
            // An unusable identifier can only address an unknown target; disclose nothing.
            throw new HistoryRefusal(error(404, "NOT_FOUND", null));
        }
    }

    private static String fingerprint(AuthorizedContext mandate) {
        Set<String> routes = mandate.permittedRoutes().stream()
                .map(route -> route.name())
                .collect(java.util.stream.Collectors.toSet());
        return ConsoleHistoryCursorCodec.mandateFingerprint(
                mandate.callerId(), mandate.acquirerRef(), mandate.merchantRef(), routes);
    }

    // ------------------------------------------------------------------ query and path

    /**
     * The C-3.1 request-form step: every refusal here is decidable from the parsed query alone, so
     * it runs after authentication and before mandate resolution or any store read. Only the six
     * syntactic classes are refused here; a value of {@code after} greater than the frozen head is
     * deliberately not part of this step (C-3.4 judges it later, after the head read).
     */
    private static ConsoleHistoryQuery queryOf(ConsoleHttpRequest request) {
        Map<String, List<String>> query = effectiveQuery(request);
        // C-3.1(c): any query parameter name other than snapshot, after or cursor is malformed.
        for (String name : query.keySet()) {
            if (!name.equals("snapshot") && !name.equals("after") && !name.equals("cursor")) {
                throw new InvalidRequest();
            }
        }
        // C-3.1(d): a repeated pagination parameter is contradictory input, not a preference.
        List<String> cursor = values(query, "cursor");
        if (cursor.size() > 1) {
            throw new InvalidRequest();
        }
        if (cursor.size() == 1) {
            if (!values(query, "after").isEmpty() || !values(query, "snapshot").isEmpty()) {
                // C-3.1(f) / C-2: a continuation carries the cursor alone. A second position form is
                // contradictory input and is refused here, before mandate resolution and the store
                // read, rather than letting one parameter silently win.
                throw new InvalidRequest();
            }
            if (cursor.get(0).isEmpty()) {
                // C-3.1(e): cursor present and empty.
                throw new InvalidRequest();
            }
            return new ConsoleHistoryQuery.Continue(cursor.get(0));
        }

        List<String> snapshot = values(query, "snapshot");
        if (snapshot.size() > 1) {
            throw new InvalidRequest();
        }
        if (snapshot.size() == 1 && !"new".equals(snapshot.get(0))) {
            // C-3.1(a): snapshot's only accepted value is new.
            throw new InvalidRequest();
        }
        List<String> after = values(query, "after");
        if (after.size() > 1) {
            throw new InvalidRequest();
        }
        long afterSequence = 0;
        if (after.size() == 1) {
            afterSequence = parseAfter(after.get(0));
        }
        return new ConsoleHistoryQuery.NewSnapshot(afterSequence);
    }

    /**
     * C-3.1(b): {@code after} is 0–12 ASCII digits forming a non-negative integer. Nothing here
     * reads the store head; the head-dependent bound is C-3.4.
     */
    private static long parseAfter(String raw) {
        if (raw.isEmpty() || raw.length() > 12) {
            throw new InvalidRequest();
        }
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c < '0' || c > '9') {
                throw new InvalidRequest();
            }
        }
        return Long.parseLong(raw);
    }

    private static List<String> values(Map<String, List<String>> query, String name) {
        List<String> values = query.get(name);
        return values == null ? List.of() : values;
    }

    /**
     * Prefer the explicit query map. When a transport hands the raw target as
     * {@code path?query}, parse it defensively; no percent-decoding ambiguity is accepted.
     */
    private static Map<String, List<String>> effectiveQuery(ConsoleHttpRequest request) {
        if (!request.query().isEmpty()) {
            return request.query();
        }
        String path = request.path();
        int question = path.indexOf('?');
        if (question < 0) {
            return Map.of();
        }
        String raw = path.substring(question + 1);
        if (raw.isEmpty() || raw.length() > 4096) {
            throw new InvalidRequest();
        }
        java.util.LinkedHashMap<String, List<String>> parsed = new java.util.LinkedHashMap<>();
        for (String pair : raw.split("&", -1)) {
            if (pair.isEmpty()) {
                throw new InvalidRequest();
            }
            int equals = pair.indexOf('=');
            String name = equals < 0 ? pair : pair.substring(0, equals);
            String value = equals < 0 ? "" : pair.substring(equals + 1);
            if (!name.equals("snapshot") && !name.equals("after") && !name.equals("cursor")) {
                throw new InvalidRequest();
            }
            parsed.computeIfAbsent(name, key -> new ArrayList<>()).add(value);
        }
        return parsed;
    }

    /** Extract the transaction id from the fixed path shape, or {@code null}. */
    public static String transactionIdOf(String path) {
        if (path == null) {
            return null;
        }
        String rawPath = path;
        int question = rawPath.indexOf('?');
        if (question >= 0) {
            rawPath = rawPath.substring(0, question);
        }
        if (!rawPath.startsWith(PATH_PREFIX) || !rawPath.endsWith(PATH_SUFFIX)) {
            return null;
        }
        String id = rawPath.substring(PATH_PREFIX.length(),
                rawPath.length() - PATH_SUFFIX.length());
        if (id.isEmpty() || id.indexOf('/') >= 0) {
            return null;
        }
        return id;
    }

    // ------------------------------------------------------------------ response shapes

    private static ConsoleHttpResponse error(int status, String code, String action) {
        StringBuilder json = new StringBuilder(80)
                .append("{\"version\":1,\"error\":");
        quote(json, code);
        json.append(",\"action\":");
        if (action == null) {
            json.append("null");
        } else {
            quote(json, action);
        }
        return ConsoleHttpResponse.json(status, json.append('}').toString());
    }

    // ------------------------------------------------------------------ internal types

    private record PageBuild(String json) {
    }

    private static final class HistoryRefusal extends RuntimeException {
        private final ConsoleHttpResponse response;

        private HistoryRefusal(ConsoleHttpResponse response) {
            super("history request refused", null, false, false);
            this.response = response;
        }

        private ConsoleHttpResponse response() {
            return response;
        }
    }

    private static final class RegistryUnavailable extends RuntimeException {
        private RegistryUnavailable() {
            super("authorization registry unavailable", null, false, false);
        }
    }

    private static final class InvalidRequest extends RuntimeException {
        private InvalidRequest() {
            super("invalid history query", null, false, false);
        }
    }
}
