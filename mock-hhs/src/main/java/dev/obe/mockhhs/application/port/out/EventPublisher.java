package dev.obe.mockhhs.application.port.out;

import dev.obe.contract.event.NotificationEvent;
import java.util.List;

public interface EventPublisher {

    /** Returns only after the broker accepted every event, and throws otherwise. */
    void publish(List<NotificationEvent> events);
}
