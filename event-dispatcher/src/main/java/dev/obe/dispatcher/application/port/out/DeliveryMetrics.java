package dev.obe.dispatcher.application.port.out;

import dev.obe.contract.event.NotificationEvent;

public interface DeliveryMetrics {

    void delivered(NotificationEvent event);

    void failed(NotificationEvent event);

    void undelivered(NotificationEvent event);
}
