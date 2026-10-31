package dev.obe.contract.subscription;

import dev.obe.contract.event.EventType;
import dev.obe.contract.event.ResourceType;

public record SubscribedType(EventType eventType, ResourceType resourceType) {
}
