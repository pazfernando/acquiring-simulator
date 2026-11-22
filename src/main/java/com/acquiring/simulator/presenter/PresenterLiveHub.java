package com.acquiring.simulator.presenter;

import com.acquiring.simulator.domain.event.EventCommitObserver;
import com.acquiring.simulator.domain.event.EventContext;
import com.acquiring.simulator.domain.event.EventHead;
import com.acquiring.simulator.domain.event.RecordedEvent;
import com.acquiring.simulator.domain.port.EventHistoryReader;
import com.acquiring.simulator.domain.port.EventStore;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Local live-delivery hub for the presenter console (observation contract O5a/O6/O7).
 *
 * <p>One authenticated transport connection owns one {@link PresenterSubscriptions.Connection}.
 * A subscribe request is installed and acknowledged before any live frame is accepted by the
 * client. After a genuine event row commits, the hub reads that exact retained row and fans one
 * frame out to the matching active subscriptions, using the subscription's own generation. It never
 * copies the payload into another store, buffers for an offline browser, calls the payment service,
 * retries a financial send or mutates a business outcome.</p>
 *
 * <p>Delivery here is a local in-process fixture for Streams plus notifier. It is at-most-best
 * effort: a disconnected or slow socket loses the live frame and recovery remains the HTTP
 * catch-up over the same event rows.</p>
 */
final class PresenterLiveHub implements EventCommitObserver, AutoCloseable {

    /** Proposed observation-contract bound. */
    static final int MAX_CONNECTIONS = 50;
    static final int MAX_FRAME_BYTES = 4_096;

    /** The transport-owned sink for one connection. */
    interface FrameSink {
        void send(String text) throws IOException;

        void close();
    }

    private final EventStore store;
    private final EventHistoryReader history;
    private final ObjectMapper json;
    private final String environmentId;
    private final PresenterSubscriptions subscriptions;
    private final Set<LiveSession> sessions = ConcurrentHashMap.newKeySet();
    private final ExecutorService fanout = Executors.newSingleThreadExecutor(
            runnable -> Thread.ofPlatform().name("presenter-live-fanout").daemon(true)
                    .unstarted(runnable));
    private final AtomicBoolean closed = new AtomicBoolean();

    PresenterLiveHub(EventStore store, EventHistoryReader history, ObjectMapper json,
            String environmentId) {
        this.store = Objects.requireNonNull(store, "store");
        this.history = Objects.requireNonNull(history, "history");
        this.json = Objects.requireNonNull(json, "json");
        this.environmentId = Objects.requireNonNull(environmentId, "environmentId");
        this.subscriptions = new PresenterSubscriptions(store, environmentId);
    }

    /** Register a newly authenticated connection and send its ready frame. */
    Optional<LiveSession> open(FrameSink sink) {
        if (closed.get() || sessions.size() >= MAX_CONNECTIONS) {
            return Optional.empty();
        }
        PresenterSubscriptions.Connection connection = subscriptions.open();
        LiveSession session = new LiveSession(connection, sink);
        sessions.add(session);
        ObjectNode ready = json.createObjectNode();
        ready.put("version", 1);
        ready.put("action", "ready");
        ready.put("connectionEpoch", connection.epoch());
        ready.put("environmentId", environmentId);
        ready.put("identityMode", "LOCAL_TEST_FIXTURE");
        if (!session.send(ready)) {
            session.close();
            return Optional.empty();
        }
        return Optional.of(session);
    }

    /**
     * Observation seam invoked after a new event row commits. The strong read and socket writes run
     * on one ordered fan-out thread so the payment path is not blocked by notification work.
     */
    @Override
    public void onCommitted(EventContext context, long sourceSequence) {
        if (closed.get()) {
            return;
        }
        try {
            fanout.execute(() -> fanOut(context, sourceSequence));
        } catch (RejectedExecutionException stopped) {
            // The hub is shutting down; live delivery is optional and catch-up remains available.
        }
    }

    private void fanOut(EventContext context, long sourceSequence) {
        if (closed.get() || sessions.isEmpty()) {
            return;
        }
        RecordedEvent row;
        try {
            List<RecordedEvent> rows =
                    history.readRange(context, sourceSequence - 1, sourceSequence, 1);
            row = rows.isEmpty() ? null : rows.get(0);
        } catch (RuntimeException unreadable) {
            // A source read/fan-out failure is an explicit live gap, never a payment failure.
            return;
        }
        if (row == null) {
            return;
        }
        for (LiveSession session : sessions) {
            session.deliver(context, row);
        }
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        fanout.shutdownNow();
        for (LiveSession session : new ArrayList<>(sessions)) {
            session.close();
        }
        sessions.clear();
    }

    /** One transport connection's subscription and frame state. */
    final class LiveSession {
        private final PresenterSubscriptions.Connection connection;
        private final FrameSink sink;
        private final Object lock = new Object();
        private final AtomicBoolean open = new AtomicBoolean(true);

        private LiveSession(PresenterSubscriptions.Connection connection, FrameSink sink) {
            this.connection = connection;
            this.sink = sink;
        }

