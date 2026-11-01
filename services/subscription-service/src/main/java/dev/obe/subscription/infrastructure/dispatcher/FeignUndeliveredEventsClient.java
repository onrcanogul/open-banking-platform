package dev.obe.subscription.infrastructure.dispatcher;

import dev.obe.contract.event.NotificationEvent;
import dev.obe.subscription.application.port.out.UndeliveredEventsClient;
import dev.obe.subscription.domain.policy.UndeliveredWindow;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
class FeignUndeliveredEventsClient implements UndeliveredEventsClient {

    private final UndeliveredEventsApi api;

    FeignUndeliveredEventsClient(UndeliveredEventsApi api) {
        this.api = api;
    }

    @Override
    public List<NotificationEvent> find(String yosCode, UndeliveredWindow window, int page) {
        return api.list(yosCode, window.from(), window.to(), page);
    }
}
