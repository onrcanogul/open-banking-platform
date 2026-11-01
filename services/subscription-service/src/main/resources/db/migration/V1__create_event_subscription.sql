CREATE TABLE event_subscription (
    subscription_no VARCHAR2(36) NOT NULL,
    yos_code        VARCHAR2(4)  NOT NULL,
    created_at      TIMESTAMP(3) DEFAULT SYSTIMESTAMP NOT NULL,
    updated_at      TIMESTAMP(3) DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT pk_event_subscription PRIMARY KEY (subscription_no),
    -- The standard allows one subscription per YOS at each HHS
    CONSTRAINT uq_event_subscription_yos UNIQUE (yos_code)
);
