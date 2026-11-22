package com.acquiring.simulator.domain.event;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.Test;

class OperationAManifestTest {

    /**
     * Test-only example capacity. The owner has not approved O8 numbers; production wiring must
     * inject approved values, which is why the manifest factory has no implicit default.
     */
    private static final CriticalCapacity EXAMPLE_CAPACITY = new CriticalCapacity(5, 8);

    private final EventManifest manifest = OperationAManifest.manifest(EXAMPLE_CAPACITY);

    @Test void declaresEveryOperationATaskFromTheObservationContract() {
        assertThat(manifest.tasks()).extracting(EventManifest.TaskDeclaration::task)
                .containsExactlyInAnyOrder(
                        EventTask.RECEIVE, EventTask.CONTEXT_CHECK, EventTask.VALIDATE,
                        EventTask.IDEMPOTENCY_CHECK, EventTask.REQUEST_ADAPT, EventTask.DISPATCH,
                        EventTask.RESPONSE_ADAPT, EventTask.BUSINESS_PERSIST, EventTask.CHANNEL_RESPONSE,
                        EventTask.NETWORK_FORWARD, EventTask.ISSUER_DECIDE);
    }

    @Test void networkAndIssuerTasksRemainConditionalBranches() {
        assertThat(manifest.task(EventTask.NETWORK_FORWARD).conditional()).isTrue();
        assertThat(manifest.task(EventTask.ISSUER_DECIDE).conditional()).isTrue();
        assertThat(manifest.task(EventTask.RECEIVE).conditional()).isFalse();
    }

    @Test void everyProducerTaskIsDeclaredWithTheSameActor() {
        for (EventManifest.ProducerDeclaration producer : manifest.producers()) {
            for (EventTask task : producer.tasks()) {
                EventManifest.TaskDeclaration declaration = manifest.task(task);
                assertThat(declaration).as("declared task %s", task).isNotNull();
                assertThat(declaration.actor()).as("actor for %s", task).isEqualTo(producer.actor());
            }
        }
    }

    @Test void onlyTheProcessorHoldsCriticalCommitAuthority() {
        assertThat(manifest.producers())
                .filteredOn(EventManifest.ProducerDeclaration::criticalAuthority)
                .extracting(EventManifest.ProducerDeclaration::producerId)
                .containsExactly(OperationAManifest.PRODUCER_PROCESSOR);
    }

    @Test void criticalBudgetIsInjectedNotEmbeddedInTheManifest() {
        assertThat(manifest.maxCriticalEventsPerOperation()).isEqualTo(5);
        assertThat(manifest.criticalSlotBudget()).isEqualTo(8);

        EventManifest other = OperationAManifest.manifest(new CriticalCapacity(2, 12));
        assertThat(other.maxCriticalEventsPerOperation()).isEqualTo(2);
        assertThat(other.criticalSlotBudget()).isEqualTo(12);
    }

    @Test void capacityPolicyRejectsAnInconsistentBudget() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new CriticalCapacity(5, 4))
                .isInstanceOf(IllegalArgumentException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> OperationAManifest.manifest(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void boundedReasonVocabularyIsDeclared() {
        assertThat(manifest.allowedReasons())
                .containsExactlyInAnyOrderElementsOf(Set.of(EventReason.values()));
    }
}
