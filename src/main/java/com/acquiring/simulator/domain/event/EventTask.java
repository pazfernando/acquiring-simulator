package com.acquiring.simulator.domain.event;

/**
 * Operation A task set declared by observation contract O1; network/issuer tasks stay
 * conditional until that branch is actually instrumented.
 */
public enum EventTask {
    RECEIVE("receive"),
    CONTEXT_CHECK("context-check"),
    VALIDATE("validate"),
    IDEMPOTENCY_CHECK("idempotency-check"),
    REQUEST_ADAPT("request-adapt"),
    DISPATCH("dispatch"),
    RESPONSE_ADAPT("response-adapt"),
    BUSINESS_PERSIST("business-persist"),
    CHANNEL_RESPONSE("channel-response"),
    NETWORK_FORWARD("network-forward"),
    ISSUER_DECIDE("issuer-decide");

    private final String wireName;

    EventTask(String wireName) {
        this.wireName = wireName;
    }

    public String wireName() {
        return wireName;
    }

    public static EventTask fromWire(String value) {
        for (EventTask task : values()) {
            if (task.wireName.equals(value)) {
                return task;
            }
        }
        throw new IllegalArgumentException("unknown task: " + value);
    }
}
