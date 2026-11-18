package com.acquiring.simulator.domain.port;

import com.acquiring.simulator.domain.model.AuthorizationRecord;
import com.acquiring.simulator.domain.model.EvidenceEvent;
import com.acquiring.simulator.domain.model.RejectionAudit;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Shared persistence port; SQL stays engine-portable for SQLite and PostgreSQL. */
public interface AuthorizationRepository {

    /** Atomically claims (business key, generation); uniqueness enforces at most one dispatch. */
    void claim(AuthorizationRecord record);

    void markDispatched(String authorizationId, String correlationId, Instant dispatchedAt);

    void updateOutcome(
            String authorizationId,
            String state,
            String decisionSource,
            String reasonCategory,
            Instant outcomeDeterminedAt);

    Optional<AuthorizationRecord> findById(String id);

    Optional<AuthorizationRecord> findLatestByBusinessKey(String businessKey);

    List<EvidenceEvent> eventsFor(String authorizationId);

    void appendEvent(EvidenceEvent event);

    void appendRejection(RejectionAudit audit);
}
