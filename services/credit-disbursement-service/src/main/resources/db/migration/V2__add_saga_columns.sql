ALTER TABLE disbursement ADD (
    next_step_at   TIMESTAMP(3) DEFAULT SYS_EXTRACT_UTC(SYSTIMESTAMP) NOT NULL,
    correlation_id VARCHAR2(36)
);

-- The saga worker looks for rows whose time has come
CREATE INDEX ix_disbursement_due ON disbursement (status, next_step_at);
