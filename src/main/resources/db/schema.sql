-- Portable schema for SQLite (local) and PostgreSQL (test/prod).
-- Synthetic identifiers and normalised evidence only; no raw request payloads.
CREATE TABLE IF NOT EXISTS authorization_record (
    id VARCHAR(64) PRIMARY KEY,
    business_key VARCHAR(768) NOT NULL,
    acquirer_ref VARCHAR(64) NOT NULL,
    merchant_ref VARCHAR(64) NOT NULL,
    channel_payment_ref VARCHAR(128) NOT NULL,
    amount_minor BIGINT NOT NULL,
    currency VARCHAR(3) NOT NULL,
    credential_ref VARCHAR(32) NOT NULL,
    terminal_ref VARCHAR(64) NOT NULL,
    entry_mode VARCHAR(32) NOT NULL,
    cvm_indicator VARCHAR(32) NOT NULL,
    cryptogram_present BOOLEAN NOT NULL,
    risk_indicator VARCHAR(16) NOT NULL,
    route VARCHAR(32) NOT NULL,
    payment_fingerprint VARCHAR(64) NOT NULL,
    generation INTEGER NOT NULL,
    state VARCHAR(32) NOT NULL,
    decision_source VARCHAR(16) NOT NULL,
    reason_category VARCHAR(64),
    dispatch_correlation_id VARCHAR(64),
    first_received_at BIGINT NOT NULL,
    duplicate_window_end BIGINT NOT NULL,
    dispatched_at BIGINT,
    outcome_determined_at BIGINT
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_auth_business_generation
    ON authorization_record (business_key, generation);
CREATE INDEX IF NOT EXISTS ix_auth_business_received
    ON authorization_record (business_key, first_received_at);

CREATE TABLE IF NOT EXISTS evidence_event (
    id VARCHAR(64) PRIMARY KEY,
    authorization_id VARCHAR(64) NOT NULL,
    event_type VARCHAR(48) NOT NULL,
    decision VARCHAR(16),
    source VARCHAR(16),
    correlation_id VARCHAR(64),
    detail VARCHAR(128),
    recorded_at BIGINT NOT NULL
);
CREATE INDEX IF NOT EXISTS ix_evidence_authorization
    ON evidence_event (authorization_id, recorded_at);

CREATE TABLE IF NOT EXISTS rejection_audit (
    id VARCHAR(64) PRIMARY KEY,
    acquirer_ref VARCHAR(64),
    merchant_ref VARCHAR(64),
    caller_id VARCHAR(64),
    channel_payment_ref VARCHAR(128),
    reason_category VARCHAR(64) NOT NULL,
    recorded_at BIGINT NOT NULL
);
