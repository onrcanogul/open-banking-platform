package dev.obe.dispatcher.application.port.out;

import dev.obe.contract.event.NotificationEvent;
import java.time.Instant;
import java.util.List;

public interface PublishedEventsClient {

    /** Returns one page of events the HHS published in the range. Pages start from 1, empty at the end. */
    List<NotificationEvent> find(Instant from, Instant to, int page);
}
