CREATE TABLE event_subscription_type (
    subscription_no VARCHAR2(36) NOT NULL,
    event_type      VARCHAR2(36) NOT NULL,
    resource_type   VARCHAR2(36) NOT NULL,
    CONSTRAINT pk_event_subscription_type PRIMARY KEY (subscription_no, event_type, resource_type),
    CONSTRAINT fk_event_subscription_type FOREIGN KEY (subscription_no)
        REFERENCES event_subscription (subscription_no) ON DELETE CASCADE
);
