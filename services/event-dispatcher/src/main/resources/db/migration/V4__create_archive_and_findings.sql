CREATE TABLE event_delivery_archive (
    event_no        VARCHAR2(36)  NOT NULL,
    yos_code        VARCHAR2(4)   NOT NULL,
    event_type      VARCHAR2(36)  NOT NULL,
    resource_type   VARCHAR2(36)  NOT NULL,
    resource_no     VARCHAR2(128) NOT NULL,
    occurred_at     TIMESTAMP(3)  NOT NULL,
    status          VARCHAR2(16)  NOT NULL,
    failed_attempts NUMBER(2)     NOT NULL,
    last_error      VARCHAR2(256),
    finished_at     TIMESTAMP(3),
    correlation_id  VARCHAR2(36),
    archived_at     TIMESTAMP(3)  NOT NULL,
    CONSTRAINT pk_event_delivery_archive PRIMARY KEY (event_no)
);

-- Finds finished rows old enough to archive without reading the whole table
CREATE INDEX ix_event_delivery_finished ON event_delivery (finished_at);

CREATE TABLE reconciliation_finding (
    -- The event number as key lets the job run twice without double reports
    event_no       VARCHAR2(36)  NOT NULL,
    yos_code       VARCHAR2(4)   NOT NULL,
    event_type     VARCHAR2(36)  NOT NULL,
    resource_type  VARCHAR2(36)  NOT NULL,
    resource_no    VARCHAR2(128) NOT NULL,
    occurred_at    TIMESTAMP(3)  NOT NULL,
    correlation_id VARCHAR2(36),
    found_at       TIMESTAMP(3)  NOT NULL,
    CONSTRAINT pk_reconciliation_finding PRIMARY KEY (event_no)
);
