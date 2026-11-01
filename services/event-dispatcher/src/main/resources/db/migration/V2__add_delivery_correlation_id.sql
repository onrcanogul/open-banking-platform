-- Nullable, because deliveries written before this column have no id
ALTER TABLE event_delivery ADD correlation_id VARCHAR2(36);
