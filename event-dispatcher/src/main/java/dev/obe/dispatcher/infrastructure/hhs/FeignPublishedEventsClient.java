package dev.obe.dispatcher.infrastructure.hhs;

import dev.obe.contract.event.NotificationEvent;
import dev.obe.dispatcher.application.port.out.PublishedEventsClient;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
class FeignPublishedEventsClient implements PublishedEventsClient {

    private final PublishedEventsApi api;

    FeignPublishedEventsClient(PublishedEventsApi api) {
        this.api = api;
    }

    @Override
    public List<NotificationEvent> find(Instant from, Instant to, int page) {
        return api.list(from, to, page);
    }
}
