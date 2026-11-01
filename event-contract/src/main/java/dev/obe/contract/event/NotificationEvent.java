package dev.obe.contract.event;

import java.time.Instant;

/**
 * An event that happened at the HHS and may have to reach a YOS. Carries no resource data, because
 * the standard has the YOS fetch current data from the resource's own API.
 *
 * @param eventNo       unique per event, so consumers can drop duplicates
 * @param correlationId the request that caused the event, null on messages older than this field
 */
public record NotificationEvent(
        String eventNo,
        String yosCode,
        EventType eventType,
        ResourceType resourceType,
        String resourceNo,
        Instant occurredAt,
        String correlationId) {

    public NotificationEvent(String eventNo, String yosCode, EventType eventType, ResourceType resourceType,
            String resourceNo, Instant occurredAt) {
        this(eventNo, yosCode, eventType, resourceType, resourceNo, occurredAt, null);
    }
}
