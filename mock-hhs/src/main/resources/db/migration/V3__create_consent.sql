CREATE TABLE consent (
    consent_no         VARCHAR2(36)  NOT NULL,
    yos_code           VARCHAR2(4)   NOT NULL,
    consent_type       VARCHAR2(24)  NOT NULL,
    status             VARCHAR2(24)  NOT NULL,
    cancel_code        VARCHAR2(2),
    authorization_code VARCHAR2(255),
    code_expires_at    TIMESTAMP(3),
    created_at         TIMESTAMP(3)  NOT NULL,
    updated_at         TIMESTAMP(3)  NOT NULL,
    CONSTRAINT pk_consent PRIMARY KEY (consent_no)
);
