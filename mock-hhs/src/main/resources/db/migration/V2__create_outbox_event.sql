CREATE TABLE outbox_event (
    event_no      VARCHAR2(36)  NOT NULL,
    yos_code      VARCHAR2(4)   NOT NULL,
    event_type    VARCHAR2(36)  NOT NULL,
    resource_type VARCHAR2(36)  NOT NULL,
    resource_no   VARCHAR2(128) NOT NULL,
    occurred_at   TIMESTAMP(3)  NOT NULL,
    status        VARCHAR2(16)  NOT NULL,
    published_at  TIMESTAMP(3),
    CONSTRAINT pk_outbox_event PRIMARY KEY (event_no)
);

-- The relay reads pending events oldest first
CREATE INDEX ix_outbox_event_pending ON outbox_event (status, occurred_at);
