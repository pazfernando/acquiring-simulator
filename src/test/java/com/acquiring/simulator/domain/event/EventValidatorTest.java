package com.acquiring.simulator.domain.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class EventValidatorTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Path VECTORS =
            Path.of("docs/ai-sdlc/work-items/WI-ACQ-OBS-001/vectors/event-vectors.json");
    private static final EventManifest MANIFEST =
            OperationAManifest.manifest(new CriticalCapacity(5, 8)); // test-only example capacity

    @Test void approvedFixtureIsAdmittedByTheOperationAManifest() throws Exception {
        assertThatCode(() -> admit(fixture())).doesNotThrowAnyException();
    }

    @Test void missingRequiredFieldIsRejected() throws Exception {
        ObjectNode payload = fixture();
        payload.remove("eventId");

        assertViolation(payload, EventViolation.MISSING_REQUIRED, "eventId");
    }

    @Test void identifierLongerThanSixtyFourCharactersIsRejected() throws Exception {
        ObjectNode payload = fixture();
        payload.put("eventId", "E".repeat(65));

        assertViolation(payload, EventViolation.OUT_OF_BOUNDS, "eventId");
    }

    @Test void contextReferenceLongerThanOneHundredTwentyEightCharactersIsRejected() throws Exception {
        ObjectNode payload = fixture();
        payload.put("acquirerRef", "A".repeat(129));

        assertViolation(payload, EventViolation.OUT_OF_BOUNDS, "acquirerRef");
    }

    @Test void nonUtcOrMalformedTimesAreRejected() throws Exception {
        ObjectNode offsetTime = fixture();
        offsetTime.put("occurredAt", "2026-11-18T12:00:00+00:00");
        assertViolation(offsetTime, EventViolation.MALFORMED_TIME, "occurredAt");

        ObjectNode malformed = fixture();
        malformed.put("occurredAt", "2026-11-18 12:00:00");
        assertViolation(malformed, EventViolation.MALFORMED_TIME, "occurredAt");
    }

    @Test void negativeProducerSequenceIsRejected() throws Exception {
        ObjectNode payload = fixture();
        payload.put("producerSequence", -1);

        assertViolation(payload, EventViolation.NEGATIVE_VALUE, "producerSequence");
    }

    @Test void unknownTaskIsRejectedAtIngress() throws Exception {
        ObjectNode payload = fixture();
        payload.put("taskName", "teleport");

        assertViolation(payload, EventViolation.UNKNOWN_TASK, "taskName");
    }

    @Test void actorMustMatchTheDeclaredTaskActor() throws Exception {
        ObjectNode payload = fixture();
        payload.put("actor", "ACL");

        assertViolation(payload, EventViolation.TASK_ACTOR_MISMATCH, "actor");
    }

    @Test void undeclaredProducerIsRejected() throws Exception {
        ObjectNode payload = fixture();
        payload.put("producerId", "PROC-X");

        assertViolation(payload, EventViolation.PRODUCER_NOT_DECLARED, "producerId");
    }

    @Test void producerCannotEmitATaskItIsNotAssignedTo() throws Exception {
        ObjectNode payload = fixture();
        payload.put("taskName", "receive");
        payload.put("actor", "CHANNEL");

        assertViolation(payload, EventViolation.PRODUCER_NOT_ASSIGNED_TO_TASK, "producerId");
    }

    @Test void manifestVersionMustMatchTheDeclaredManifest() throws Exception {
        ObjectNode payload = fixture();
        payload.put("manifestVersion", "A-2");

        assertViolation(payload, EventViolation.MANIFEST_MISMATCH, "manifestVersion");
    }

    @Test void skippedTaskNeedsDeclaredReasonOrEvidence() throws Exception {
        ObjectNode payload = fixture();
        payload.put("status", "SKIPPED");

        assertViolation(payload, EventViolation.COVERAGE_GAP_NOT_ALLOWED, "status");

        payload.put("reason", "SKIPPED_NOT_APPLICABLE");
        assertThatCode(() -> admit(payload)).doesNotThrowAnyException();
    }

    @Test void elapsedDurationRequiresSameClockStartAndEnd() throws Exception {
        ObjectNode unknownClock = fixture();
        unknownClock.put("elapsedMicros", 1200);
        unknownClock.put("clockQuality", "UNKNOWN");
        assertViolation(unknownClock, EventViolation.INVALID_FORMAT, "clockQuality");

        ObjectNode missingBounds = fixture();
        missingBounds.put("elapsedMicros", 1200);
        missingBounds.put("clockQuality", "SAME_MONOTONIC");
        assertViolation(missingBounds, EventViolation.MISSING_REQUIRED, "startedAt");

        missingBounds.put("startedAt", "2026-11-18T12:00:00Z");
        missingBounds.put("endedAt", "2026-11-18T12:00:00.001200Z");
        assertThatCode(() -> admit(missingBounds)).doesNotThrowAnyException();
    }

    @Test void maximallyBoundedEventStaysWithinTheCanonicalPayloadLimit() {
        CanonicalEvent event = CanonicalEvent.builder()
                .eventId("E".repeat(64))
                .producerId(OperationAManifest.PRODUCER_PROCESSOR)
                .instanceId("I".repeat(64))
                .actor(EventActor.PROCESSOR)
                .capability(OperationAManifest.CAPABILITY)
                .version("V".repeat(32))
                .manifestVersion(OperationAManifest.MANIFEST_VERSION)
                .acquirerRef("A".repeat(128))
                .merchantRef("M".repeat(128))
                .terminalRef("T".repeat(128))
                .transactionId("X".repeat(64))
                .operationId("O".repeat(64))
                .attemptId("P".repeat(64))
                .correlationId("C".repeat(64))
                .taskExecutionId("K".repeat(64))
                .taskName(EventTask.VALIDATE)
                .status(EventStatus.SUCCEEDED)
                .occurredAt("2026-11-18T12:00:00Z")
                .producerSequence(1)
                .provenance(EventProvenance.OBSERVED)
                .reason(EventReason.VALID)
                .parentTaskId("Q".repeat(64))
                .parentEventId("R".repeat(64))
                .retryOfAttemptId("S".repeat(64))
                .originalOperationId("U".repeat(64))
                .evidenceRef("V".repeat(64))
                .build();

        assertThatCode(() -> new EventValidator().validate(event, MANIFEST)).doesNotThrowAnyException();
        assertThat(CanonicalEventCodec.canonicalBytes(event).length)
                .isLessThanOrEqualTo(EventValidator.MAX_CANONICAL_BYTES);
    }

    private static void assertViolation(ObjectNode payload, EventViolation violation, String field) {
        assertThatThrownBy(() -> admit(payload))
                .isInstanceOfSatisfying(EventValidationException.class, e -> {
                    assertThat(e.violation()).isEqualTo(violation);
                    assertThat(e.field()).isEqualTo(field);
                });
    }

    private static CanonicalEvent admit(ObjectNode payload) {
        return CanonicalEventJson.parse(payload.toString(), MANIFEST);
    }

    private static ObjectNode fixture() throws Exception {
        return (ObjectNode) MAPPER.readTree(VECTORS.toFile())
                .path("events").get(0).path("producerPayload");
    }
}
