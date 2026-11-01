package dev.obe.dispatcher.infrastructure.batch;

import dev.obe.contract.event.NotificationEvent;
import dev.obe.dispatcher.application.port.out.PublishedEventsClient;
import java.time.Instant;
import java.util.Iterator;
import java.util.List;
import org.springframework.batch.infrastructure.item.ItemReader;

/** Serves published events one at a time, fetching the next page when the current one runs out. */
class PublishedEventReader implements ItemReader<NotificationEvent> {

    private final PublishedEventsClient client;
    private final Instant from;
    private final Instant to;
    private Iterator<NotificationEvent> currentPage = List.<NotificationEvent>of().iterator();
    private int nextPage = 1;

    PublishedEventReader(PublishedEventsClient client, Instant from, Instant to) {
        this.client = client;
        this.from = from;
        this.to = to;
    }

    /** Returning null is how a reader tells Spring Batch that the data has ended. */
    @Override
    public NotificationEvent read() {
        if (!currentPage.hasNext()) {
            currentPage = client.find(from, to, nextPage).iterator();
            nextPage++;
        }
        return currentPage.hasNext() ? currentPage.next() : null;
    }
}
