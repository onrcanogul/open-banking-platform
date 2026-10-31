package dev.obe.mockyos.application;

import dev.obe.contract.listener.EventNotificationRequest;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.stereotype.Component;

/** Keeps received calls in memory so they can be inspected by hand and in tests. */
@Component
public class ReceivedEventStore {

    private final List<EventNotificationRequest> received = new CopyOnWriteArrayList<>();

    public void add(EventNotificationRequest request) {
        received.add(request);
    }

    public List<EventNotificationRequest> all() {
        return List.copyOf(received);
    }

    public void clear() {
        received.clear();
    }
}
