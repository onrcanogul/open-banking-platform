CREATE TABLE disbursement (
    disbursement_no VARCHAR2(36)  NOT NULL,
    partner_code    VARCHAR2(16)  NOT NULL,
    idempotency_key VARCHAR2(64)  NOT NULL,
    -- Tells a retry of the same request from a new request reusing the key by mistake
    request_hash    VARCHAR2(64)  NOT NULL,
    customer_no     VARCHAR2(32)  NOT NULL,
    account_no      VARCHAR2(34)  NOT NULL,
    amount          NUMBER(18, 2) NOT NULL,
    currency        VARCHAR2(3)   NOT NULL,
    status          VARCHAR2(16)  NOT NULL,
    failure_reason  VARCHAR2(256),
    created_at      TIMESTAMP(3)  NOT NULL,
    updated_at      TIMESTAMP(3)  NOT NULL,
    version         NUMBER(10)    NOT NULL,
    CONSTRAINT pk_disbursement PRIMARY KEY (disbursement_no),
    -- The guarantee against paying twice: a key can start only one disbursement
    CONSTRAINT uq_disbursement_key UNIQUE (partner_code, idempotency_key)
);
