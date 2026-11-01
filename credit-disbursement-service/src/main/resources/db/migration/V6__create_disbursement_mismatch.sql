CREATE TABLE disbursement_mismatch (
    -- One row per disbursement, so a job that runs twice cannot report the same case twice
    disbursement_no VARCHAR2(36) NOT NULL,
    mismatch_type   VARCHAR2(32) NOT NULL,
    our_status      VARCHAR2(16) NOT NULL,
    core_transfer   NUMBER(1)    NOT NULL,
    correlation_id  VARCHAR2(36),
    found_at        TIMESTAMP(3) NOT NULL,
    CONSTRAINT pk_disbursement_mismatch PRIMARY KEY (disbursement_no)
);

-- Finds the rows of the period the job checks
CREATE INDEX ix_disbursement_updated ON disbursement (updated_at);
