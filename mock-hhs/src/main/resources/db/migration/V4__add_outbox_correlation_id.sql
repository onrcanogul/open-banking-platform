-- Nullable, because events written before this column have no id
ALTER TABLE outbox_event ADD correlation_id VARCHAR2(36);
