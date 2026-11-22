package com.acquiring.simulator.domain.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class EventSubmissionTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Path VECTORS =
            Path.of("docs/ai-sdlc/work-items/WI-ACQ-OBS-001/vectors/event-vectors.json");
    private static final EventManifest MANIFEST =
            OperationAManifest.manifest(new CriticalCapacity(5, 8)); // test-only example capacity

    private final CanonicalEvent event = fixture();
    private final EventContext context = new EventContext("SIM", "ACQ-A", "MERCHANT-A", "TX-001");

    @Test void dedupeKeyIsEnvironmentBoundAndDerivedFromProducerIdentity() {
        EventSubmission submission = critical(context, event);

        assertThat(submission.dedupeKey())
                .isEqualTo(new EventDedupeKey("SIM", "PROC-A", "EVENT-001"));
    }

    @Test void collectorBuildsTheWrapperForTheAllocatedSequence() {
        EventSubmission submission = critical(context, event);

        EventSourceWrapper wrapper = submission.wrapper(7, CanonicalEventCodec.sha256(event));

        assertThat(wrapper.sourceSequence()).isEqualTo(7);
        assertThat(wrapper.deliveryClass()).isEqualTo(EventDurability.CRITICAL_COMMITTED);
        assertThat(wrapper.journalVersion()).isEqualTo(1L);
        assertThat(wrapper.collectedAt()).isEqualTo(wrapper.committedAt());
        assertThat(wrapper.eventSha256()).isEqualTo(CanonicalEventCodec.sha256(event));
    }

    @Test void collectionAndStorageTimesAreRequired() {
        assertThatThrownBy(() -> new EventSubmission(context, event,
                EventDurability.TASK_BEST_EFFORT, null, null, null,
                "2026-11-18T12:00:02Z", null))
                .isInstanceOfSatisfying(EventValidationException.class,
                        e -> assertThat(e.violation()).isEqualTo(EventViolation.MISSING_REQUIRED));
        assertThatThrownBy(() -> new EventSubmission(context, event,
                EventDurability.TASK_BEST_EFFORT, null, null, "2026-11-18T12:00:01Z",
                "", null))
                .isInstanceOfSatisfying(EventValidationException.class,
                        e -> assertThat(e.violation()).isEqualTo(EventViolation.MISSING_REQUIRED));
    }

    @Test void contextRequiresTheCollectorToHaveFixedEveryReference() {
        assertThatThrownBy(() -> new EventContext(null, "ACQ-A", "MERCHANT-A", "TX-001"))
                .isInstanceOf(EventValidationException.class);
        assertThatThrownBy(() -> new EventContext("SIM", "ACQ-A", null, "TX-001"))
                .isInstanceOf(EventValidationException.class);
        assertThatThrownBy(() -> new EventContext("SIM", "ACQ-A", "MERCHANT-A", "T".repeat(65)))
                .isInstanceOfSatisfying(EventValidationException.class,
                        e -> assertThat(e.violation()).isEqualTo(EventViolation.OUT_OF_BOUNDS));
    }

    private static EventSubmission critical(EventContext context, CanonicalEvent event) {
        return new EventSubmission(context, event, EventDurability.CRITICAL_COMMITTED, 1L,
                "2026-11-18T12:00:01Z", "2026-11-18T12:00:01Z", "2026-11-18T12:00:02Z",
                "APPROVED");
    }

    private static CanonicalEvent fixture() {
        try {
            return CanonicalEventJson.parse(MAPPER.readTree(VECTORS.toFile())
                    .path("events").get(0).path("producerPayload").toString(), MANIFEST);
        } catch (Exception e) {
            throw new IllegalStateException("cannot read event fixture", e);
        }
    }
}
