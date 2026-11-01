package dev.obe.dispatcher.domain.model;

import dev.obe.contract.event.NotificationEvent;
import java.util.List;

/** Events that go to one YOS in a single call. */
public record DeliveryBatch(String yosCode, List<NotificationEvent> events) {
}
