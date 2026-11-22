package com.acquiring.simulator.domain.event;

/**
 * Read representation of the two version facts a claim record can carry (observation contract O1a /
 * channel contract C3).
 *
 * <p>{@code storageVersion} is the internal lost-update CAS counter of the journal write path; it is
 * still {@code 0} for a newly claimed record. {@code committedJournalVersion} is the public committed
 * journal version and is {@code 1} for the initial claim. They are separate persisted facts: this
 * type never derives one from the other, so {@code committedJournalVersion == null} means the row
 * records no committed-version evidence (rows written before additive persistence) and must not be
 * reported as {@code storageVersion + 1}.
 */
public record CommittedVersionEvidence(long storageVersion, Long committedJournalVersion) {

    public CommittedVersionEvidence {
        if (storageVersion < 0) {
            throw new IllegalArgumentException("storage version cannot be negative");
        }
        if (committedJournalVersion != null && committedJournalVersion < 1) {
            throw new IllegalArgumentException("committed journal version must be positive");
        }
    }

    /** True when the row carries an explicit committed journal version. */
    public boolean committedVersionRecorded() {
        return committedJournalVersion != null;
    }

    /** True only when both the CAS counter and the committed version were read from the row. */
    public boolean agreesWith(long expectedStorageVersion, long expectedCommittedJournalVersion) {
        return storageVersion == expectedStorageVersion
                && committedJournalVersion != null
                && committedJournalVersion == expectedCommittedJournalVersion;
    }
}
