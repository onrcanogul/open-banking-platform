ALTER TABLE disbursement ADD (
    -- Says whether compensation has a ledger entry to reverse
    accounting_posted NUMBER(1) DEFAULT 0 NOT NULL,
    -- The step whose call has an unknown outcome, so a check knows what to ask about
    uncertain_from    VARCHAR2(16)
);
