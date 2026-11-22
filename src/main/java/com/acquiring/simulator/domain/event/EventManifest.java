package com.acquiring.simulator.domain.event;

import java.util.List;
import java.util.Set;

/**
 * Versioned task manifest (observation contract O1). It declares which tasks exist, which producer
 * emits them, the actor role, conditional branches and the declared critical-transition budget.
 *
 * <p>The critical budget is supplied by an explicit {@link CriticalCapacity} policy at construction;
 * the O8 figures remain PROPOSED and are not a runtime default. The budget is configuration, never
 * owner-approved capacity.
 */
public record EventManifest(
        String manifestVersion,
        String capability,
        List<TaskDeclaration> tasks,
        List<ProducerDeclaration> producers,
        Set<EventReason> allowedReasons,
        int maxCriticalEventsPerOperation,
        int criticalSlotBudget) {

    public EventManifest {
        requireText(manifestVersion, "manifestVersion");
        requireText(capability, "capability");
        tasks = List.copyOf(tasks);
        producers = List.copyOf(producers);
        allowedReasons = Set.copyOf(allowedReasons);
        if (tasks.isEmpty() || producers.isEmpty() || allowedReasons.isEmpty()) {
            throw new IllegalArgumentException("manifest must declare tasks, producers and reasons");
        }
        if (maxCriticalEventsPerOperation < 0 || criticalSlotBudget < maxCriticalEventsPerOperation) {
            throw new IllegalArgumentException("critical budget must cover the declared transitions");
        }
        for (int i = 0; i < tasks.size(); i++) {
            for (int j = i + 1; j < tasks.size(); j++) {
                if (tasks.get(i).task() == tasks.get(j).task()) {
                    throw new IllegalArgumentException("duplicate task declaration: " + tasks.get(i).task());
                }
            }
        }
        for (int i = 0; i < producers.size(); i++) {
            for (int j = i + 1; j < producers.size(); j++) {
                if (producers.get(i).producerId().equals(producers.get(j).producerId())) {
                    throw new IllegalArgumentException("duplicate producer declaration");
                }
            }
        }
    }

    public TaskDeclaration task(EventTask task) {
        return tasks.stream().filter(t -> t.task() == task).findFirst().orElse(null);
    }

    public ProducerDeclaration producer(String producerId) {
        return producers.stream().filter(p -> p.producerId().equals(producerId)).findFirst().orElse(null);
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank() || value.length() > 32) {
            throw new IllegalArgumentException("invalid manifest " + field);
        }
    }

    /** One declared task: owner actor role and whether it only runs on a conditional branch. */
    public record TaskDeclaration(EventTask task, EventActor actor, boolean conditional) {
        public TaskDeclaration {
            if (task == null || actor == null) {
                throw new IllegalArgumentException("task declaration requires task and actor");
            }
        }
    }

    /** One declared producer: stable producer identity, actor role and the tasks it may emit. */
    public record ProducerDeclaration(
            String producerId,
            EventActor actor,
            Set<EventTask> tasks,
            boolean criticalAuthority) {
        public ProducerDeclaration {
            if (producerId == null || producerId.isBlank() || producerId.length() > 64) {
                throw new IllegalArgumentException("invalid producer id");
            }
            if (actor == null || tasks == null || tasks.isEmpty()) {
                throw new IllegalArgumentException("producer declaration requires actor and tasks");
            }
            tasks = Set.copyOf(tasks);
        }
    }
}
