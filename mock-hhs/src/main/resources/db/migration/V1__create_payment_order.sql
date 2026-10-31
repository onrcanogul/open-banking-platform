CREATE TABLE payment_order (
    order_no   VARCHAR2(36)  NOT NULL,
    yos_code   VARCHAR2(4)   NOT NULL,
    amount     NUMBER(18, 2) NOT NULL,
    status     VARCHAR2(16)  NOT NULL,
    created_at TIMESTAMP(3)  NOT NULL,
    updated_at TIMESTAMP(3)  NOT NULL,
    CONSTRAINT pk_payment_order PRIMARY KEY (order_no)
);
