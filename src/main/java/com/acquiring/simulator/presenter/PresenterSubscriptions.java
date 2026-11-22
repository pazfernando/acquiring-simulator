package com.acquiring.simulator.presenter;

import com.acquiring.simulator.domain.event.EventContext;
import com.acquiring.simulator.domain.event.EventHead;
import com.acquiring.simulator.domain.port.EventStore;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * In-memory subscription identity state for the local live transport.
 *
 * <p>Implements the observation contract O5a semantics that do not require AWS: one authentication
 * epoch per connection; idempotency key {@code K=(connectionId, connectionEpoch, requestId)};
 * immutable request tuple {@code T}; subscribe-before-head-observation; a cached ACK that survives a
 * lost/duplicated request; explicit conflict codes; unique active subscription per connection;
 * control-ID cap; and tombstones so a retired request is never silently reinstalled.</p>
 *
 * <p>Notification processing here is observation-only. No method on this type calls the payment
 * service, performs a POST, retries a financial send or mutates a business outcome.</p>
 */
public final class PresenterSubscriptions {

    /** Proposed per-epoch control identity cap (observation contract O5a). */
    public static final int MAX_CONTROL_IDS = 100;

    private final EventStore store;
    private final String environmentId;
    private final SecureRandom random = new SecureRandom();

    public PresenterSubscriptions(EventStore store, String environmentId) {
        this.store = Objects.requireNonNull(store, "store");
        this.environmentId = Objects.requireNonNull(environmentId, "environmentId");
    }

    /** Create the authentication epoch/ready state for a freshly authenticated connection. */
    public Connection open() {
        byte[] raw = new byte[16];
        random.nextBytes(raw);
        return new Connection(
                HexFormat.of().formatHex(raw), HexFormat.of().formatHex(raw));
    }

    /** The immutable request tuple a subscribe/unsubscribe names. */
    public record Tuple(String subscriptionId, String environmentId, String acquirerRef,
            String merchantRef, String transactionId) {
        public Tuple {
            Objects.requireNonNull(subscriptionId, "subscriptionId");
            Objects.requireNonNull(environmentId, "environmentId");
            Objects.requireNonNull(acquirerRef, "acquirerRef");
            Objects.requireNonNull(merchantRef, "merchantRef");
            Objects.requireNonNull(transactionId, "transactionId");
        }
    }

    /** One active live route; its generation is fixed for the epoch. */
    public record Route(String subscriptionId, String requestId, Tuple tuple, long generation,
            long headSequence, long expiryRevision, long firstRetainedSequence) {
    }

    /** The cached acknowledgement of one successful control request. */
    public record Ack(String action, String requestId, String subscriptionId,
            String connectionEpoch, String environmentId, String acquirerRef, String merchantRef,
            String transactionId, long generation, long headSequence, long expiryRevision,
            long firstRetainedSequence) {
    }

    /** A safe rejection code; no foreign data is echoed. */
    public record Rejected(String action, String requestId, String code) {
    }

    /** Server-owned connection identity; the client can never assert it. */
    public static final class Connection {
        private final String connectionId;
        private final String epoch;
        private final Map<String, Control> controls = new LinkedHashMap<>();
        private final Map<String, Route> active = new LinkedHashMap<>();
        private long nextGeneration = 1;

        Connection(String connectionId, String epoch) {
            this.connectionId = connectionId;
            this.epoch = epoch;
        }

        public String connectionId() {
            return connectionId;
        }

        public String epoch() {
            return epoch;
        }
    }

    private enum Action { SUBSCRIBE, UNSUBSCRIBE }

    private static final class Control {
        private final Action action;
        private final Tuple tuple;
        private long generation;
        private boolean retired;
        private Ack ack;

        private Control(Action action, Tuple tuple) {
            this.action = action;
            this.tuple = tuple;
        }
    }

    /**
     * Install (or re-confirm) one subscription. Routing is installed before the strong head is
     * observed; the ACK is cached only after the observation succeeds.
     */
    public Object subscribe(Connection connection, String requestId, Tuple tuple) {
        Objects.requireNonNull(connection, "connection");
        if (requestId == null || requestId.isBlank()) {
            return new Rejected("subscribed", requestId, "INVALID_REQUEST");
        }
        synchronized (connection) {
            if (!environmentId.equals(tuple.environmentId())) {
                return new Rejected("subscribed", requestId, "CONTEXT_DENIED");
            }
            Control control = connection.controls.get(requestId);
            if (control != null) {
                if (control.action != Action.SUBSCRIBE || !control.tuple.equals(tuple)) {
                    return new Rejected("subscribed", requestId, "REQUEST_CONFLICT");
                }
                if (control.retired) {
                    return new Rejected("subscribed", requestId, "REQUEST_RETIRED");
                }
                return control.ack;
            }
            if (connection.controls.size() >= MAX_CONTROL_IDS) {
                return new Rejected("subscribed", requestId, "CONTROL_LIMIT");
            }
            Route existing = connection.active.get(tuple.subscriptionId());
            if (existing != null) {
                return new Rejected("subscribed", requestId, "SUBSCRIPTION_CONFLICT");
            }

            long generation = connection.nextGeneration++;
            Control installed = new Control(Action.SUBSCRIBE, tuple);
            installed.generation = generation;
            connection.controls.put(requestId, installed);
            Optional<EventHead> head = store.head(context(tuple));
            Route route = new Route(tuple.subscriptionId(), requestId, tuple, generation,
                    head.map(EventHead::lastSequence).orElse(0L),
                    head.map(EventHead::expiryRevision).orElse(0L),
                    head.map(EventHead::firstRetainedSequence).orElse(0L));
            connection.active.put(tuple.subscriptionId(), route);
            installed.ack = new Ack("subscribed", requestId, tuple.subscriptionId(),
                    connection.epoch(), tuple.environmentId(), tuple.acquirerRef(),
                    tuple.merchantRef(), tuple.transactionId(), generation,
                    route.headSequence(), route.expiryRevision(), route.firstRetainedSequence());
            return installed.ack;
        }
    }

