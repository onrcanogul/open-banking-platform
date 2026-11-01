package dev.obe.mockyos.application;

import dev.obe.contract.listener.EventNotification;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.stereotype.Component;

/** Keeps received calls in memory so they can be inspected by hand and in tests. */
@Component
public class ReceivedEventStore {

    private final List<EventNotification> received = new CopyOnWriteArrayList<>();

    public void add(EventNotification request) {
        received.add(request);
    }

    public List<EventNotification> all() {
        return List.copyOf(received);
    }

    public void clear() {
        received.clear();
    }
}
