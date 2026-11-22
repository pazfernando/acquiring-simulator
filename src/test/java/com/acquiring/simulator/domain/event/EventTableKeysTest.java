package com.acquiring.simulator.domain.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class EventTableKeysTest {

    private final EventContext context =
            new EventContext("SIM", "ACQ-A", "MERCHANT-A", "TX-001");

    @Test void eventPartitionFollowsTheApprovedEnvironmentAcquirerMerchantTransactionOrder() {
        assertThat(EventTableKeys.eventPartition(context))
                .isEqualTo("ENV#SIM#ACQ#ACQ-A#MID#MERCHANT-A#TX#TX-001");
    }

    @Test void eventSortKeyIsTwelveDigitZeroPaddedCommitOrder() {
        assertThat(EventTableKeys.eventSortKey(1)).isEqualTo("E#000000000001");
        assertThat(EventTableKeys.eventSortKey(42)).isEqualTo("E#000000000042");
        assertThat(EventTableKeys.eventSortKey(999_999_999_999L)).isEqualTo("E#999999999999");
        assertThat(EventTableKeys.eventSortKey(2)).isGreaterThan(EventTableKeys.eventSortKey(1));
    }

    @Test void headAndDedupeKeysAreStable() {
        assertThat(EventTableKeys.headSortKey()).isEqualTo("HEAD");
        assertThat(EventTableKeys.dedupePartition("SIM")).isEqualTo("ENV#SIM#DEDUPE");
        assertThat(EventTableKeys.dedupeSortKey(new EventDedupeKey("SIM", "PROC-A", "EVENT-001")))
                .isEqualTo("P#PROC-A#E#EVENT-001");
    }

    @Test void sequenceOutsideTheTwelveDigitRangeIsRejected() {
        assertThatThrownBy(() -> EventTableKeys.eventSortKey(0))
                .isInstanceOfSatisfying(EventValidationException.class,
                        e -> assertThat(e.violation()).isEqualTo(EventViolation.OUT_OF_BOUNDS));
        assertThatThrownBy(() -> EventTableKeys.eventSortKey(1_000_000_000_000L))
                .isInstanceOfSatisfying(EventValidationException.class,
                        e -> assertThat(e.violation()).isEqualTo(EventViolation.OUT_OF_BOUNDS));
    }

    @Test void contextIsBoundedAndSanitized() {
        assertThatThrownBy(() -> new EventContext("", "ACQ-A", "MERCHANT-A", "TX-001"))
                .isInstanceOfSatisfying(EventValidationException.class,
                        e -> assertThat(e.violation()).isEqualTo(EventViolation.MISSING_REQUIRED));
        assertThatThrownBy(() -> new EventContext("SIM", "A".repeat(129), "MERCHANT-A", "TX-001"))
                .isInstanceOfSatisfying(EventValidationException.class,
                        e -> assertThat(e.violation()).isEqualTo(EventViolation.OUT_OF_BOUNDS));
        assertThatThrownBy(() -> new EventContext("SIM", "1234567890123456", "MERCHANT-A", "TX-001"))
                .isInstanceOfSatisfying(EventValidationException.class,
                        e -> assertThat(e.violation()).isEqualTo(EventViolation.PROHIBITED_CONTENT));
    }
}
