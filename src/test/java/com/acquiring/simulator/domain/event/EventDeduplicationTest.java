package com.acquiring.simulator.domain.event;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class EventDeduplicationTest {

    @Test void unseenIdentityIsNew() {
        assertThat(EventDeduplication.classify(null, "a".repeat(64)))
                .isEqualTo(EventDeduplication.Decision.NEW);
        assertThat(EventDeduplication.classify("  ", "a".repeat(64)))
                .isEqualTo(EventDeduplication.Decision.NEW);
    }

    @Test void sameHashIsExactReplayAndDifferentHashIsConflict() {
        assertThat(EventDeduplication.classify("a".repeat(64), "a".repeat(64)))
                .isEqualTo(EventDeduplication.Decision.EXACT_REPLAY);
        assertThat(EventDeduplication.classify("a".repeat(64), "b".repeat(64)))
                .isEqualTo(EventDeduplication.Decision.CONTENT_CONFLICT);
    }

    @Test void dedupeIdentityIgnoresTheBootInstance() {
        EventDedupeKey firstBoot = new EventDedupeKey("SIM", "PROC-A", "EVENT-001");
        EventDedupeKey secondBoot = new EventDedupeKey("SIM", "PROC-A", "EVENT-001");

        assertThat(firstBoot).isEqualTo(secondBoot);
        assertThat(firstBoot).isNotEqualTo(new EventDedupeKey("SIM", "PROC-B", "EVENT-001"));
        assertThat(firstBoot).isNotEqualTo(new EventDedupeKey("OTHER", "PROC-A", "EVENT-001"));
    }
}
