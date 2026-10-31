CREATE TABLE event_delivery (
    -- The event number as key makes a redelivered Kafka message harmless
    event_no        VARCHAR2(36)  NOT NULL,
    yos_code        VARCHAR2(4)   NOT NULL,
    event_type      VARCHAR2(36)  NOT NULL,
    resource_type   VARCHAR2(36)  NOT NULL,
    resource_no     VARCHAR2(128) NOT NULL,
    occurred_at     TIMESTAMP(3)  NOT NULL,
    status          VARCHAR2(16)  NOT NULL,
    failed_attempts NUMBER(2)     NOT NULL,
    next_attempt_at TIMESTAMP(3)  NOT NULL,
    last_error      VARCHAR2(256),
    finished_at     TIMESTAMP(3),
    CONSTRAINT pk_event_delivery PRIMARY KEY (event_no)
);

-- The scheduler looks for pending rows whose time has come
CREATE INDEX ix_event_delivery_due ON event_delivery (status, next_attempt_at);

-- The undelivered events query reads one YOS by event time
CREATE INDEX ix_event_delivery_undelivered ON event_delivery (yos_code, status, occurred_at);
