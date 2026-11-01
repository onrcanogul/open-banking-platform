-- The staff list shows the newest first, which would otherwise sort the whole table on every page
CREATE INDEX ix_disbursement_created ON disbursement (created_at);
