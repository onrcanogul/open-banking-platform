package dev.obe.contract.event;

import java.time.Instant;

/**
 * An event that happened at the HHS and may have to reach a YOS. Carries no resource data, because
 * the standard has the YOS fetch current data from the resource's own API.
 *
 * @param eventNo unique per event, so consumers can drop duplicates
 */
public record NotificationEvent(
        String eventNo,
        String yosCode,
        EventType eventType,
        ResourceType resourceType,
        String resourceNo,
        Instant occurredAt) {
}
