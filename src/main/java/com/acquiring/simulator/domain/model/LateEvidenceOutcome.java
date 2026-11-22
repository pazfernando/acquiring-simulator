package com.acquiring.simulator.domain.model;

import com.acquiring.simulator.domain.event.CommittedVersionEvidence;
import java.util.Objects;

/**
 * Internal result of the service's verified late-evidence seam: the caller-visible outcome plus the
 * persisted admission facts. It is not a public HTTP response and not a P08 caller disclosure.
 *
 * @param outcome   derived from persisted state only; {@link Outcome#NOT_CONFIRMED} when the
 *                  admission is uncertain, refused or not committed
 * @param admission persisted admission result, never {@code null}
 */
public record LateEvidenceOutcome(Outcome outcome, LateEvidenceAdmission admission) {

    public LateEvidenceOutcome {
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(admission, "admission");
    }

    public LateEvidenceAdmission.Status status() {
        return admission.status();
    }

    public AuthorizationState confirmedState() {
        return admission.confirmedState();
    }

    public CommittedVersionEvidence evidence() {
        return admission.evidence();
    }

    public String detail() {
        return admission.detail();
    }

    /** True only when the admission is a recorded confirmed result with persisted version evidence. */
    public boolean recorded() {
        return admission.recorded();
    }
}
