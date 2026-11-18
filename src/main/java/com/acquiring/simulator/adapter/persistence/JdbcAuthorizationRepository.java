package com.acquiring.simulator.adapter.persistence;

import com.acquiring.simulator.domain.model.AuthorizationRecord;
import com.acquiring.simulator.domain.model.AuthorizationState;
import com.acquiring.simulator.domain.model.CvmIndicator;
import com.acquiring.simulator.domain.model.DecisionSource;
import com.acquiring.simulator.domain.model.EntryMode;
import com.acquiring.simulator.domain.model.EvidenceEvent;
import com.acquiring.simulator.domain.model.RejectionAudit;
import com.acquiring.simulator.domain.model.RiskIndicator;
import com.acquiring.simulator.domain.model.Route;
import com.acquiring.simulator.domain.port.AuthorizationRepository;
import com.acquiring.simulator.domain.port.DuplicateClaimException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JdbcAuthorizationRepository implements AuthorizationRepository {

    private final JdbcClient jdbc;

    public JdbcAuthorizationRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public void claim(AuthorizationRecord r) {
        try {
            jdbc.sql("""
                    INSERT INTO authorization_record (
                        id, business_key, acquirer_ref, merchant_ref, channel_payment_ref,
                        amount_minor, currency, credential_ref, terminal_ref, entry_mode,
                        cvm_indicator, cryptogram_present, risk_indicator, route,
                        payment_fingerprint, generation, state, decision_source, reason_category,
                        dispatch_correlation_id, first_received_at, duplicate_window_end,
                        dispatched_at, outcome_determined_at
                    ) VALUES (
                        :id, :businessKey, :acquirerRef, :merchantRef, :channelPaymentRef,
                        :amountMinor, :currency, :credentialRef, :terminalRef, :entryMode,
                        :cvmIndicator, :cryptogramPresent, :riskIndicator, :route,
                        :paymentFingerprint, :generation, :state, :decisionSource, :reasonCategory,
                        :dispatchCorrelationId, :firstReceivedAt, :duplicateWindowEnd,
                        :dispatchedAt, :outcomeDeterminedAt
                    )
                    """)
                    .param("id", r.id())
                    .param("businessKey", r.businessKey())
                    .param("acquirerRef", r.acquirerRef())
                    .param("merchantRef", r.merchantRef())
                    .param("channelPaymentRef", r.channelPaymentRef())
                    .param("amountMinor", r.amountMinor())
                    .param("currency", r.currency())
                    .param("credentialRef", r.credentialRef())
                    .param("terminalRef", r.terminalRef())
                    .param("entryMode", r.entryMode().name())
                    .param("cvmIndicator", r.cvmIndicator().name())
                    .param("cryptogramPresent", r.cryptogramPresent())
                    .param("riskIndicator", r.riskIndicator().name())
                    .param("route", r.route().name())
                    .param("paymentFingerprint", r.paymentFingerprint())
                    .param("generation", r.generation())
                    .param("state", r.state().name())
                    .param("decisionSource", r.decisionSource().name())
                    .param("reasonCategory", r.reasonCategory())
                    .param("dispatchCorrelationId", r.dispatchCorrelationId())
                    .param("firstReceivedAt", r.firstReceivedAt().toEpochMilli())
                    .param("duplicateWindowEnd", r.duplicateWindowEnd().toEpochMilli())
                    .param("dispatchedAt", epochOrNull(r.dispatchedAt()))
                    .param("outcomeDeterminedAt", epochOrNull(r.outcomeDeterminedAt()))
                    .update();
        } catch (DataAccessException e) {
            throw new DuplicateClaimException(r.businessKey(), r.generation(), e);
        }
    }

    @Override
    @Transactional
    public void markDispatched(String authorizationId, String correlationId, Instant dispatchedAt) {
        jdbc.sql("""
                UPDATE authorization_record
                   SET state = :state, dispatch_correlation_id = :correlationId,
                       dispatched_at = :dispatchedAt
                 WHERE id = :id
                """)
                .param("state", AuthorizationState.AWAITING.name())
                .param("correlationId", correlationId)
                .param("dispatchedAt", dispatchedAt.toEpochMilli())
                .param("id", authorizationId)
                .update();
    }

    @Override
    @Transactional
    public void updateOutcome(
            String authorizationId,
            String state,
            String decisionSource,
            String reasonCategory,
            Instant outcomeDeterminedAt) {
        jdbc.sql("""
                UPDATE authorization_record
                   SET state = :state, decision_source = :decisionSource,
                       reason_category = :reasonCategory, outcome_determined_at = :determinedAt
                 WHERE id = :id
                """)
                .param("state", state)
                .param("decisionSource", decisionSource)
                .param("reasonCategory", reasonCategory)
                .param("determinedAt", outcomeDeterminedAt.toEpochMilli())
                .param("id", authorizationId)
                .update();
    }

    @Override
    public Optional<AuthorizationRecord> findById(String id) {
        return jdbc.sql("SELECT * FROM authorization_record WHERE id = :id")
                .param("id", id)
                .query(this::map)
                .optional();
    }

    @Override
    public Optional<AuthorizationRecord> findLatestByBusinessKey(String businessKey) {
        return jdbc.sql("""
                SELECT * FROM authorization_record
                 WHERE business_key = :businessKey
                 ORDER BY generation DESC
                 LIMIT 1
                """)
                .param("businessKey", businessKey)
                .query(this::map)
                .optional();
    }

    @Override
    public List<EvidenceEvent> eventsFor(String authorizationId) {
        return jdbc.sql("""
                SELECT * FROM evidence_event
                 WHERE authorization_id = :authorizationId
                 ORDER BY recorded_at, id
                """)
                .param("authorizationId", authorizationId)
                .query((rs, rowNum) -> new EvidenceEvent(
                        rs.getString("id"),
                        rs.getString("authorization_id"),
                        rs.getString("event_type"),
                        rs.getString("decision"),
                        DecisionSource.valueOf(rs.getString("source")),
                        rs.getString("correlation_id"),
                        rs.getString("detail"),
                        rs.getLong("recorded_at")))
                .list();
    }

    @Override
    @Transactional
    public void appendEvent(EvidenceEvent event) {
        jdbc.sql("""
                INSERT INTO evidence_event (
                    id, authorization_id, event_type, decision, source,
                    correlation_id, detail, recorded_at
                ) VALUES (
                    :id, :authorizationId, :eventType, :decision, :source,
                    :correlationId, :detail, :recordedAt
                )
                """)
                .param("id", event.id())
                .param("authorizationId", event.authorizationId())
                .param("eventType", event.eventType())
                .param("decision", event.decision())
                .param("source", event.source().name())
                .param("correlationId", event.correlationId())
                .param("detail", event.detail())
                .param("recordedAt", event.recordedAt())
                .update();
    }

    @Override
    @Transactional
    public void appendRejection(RejectionAudit audit) {
        jdbc.sql("""
                INSERT INTO rejection_audit (
                    id, acquirer_ref, merchant_ref, caller_id,
                    channel_payment_ref, reason_category, recorded_at
                ) VALUES (
                    :id, :acquirerRef, :merchantRef, :callerId,
                    :channelPaymentRef, :reasonCategory, :recordedAt
                )
                """)
                .param("id", audit.id())
                .param("acquirerRef", audit.acquirerRef())
                .param("merchantRef", audit.merchantRef())
                .param("callerId", audit.callerId())
                .param("channelPaymentRef", audit.channelPaymentRef())
                .param("reasonCategory", audit.reason().name())
                .param("recordedAt", audit.recordedAt())
                .update();
    }

    private AuthorizationRecord map(ResultSet rs, int rowNum) throws SQLException {
        return new AuthorizationRecord(
                rs.getString("id"),
                rs.getString("business_key"),
                rs.getString("acquirer_ref"),
                rs.getString("merchant_ref"),
                rs.getString("channel_payment_ref"),
                rs.getLong("amount_minor"),
                rs.getString("currency"),
                rs.getString("credential_ref"),
                rs.getString("terminal_ref"),
                EntryMode.valueOf(rs.getString("entry_mode")),
                CvmIndicator.valueOf(rs.getString("cvm_indicator")),
                rs.getBoolean("cryptogram_present"),
                RiskIndicator.valueOf(rs.getString("risk_indicator")),
                Route.valueOf(rs.getString("route")),
                rs.getString("payment_fingerprint"),
                rs.getInt("generation"),
                AuthorizationState.valueOf(rs.getString("state")),
                DecisionSource.valueOf(rs.getString("decision_source")),
                rs.getString("reason_category"),
                rs.getString("dispatch_correlation_id"),
                Instant.ofEpochMilli(rs.getLong("first_received_at")),
                instantOrNull(rs.getObject("dispatched_at")),
                instantOrNull(rs.getObject("outcome_determined_at")),
                Instant.ofEpochMilli(rs.getLong("duplicate_window_end")));
    }

    private static Long epochOrNull(Instant instant) {
        return instant == null ? null : instant.toEpochMilli();
    }

    private static Instant instantOrNull(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return Instant.ofEpochMilli(number.longValue());
        }
        return Instant.ofEpochMilli(Long.parseLong(value.toString()));
    }
}