    /** Deactivate one subscription, retaining a tombstone for repeated identical removals. */
    public Object unsubscribe(Connection connection, String requestId, String subscriptionId,
            long generation, Tuple tuple) {
        Objects.requireNonNull(connection, "connection");
        synchronized (connection) {
            Control control = requestId == null ? null : connection.controls.get(requestId);
            if (control != null) {
                if (control.action != Action.UNSUBSCRIBE
                        || !control.tuple.equals(tuple)
                        || !control.tuple.subscriptionId().equals(subscriptionId)) {
                    return new Rejected("unsubscribed", requestId, "REQUEST_CONFLICT");
                }
                if (control.ack != null) {
                    return control.ack;
                }
                if (control.generation != generation) {
                    return new Rejected("unsubscribed", requestId, "STALE_SUBSCRIPTION");
                }
                Route route = connection.active.remove(subscriptionId);
                if (route == null || route.generation() != generation) {
                    return new Rejected("unsubscribed", requestId, "STALE_SUBSCRIPTION");
                }
                control.retired = true;
                control.ack = new Ack("unsubscribed", requestId, subscriptionId,
                        connection.epoch(), tuple.environmentId(), tuple.acquirerRef(),
                        tuple.merchantRef(), tuple.transactionId(), generation,
                        route.headSequence(), route.expiryRevision(),
                        route.firstRetainedSequence());
                return control.ack;
            }
            if (connection.active.containsKey(subscriptionId)) {
                return new Rejected("unsubscribed", requestId, "SUBSCRIPTION_CONFLICT");
            }
            return new Rejected("unsubscribed", requestId, "STALE_SUBSCRIPTION");
        }
    }

    /** Register the first removal request for a still-active subscription. */
    public Object beginUnsubscribe(Connection connection, String requestId, Tuple tuple,
            long generation) {
        Objects.requireNonNull(connection, "connection");
        synchronized (connection) {
            if (connection.controls.containsKey(requestId)) {
                // Never overwrite an existing idempotency key; the shared control namespace decides
                // whether this is a repeat, a conflict or a stale target.
                return unsubscribe(connection, requestId, tuple.subscriptionId(), generation, tuple);
            }
            if (connection.controls.size() >= MAX_CONTROL_IDS) {
                return new Rejected("unsubscribed", requestId, "CONTROL_LIMIT");
            }
            Control control = new Control(Action.UNSUBSCRIBE, tuple);
            control.generation = generation;
            connection.controls.put(requestId, control);
            return unsubscribe(connection, requestId, tuple.subscriptionId(), generation, tuple);
        }
    }

    /** Active routes of this connection, for fan-out and heartbeat synchronization. */
    public List<Route> routes(Connection connection) {
        synchronized (connection) {
            return List.copyOf(connection.active.values());
        }
    }

    /** Remove all routes for an epoch (session expiry or disconnect). */
    public void close(Connection connection) {
        synchronized (connection) {
            connection.active.clear();
        }
    }

    private static EventContext context(Tuple tuple) {
        return new EventContext(tuple.environmentId(), tuple.acquirerRef(), tuple.merchantRef(),
                tuple.transactionId());
    }

    /** Absent HEAD means no event was ever committed for this transaction. */
    private static final EventHead EMPTY_HEAD =
            new EventHead(new EventContext("ENV", "ACQ", "MID", "TX"), 0, 0, 0, null, null);

    /** Small helper so callers can render the ready frame without exposing identity claims. */
    public static String newRequestId() {
        byte[] raw = new byte[12];
        new SecureRandom().nextBytes(raw);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
    }

    /** True when the two tuples address the same transaction. */
    public static boolean sameTransaction(Tuple left, Tuple right) {
        return left.transactionId().equals(right.transactionId())
                && left.acquirerRef().equals(right.acquirerRef())
                && left.merchantRef().equals(right.merchantRef());
    }

    /** Optional route lookup for diagnostics; never used to authorize a payment. */
    public Optional<Route> route(Connection connection, String subscriptionId) {
        synchronized (connection) {
            return Optional.ofNullable(connection.active.get(subscriptionId));
        }
    }
}