        String connectionEpoch() {
            return connection.epoch();
        }

        boolean isOpen() {
            return open.get();
        }

        /** Parse and dispatch one client text message. */
        void handle(String text) {
            JsonNode frame;
            try {
                frame = json.readTree(text);
            } catch (IOException invalid) {
                return;
            }
            if (frame == null || !frame.isObject() || frame.path("version").asInt(-1) != 1
                    || !frame.path("action").isTextual()) {
                return;
            }
            switch (frame.path("action").asText()) {
                case "subscribe" -> handleSubscribe(frame);
                case "unsubscribe" -> handleUnsubscribe(frame);
                case "sync" -> handleSync();
                default -> {
                    // Unknown client actions carry no server meaning; nothing is echoed.
                }
            }
        }

        private void handleSubscribe(JsonNode frame) {
            String requestId = text(frame, "requestId", 64);
            String subscriptionId = text(frame, "subscriptionId", 64);
            String frameEnvironment = text(frame, "environmentId", 64);
            String transactionId = text(frame, "transactionId", 128);
            JsonNode context = frame.get("context");
            String acquirerRef = context == null ? null : text(context, "acquirerRef", 128);
            String merchantRef = context == null ? null : text(context, "merchantRef", 128);
            if (requestId == null || subscriptionId == null || transactionId == null
                    || !environmentId.equals(frameEnvironment)
                    || acquirerRef == null || merchantRef == null) {
                reject(requestId, "INVALID_REQUEST");
                return;
            }
            PresenterSubscriptions.Tuple tuple = new PresenterSubscriptions.Tuple(
                    subscriptionId, frameEnvironment, acquirerRef, merchantRef, transactionId);
            Object result;
            synchronized (lock) {
                result = subscriptions.subscribe(connection, requestId, tuple);
                if (result instanceof PresenterSubscriptions.Ack ack) {
                    send(ackFrame(ack));
                } else if (result instanceof PresenterSubscriptions.Rejected rejected) {
                    send(rejectFrame(rejected.requestId(), rejected.code()));
                }
            }
        }

        private void handleUnsubscribe(JsonNode frame) {
            String requestId = text(frame, "requestId", 64);
            String subscriptionId = text(frame, "subscriptionId", 64);
            String frameEnvironment = text(frame, "environmentId", 64);
            String transactionId = text(frame, "transactionId", 128);
            JsonNode context = frame.get("context");
            String acquirerRef = context == null ? null : text(context, "acquirerRef", 128);
            String merchantRef = context == null ? null : text(context, "merchantRef", 128);
            JsonNode generationNode = frame.get("generation");
            long generation = generationNode == null || !generationNode.canConvertToLong()
                    ? -1L : generationNode.longValue();
            if (requestId == null || subscriptionId == null || transactionId == null
                    || !environmentId.equals(frameEnvironment) || generation < 0
                    || acquirerRef == null || merchantRef == null) {
                reject(requestId, "INVALID_REQUEST");
                return;
            }
            PresenterSubscriptions.Tuple tuple = new PresenterSubscriptions.Tuple(
                    subscriptionId, frameEnvironment, acquirerRef, merchantRef, transactionId);
            synchronized (lock) {
                Object result = subscriptions.beginUnsubscribe(
                        connection, requestId, tuple, generation);
                if (result instanceof PresenterSubscriptions.Ack ack) {
                    send(ackFrame(ack));
                } else if (result instanceof PresenterSubscriptions.Rejected rejected) {
                    send(rejectFrame(rejected.requestId(), rejected.code()));
                }
            }
        }

        private void handleSync() {
            synchronized (lock) {
                List<PresenterSubscriptions.Route> routes = subscriptions.routes(connection);
                ObjectNode sync = json.createObjectNode();
                sync.put("version", 1);
                sync.put("action", "sync");
                ArrayNode inventory = sync.putArray("subscriptions");
                for (PresenterSubscriptions.Route route : routes) {
                    long head = route.headSequence();
                    long expiryRevision = route.expiryRevision();
                    long firstRetained = route.firstRetainedSequence();
                    try {
                        Optional<EventHead> current = store.head(context(route.tuple()));
                        if (current.isPresent()) {
                            head = current.get().lastSequence();
                            expiryRevision = current.get().expiryRevision();
                            firstRetained = current.get().firstRetainedSequence();
                        }
                    } catch (RuntimeException unreadable) {
                        // Keep the last acknowledged routing observation; never invent progress.
                    }
                    ObjectNode item = inventory.addObject();
                    item.put("subscriptionId", route.subscriptionId());
                    item.put("transactionId", route.tuple().transactionId());
                    item.put("generation", route.generation());
                    item.put("headSequence", head);
                    item.put("expiryRevision", expiryRevision);
                    item.put("firstRetainedSequence", firstRetained);
                }
                send(sync);
            }
        }

