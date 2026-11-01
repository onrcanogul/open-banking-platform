package dev.obe.subscription.application.port.out;

import dev.obe.contract.event.NotificationEvent;
import dev.obe.subscription.domain.policy.UndeliveredWindow;
import java.util.List;

public interface UndeliveredEventsClient {

    /** Returns one page of events, oldest first. Pages start from 1. */
    List<NotificationEvent> find(String yosCode, UndeliveredWindow window, int page);
}
