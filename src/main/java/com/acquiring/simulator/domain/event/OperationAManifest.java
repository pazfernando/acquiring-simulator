package com.acquiring.simulator.domain.event;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Operation A manifest, version A-1. The task list is the one approved in observation contract O1;
 * producer wiring mirrors the components that exist today. Network and issuer tasks are marked
 * conditional because that branch is only instrumented where actually observed.
 *
 * <p>Critical capacity is NOT part of the fixed manifest: it is injected as an explicit
 * {@link CriticalCapacity} policy. The owner has not approved the O8 proposed numbers, so there is
 * no implicit production default and no silently active budget in runtime code.
 */
public final class OperationAManifest {

    public static final String MANIFEST_VERSION = "A-1";
    public static final String CAPABILITY = "A";
    public static final String PRODUCER_CHANNEL = "CHANNEL-A";
    public static final String PRODUCER_ACL = "ACL-A";
    public static final String PRODUCER_PROCESSOR = "PROC-A";
    public static final String PRODUCER_NETWORK = "NETWORK-A";
    public static final String PRODUCER_ISSUER = "ISSUER-A";

    private static final List<EventManifest.TaskDeclaration> TASKS = List.of(
            new EventManifest.TaskDeclaration(EventTask.RECEIVE, EventActor.CHANNEL, false),
            new EventManifest.TaskDeclaration(EventTask.REQUEST_ADAPT, EventActor.ACL, false),
            new EventManifest.TaskDeclaration(EventTask.CHANNEL_RESPONSE, EventActor.ACL, false),
            new EventManifest.TaskDeclaration(EventTask.CONTEXT_CHECK, EventActor.PROCESSOR, false),
            new EventManifest.TaskDeclaration(EventTask.VALIDATE, EventActor.PROCESSOR, false),
            new EventManifest.TaskDeclaration(EventTask.IDEMPOTENCY_CHECK, EventActor.PROCESSOR, false),
            new EventManifest.TaskDeclaration(EventTask.DISPATCH, EventActor.PROCESSOR, false),
            new EventManifest.TaskDeclaration(EventTask.RESPONSE_ADAPT, EventActor.PROCESSOR, false),
            new EventManifest.TaskDeclaration(EventTask.BUSINESS_PERSIST, EventActor.PROCESSOR, false),
            new EventManifest.TaskDeclaration(EventTask.NETWORK_FORWARD, EventActor.NETWORK, true),
            new EventManifest.TaskDeclaration(EventTask.ISSUER_DECIDE, EventActor.ISSUER, true));

    private static final List<EventManifest.ProducerDeclaration> PRODUCERS = List.of(
            new EventManifest.ProducerDeclaration(PRODUCER_CHANNEL, EventActor.CHANNEL,
                    Set.of(EventTask.RECEIVE), false),
            new EventManifest.ProducerDeclaration(PRODUCER_ACL, EventActor.ACL,
                    Set.of(EventTask.REQUEST_ADAPT, EventTask.CHANNEL_RESPONSE), false),
            new EventManifest.ProducerDeclaration(PRODUCER_PROCESSOR, EventActor.PROCESSOR,
                    Set.of(EventTask.CONTEXT_CHECK, EventTask.VALIDATE,
                            EventTask.IDEMPOTENCY_CHECK, EventTask.DISPATCH,
                            EventTask.RESPONSE_ADAPT, EventTask.BUSINESS_PERSIST), true),
            new EventManifest.ProducerDeclaration(PRODUCER_NETWORK, EventActor.NETWORK,
                    Set.of(EventTask.NETWORK_FORWARD), false),
            new EventManifest.ProducerDeclaration(PRODUCER_ISSUER, EventActor.ISSUER,
                    Set.of(EventTask.ISSUER_DECIDE), false));

    private OperationAManifest() {
    }

    /**
     * Build the Operation A manifest with an explicit capacity policy. No capacity decision is
     * implied by the task contract; callers must supply approved or test-only values.
     */
    public static EventManifest manifest(CriticalCapacity capacity) {
        if (capacity == null) {
            throw new IllegalArgumentException("critical capacity policy is required");
        }
        return new EventManifest(MANIFEST_VERSION, CAPABILITY, TASKS, PRODUCERS,
                EnumSet.allOf(EventReason.class),
                capacity.maxCriticalEventsPerOperation(),
                capacity.criticalSlotBudget());
    }
}
