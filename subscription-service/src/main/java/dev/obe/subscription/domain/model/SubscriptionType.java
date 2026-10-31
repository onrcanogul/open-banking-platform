package dev.obe.subscription.domain.model;

import dev.obe.contract.event.EventType;
import dev.obe.contract.event.ResourceType;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

@Embeddable
public record SubscriptionType(
        @Enumerated(EnumType.STRING) @Column(name = "event_type", nullable = false) EventType eventType,
        @Enumerated(EnumType.STRING) @Column(name = "resource_type", nullable = false) ResourceType resourceType) {
}
