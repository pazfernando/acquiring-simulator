package com.acquiring.simulator.domain.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class EventSourceWrapperTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Path OBS_VECTORS =
            Path.of("docs/ai-sdlc/work-items/WI-ACQ-OBS-001/vectors/event-vectors.json");

    private final CanonicalEvent event = loadFixture();
    private final String hash = CanonicalEventCodec.sha256(event);

    @Test void bestEffortTaskEventCannotCarryJournalMetadata() {
        EventSourceWrapper wrapper = new EventSourceWrapper(EventDurability.TASK_BEST_EFFORT, hash,
                null, null, "2026-11-18T12:00:01Z", 1, "2026-11-18T12:00:02Z", null);

        assertThatCode(() -> validator().validateWrapper(wrapper, event)).doesNotThrowAnyException();

        EventSourceWrapper withVersion = new EventSourceWrapper(EventDurability.TASK_BEST_EFFORT, hash,
                3L, null, "2026-11-18T12:00:01Z", 1, "2026-11-18T12:00:02Z", null);
        assertThatThrownBy(() -> validator().validateWrapper(withVersion, event))
                .isInstanceOfSatisfying(EventValidationException.class,
                        e -> assertThat(e.violation()).isEqualTo(EventViolation.CRITICAL_METADATA_FORBIDDEN));

        EventSourceWrapper withState = new EventSourceWrapper(EventDurability.TASK_BEST_EFFORT, hash,
                null, null, "2026-11-18T12:00:01Z", 1, "2026-11-18T12:00:02Z", "APPROVED");
        assertThatThrownBy(() -> validator().validateWrapper(withState, event))
                .isInstanceOfSatisfying(EventValidationException.class,
                        e -> assertThat(e.violation()).isEqualTo(EventViolation.CRITICAL_METADATA_FORBIDDEN));
    }

    @Test void criticalEventCollectsAtCommitTimeAndCarriesJournalVersion() {
        EventSourceWrapper wrapper = new EventSourceWrapper(EventDurability.CRITICAL_COMMITTED, hash,
                3L, "2026-11-18T12:00:01Z", "2026-11-18T12:00:01Z", 1,
                "2026-11-18T12:00:02Z", "APPROVED");

        assertThatCode(() -> validator().validateWrapper(wrapper, event)).doesNotThrowAnyException();

        EventSourceWrapper laterCollection = new EventSourceWrapper(EventDurability.CRITICAL_COMMITTED, hash,
                3L, "2026-11-18T12:00:01Z", "2026-11-18T12:00:05Z", 1,
                "2026-11-18T12:00:06Z", "APPROVED");
        assertThatThrownBy(() -> validator().validateWrapper(laterCollection, event))
                .isInstanceOfSatisfying(EventValidationException.class,
                        e -> assertThat(e.violation()).isEqualTo(EventViolation.CRITICAL_METADATA_REQUIRED));
    }

    @Test void criticalEventRequiresJournalVersionAndCommittedState() {
        EventSourceWrapper noVersion = new EventSourceWrapper(EventDurability.CRITICAL_COMMITTED, hash,
                null, "2026-11-18T12:00:01Z", "2026-11-18T12:00:01Z", 1,
                "2026-11-18T12:00:02Z", "APPROVED");
        assertThatThrownBy(() -> validator().validateWrapper(noVersion, event))
                .isInstanceOfSatisfying(EventValidationException.class,
                        e -> assertThat(e.violation()).isEqualTo(EventViolation.CRITICAL_METADATA_REQUIRED));

        EventSourceWrapper noState = new EventSourceWrapper(EventDurability.CRITICAL_COMMITTED, hash,
                3L, "2026-11-18T12:00:01Z", "2026-11-18T12:00:01Z", 1,
                "2026-11-18T12:00:02Z", null);
        assertThatThrownBy(() -> validator().validateWrapper(noState, event))
                .isInstanceOfSatisfying(EventValidationException.class,
                        e -> assertThat(e.violation()).isEqualTo(EventViolation.MISSING_REQUIRED));
    }

    @Test void storedHashMustMatchTheCanonicalPayload() {
        EventSourceWrapper tampered = new EventSourceWrapper(EventDurability.TASK_BEST_EFFORT, "0".repeat(64),
                null, null, "2026-11-18T12:00:01Z", 1, "2026-11-18T12:00:02Z", null);

        assertThatThrownBy(() -> validator().validateWrapper(tampered, event))
                .isInstanceOfSatisfying(EventValidationException.class,
                        e -> assertThat(e.violation()).isEqualTo(EventViolation.HASH_MISMATCH));
    }

    @Test void sampledTelemetryCannotEnterTheFunctionalEventStore() {
        EventSourceWrapper sampled = new EventSourceWrapper(EventDurability.TECHNICAL_SAMPLED, hash,
                null, null, "2026-11-18T12:00:01Z", 1, "2026-11-18T12:00:02Z", null);

        assertThatThrownBy(() -> validator().validateWrapper(sampled, event))
                .isInstanceOfSatisfying(EventValidationException.class,
                        e -> assertThat(e.violation()).isEqualTo(EventViolation.NOT_A_FUNCTIONAL_EVENT));
    }

    @Test void sourceSequenceMustBeATwelveDigitCommitSequence() {
        EventSourceWrapper zero = new EventSourceWrapper(EventDurability.TASK_BEST_EFFORT, hash,
                null, null, "2026-11-18T12:00:01Z", 0, "2026-11-18T12:00:02Z", null);
        assertThatThrownBy(() -> validator().validateWrapper(zero, event))
                .isInstanceOfSatisfying(EventValidationException.class,
                        e -> assertThat(e.violation()).isEqualTo(EventViolation.OUT_OF_BOUNDS));

        EventSourceWrapper tooLarge = new EventSourceWrapper(EventDurability.TASK_BEST_EFFORT, hash,
                null, null, "2026-11-18T12:00:01Z", 1_000_000_000_000L, "2026-11-18T12:00:02Z", null);
        assertThatThrownBy(() -> validator().validateWrapper(tooLarge, event))
                .isInstanceOfSatisfying(EventValidationException.class,
                        e -> assertThat(e.violation()).isEqualTo(EventViolation.OUT_OF_BOUNDS));
    }

    private static EventValidator validator() {
        return new EventValidator();
    }

    private static CanonicalEvent loadFixture() {
        try {
            return CanonicalEventJson.parse(MAPPER.readTree(OBS_VECTORS.toFile())
                    .path("events").get(0).path("producerPayload").toString(),
                    OperationAManifest.manifest(new CriticalCapacity(5, 8)));
        } catch (Exception e) {
            throw new IllegalStateException("cannot read event fixture", e);
        }
    }
}
