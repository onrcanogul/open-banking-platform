CREATE TABLE disbursement_outbox (
    event_no        VARCHAR2(36) NOT NULL,
    disbursement_no VARCHAR2(36) NOT NULL,
    partner_code    VARCHAR2(16) NOT NULL,
    final_status    VARCHAR2(16) NOT NULL,
    failure_reason  VARCHAR2(256),
    occurred_at     TIMESTAMP(3) NOT NULL,
    correlation_id  VARCHAR2(36),
    status          VARCHAR2(16) NOT NULL,
    published_at    TIMESTAMP(3),
    CONSTRAINT pk_disbursement_outbox PRIMARY KEY (event_no)
);

-- The relay reads pending events oldest first
CREATE INDEX ix_disbursement_outbox_pending ON disbursement_outbox (status, occurred_at);
