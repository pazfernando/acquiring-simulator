package com.acquiring.simulator.domain.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Executes the approved canonical event fixtures (EV1–EV5) against the RFC 8785 codec. These are
 * the first executed checks for that vector set; other observation vectors remain unexecuted.
 */
class CanonicalEventCodecTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Path VECTORS =
            Path.of("docs/ai-sdlc/work-items/WI-ACQ-OBS-001/vectors/event-vectors.json");

    @Test void canonicalJsonAndHashMatchTheApprovedEv1Oracle() throws Exception {
        JsonNode vectors = MAPPER.readTree(VECTORS.toFile());
        JsonNode ev1 = vectors.path("events").get(0);

        CanonicalEvent event = CanonicalEventJson.parse(ev1.path("producerPayload").toString());

        assertThat(CanonicalEventCodec.canonicalJson(event)).isEqualTo(vectors.path("canonicalJson").asText());
        assertThat(CanonicalEventCodec.sha256(event)).isEqualTo(ev1.path("canonicalSha256").asText());
        assertThat(CanonicalEventCodec.sha256(event))
                .isEqualTo("ed1044b9562b6cb62da04bc541a5a6eaab9df2ed321f8ccfd65b2f2a298687b5");
    }

    @Test void redeliveryUnderANewBootKeepsTheOriginalPayloadAndHash() throws Exception {
        JsonNode events = MAPPER.readTree(VECTORS.toFile()).path("events");
        CanonicalEvent original = CanonicalEventJson.parse(events.get(0).path("producerPayload").toString());
        CanonicalEvent redelivered = CanonicalEventJson.parse(events.get(4).path("producerPayload").toString());

        assertThat(redelivered.instanceId()).isEqualTo("BOOT-001");
        assertThat(CanonicalEventCodec.sha256(redelivered)).isEqualTo(CanonicalEventCodec.sha256(original));
        assertThat(new EventDedupeKey("SIM", redelivered.producerId(), redelivered.eventId()))
                .isEqualTo(new EventDedupeKey("SIM", original.producerId(), original.eventId()));
        assertThat(EventDeduplication.classify(
                CanonicalEventCodec.sha256(original), CanonicalEventCodec.sha256(redelivered)))
                .isEqualTo(EventDeduplication.Decision.EXACT_REPLAY);
    }

    @Test void changedStatusIsAContentConflictNotASecondEvent() throws Exception {
        JsonNode ev3 = MAPPER.readTree(VECTORS.toFile()).path("events").get(2);
        CanonicalEvent original = CanonicalEventJson.parse(
                MAPPER.readTree(VECTORS.toFile()).path("events").get(0).path("producerPayload").toString());
        CanonicalEvent changed = CanonicalEventJson.parse(ev3.path("producerPayload").toString());

        assertThat(EventDeduplication.classify(
                CanonicalEventCodec.sha256(original), CanonicalEventCodec.sha256(changed)))
                .isEqualTo(EventDeduplication.Decision.CONTENT_CONFLICT);
    }

    @Test void contextChangeAltersTheCanonicalHashSoItCannotMasqueradeAsOriginal() throws Exception {
        JsonNode events = MAPPER.readTree(VECTORS.toFile()).path("events");
        CanonicalEvent original = CanonicalEventJson.parse(events.get(0).path("producerPayload").toString());
        CanonicalEvent forged = CanonicalEventJson.parse(events.get(3).path("producerPayload").toString());

        assertThat(forged.acquirerRef()).isEqualTo("ACQ-B");
        assertThat(CanonicalEventCodec.sha256(forged)).isNotEqualTo(CanonicalEventCodec.sha256(original));
    }

    @Test void undeclaredPayloadFieldsAreRejected() throws Exception {
        ObjectNode payload = payload(0);
        payload.put("rawIso", "0200");

        assertThatThrownBy(() -> CanonicalEventJson.parse(payload.toString()))
                .isInstanceOfSatisfying(EventValidationException.class,
                        e -> assertThat(e.violation()).isEqualTo(EventViolation.UNDECLARED_FIELD));
    }

    @Test void producerCannotSupplyCollectorFields() throws Exception {
        ObjectNode payload = payload(0);
        payload.put("collectedAt", "2026-11-18T12:00:09Z");

        assertThatThrownBy(() -> CanonicalEventJson.parse(payload.toString()))
                .isInstanceOfSatisfying(EventValidationException.class,
                        e -> assertThat(e.violation()).isEqualTo(EventViolation.COLLECTOR_FIELD_FORBIDDEN));
    }

    @Test void absentOptionalValuesSerializeAsExplicitNulls() throws Exception {
        CanonicalEvent event = CanonicalEventJson.parse(payload(0).toString());

        assertThat(CanonicalEventCodec.canonicalJson(event))
                .contains("\"clockQuality\":null")
                .contains("\"parentTaskId\":null")
                .contains("\"gapCode\":null");
    }

    private static ObjectNode payload(int index) throws Exception {
        return (ObjectNode) MAPPER.readTree(VECTORS.toFile()).path("events").get(index).path("producerPayload");
    }
}