        /** Deliver one recorded row to every active route addressing its transaction. */
        private void deliver(EventContext context, RecordedEvent row) {
            if (!open.get()) {
                return;
            }
            JsonNode record = null;
            List<PresenterSubscriptions.Route> matching = new ArrayList<>();
            synchronized (lock) {
                for (PresenterSubscriptions.Route route : subscriptions.routes(connection)) {
                    if (sameTransaction(context, route.tuple())) {
                        matching.add(route);
                    }
                }
                if (matching.isEmpty()) {
                    return;
                }
                try {
                    record = json.readTree(row.canonicalJson());
                } catch (IOException unreadable) {
                    return;
                }
                for (PresenterSubscriptions.Route route : matching) {
                    ObjectNode frame = eventFrame(context, route, row, record);
                    if (frame.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8).length
                            <= MAX_FRAME_BYTES) {
                        send(frame);
                    }
                    // A frame over the contract bound is not truncated and not invented: the
                    // retained HTTP snapshot remains the delivery path for that row.
                }
            }
        }

        private ObjectNode eventFrame(EventContext context, PresenterSubscriptions.Route route,
                RecordedEvent row, JsonNode record) {
            ObjectNode frame = json.createObjectNode();
            frame.put("version", 1);
            frame.put("action", "event");
            frame.put("requestId", route.requestId());
            frame.put("subscriptionId", route.subscriptionId());
            frame.put("connectionEpoch", connection.epoch());
            frame.put("environmentId", environmentId);
            ObjectNode frameContext = frame.putObject("context");
            frameContext.put("acquirerRef", context.acquirerRef());
            frameContext.put("merchantRef", context.merchantRef());
            frame.put("generation", route.generation());
            frame.put("transactionId", context.transactionId());
            frame.put("sourceSequence", row.sourceSequence());
            frame.put("eventId", record.path("eventId").asText(null));
            frame.put("producerId", record.path("producerId").asText(null));
            frame.put("eventSha256", row.wrapper().eventSha256());
            frame.put("authority", "RECORDED_EVENT");
            frame.put("deliveryClass", row.wrapper().deliveryClass() == null
                    ? null : row.wrapper().deliveryClass().name());
            if (row.wrapper().journalVersion() == null) {
                frame.putNull("journalVersion");
            } else {
                frame.put("journalVersion", row.wrapper().journalVersion());
            }
            frame.put("committedAt", row.wrapper().committedAt());
            frame.put("collectedAt", row.wrapper().collectedAt());
            frame.put("storedAt", row.wrapper().storedAt());
            frame.put("stateAfter", row.wrapper().stateAfter());
            frame.set("record", record);
            return frame;
        }

        private ObjectNode ackFrame(PresenterSubscriptions.Ack ack) {
            ObjectNode frame = json.createObjectNode();
            frame.put("version", 1);
            frame.put("action", ack.action());
            frame.put("requestId", ack.requestId());
            frame.put("subscriptionId", ack.subscriptionId());
            frame.put("connectionEpoch", ack.connectionEpoch());
            frame.put("environmentId", ack.environmentId());
            ObjectNode context = frame.putObject("context");
            context.put("acquirerRef", ack.acquirerRef());
            context.put("merchantRef", ack.merchantRef());
            frame.put("transactionId", ack.transactionId());
            frame.put("generation", ack.generation());
            frame.put("headSequence", ack.headSequence());
            frame.put("expiryRevision", ack.expiryRevision());
            frame.put("firstRetainedSequence", ack.firstRetainedSequence());
            return frame;
        }

        private ObjectNode rejectFrame(String requestId, String code) {
            ObjectNode frame = json.createObjectNode();
            frame.put("version", 1);
            frame.put("action", "subscriptionRejected");
            if (requestId == null) {
                frame.putNull("requestId");
            } else {
                frame.put("requestId", requestId);
            }
            frame.put("code", code);
            return frame;
        }

        private void reject(String requestId, String code) {
            synchronized (lock) {
                send(rejectFrame(requestId, code));
            }
        }

        boolean send(ObjectNode frame) {
            if (!open.get()) {
                return false;
            }
            synchronized (lock) {
                try {
                    sink.send(json.writeValueAsString(frame));
                    return true;
                } catch (IOException failed) {
                    close();
                    return false;
                }
            }
        }

        void close() {
            if (open.compareAndSet(true, false)) {
                sessions.remove(this);
                sink.close();
            }
        }
    }

    private static EventContext context(PresenterSubscriptions.Tuple tuple) {
        return new EventContext(tuple.environmentId(), tuple.acquirerRef(), tuple.merchantRef(),
                tuple.transactionId());
    }

    private static boolean sameTransaction(EventContext context,
            PresenterSubscriptions.Tuple tuple) {
        return context.environment().equals(tuple.environmentId())
                && context.acquirerRef().equals(tuple.acquirerRef())
                && context.merchantRef().equals(tuple.merchantRef())
                && context.transactionId().equals(tuple.transactionId());
    }

    private static String text(JsonNode node, String field, int maxLength) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual()) {
            return null;
        }
        String text = value.textValue();
        return text.isEmpty() || text.length() > maxLength ? null : text;
    }
}
