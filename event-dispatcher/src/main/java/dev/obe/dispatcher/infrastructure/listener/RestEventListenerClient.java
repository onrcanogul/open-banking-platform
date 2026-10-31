package dev.obe.dispatcher.infrastructure.listener;

import dev.obe.contract.event.NotificationEvent;
import dev.obe.contract.listener.EventNotification;
import dev.obe.dispatcher.application.port.out.EventListenerClient;
import dev.obe.dispatcher.domain.model.DeliveryBatch;
import dev.obe.dispatcher.infrastructure.config.DispatcherProperties;
import java.time.Duration;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
class RestEventListenerClient implements EventListenerClient {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(5);

    private final RestClient restClient;
    private final DispatcherProperties properties;

    RestEventListenerClient(RestClient.Builder builder, DispatcherProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(CONNECT_TIMEOUT);
        requestFactory.setReadTimeout(READ_TIMEOUT);
        this.restClient = builder.requestFactory(requestFactory).build();
        this.properties = properties;
    }

    @Override
    public void deliver(DeliveryBatch batch) {
        String baseUrl = properties.listenerUrls().get(batch.yosCode());
        if (baseUrl == null) {
            throw new IllegalStateException("No listener address for YOS " + batch.yosCode());
        }
        ResponseEntity<Void> response = restClient.post()
                .uri(baseUrl + "/olay-dinleme")
                .contentType(MediaType.APPLICATION_JSON)
                .body(toRequest(batch))
                .retrieve()
                .toBodilessEntity();
        // The standard counts anything other than 202 as not delivered, even another 2xx
        if (response.getStatusCode() != HttpStatus.ACCEPTED) {
            throw new IllegalStateException("YOS " + batch.yosCode() + " answered " + response.getStatusCode());
        }
    }

    private EventNotification toRequest(DeliveryBatch batch) {
        List<EventNotification.Event> events = batch.events().stream().map(this::toEvent).toList();
        return new EventNotification(
                new EventNotification.Participant(properties.hhsCode(), batch.yosCode()), events);
    }

    private EventNotification.Event toEvent(NotificationEvent event) {
        return new EventNotification.Event(event.eventNo(), event.occurredAt(), event.eventType(),
                event.resourceType(), event.resourceNo());
    }
}
