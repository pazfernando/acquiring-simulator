package com.acquiring.simulator.domain.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** The two version facts are independent: absence of committed evidence is never CAS+1. */
class CommittedVersionEvidenceTest {

    @Test void claimReportsCasZeroAndCommittedOneAsSeparateFacts() {
        CommittedVersionEvidence evidence = new CommittedVersionEvidence(0L, 1L);

        assertThat(evidence.storageVersion()).isZero();
        assertThat(evidence.committedJournalVersion()).isEqualTo(1L);
        assertThat(evidence.committedVersionRecorded()).isTrue();
        assertThat(evidence.agreesWith(0L, 1L)).isTrue();
    }

    @Test void missingCommittedVersionIsExplicitAndNotDerivedFromCas() {
        CommittedVersionEvidence evidence = new CommittedVersionEvidence(0L, null);

        assertThat(evidence.committedVersionRecorded()).isFalse();
        assertThat(evidence.committedJournalVersion()).isNull();
        assertThat(evidence.agreesWith(0L, 1L)).isFalse();
    }

    @Test void committedVersionMustBePositiveWhenPresent() {
        assertThatThrownBy(() -> new CommittedVersionEvidence(0L, 0L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CommittedVersionEvidence(-1L, 1L))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
