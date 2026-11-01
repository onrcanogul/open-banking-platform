package dev.obe.dispatcher.application.service;

import dev.obe.contract.event.NotificationEvent;
import dev.obe.dispatcher.application.port.out.DeliveryMetrics;
import dev.obe.dispatcher.application.port.out.EventListenerClient;
import dev.obe.dispatcher.domain.model.DeliveryBatch;
import dev.obe.dispatcher.domain.policy.DeliveryPlanner;
import dev.obe.observability.Correlation;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/** Sends due deliveries. Delivery is at least once: a crash after a 202 repeats that call. */
@Service
public class DeliverySender {

    private static final Logger log = LoggerFactory.getLogger(DeliverySender.class);

    private final DeliveryStore store;
    private final DeliveryPlanner planner;
    private final EventListenerClient listenerClient;
    private final DeliveryMetrics metrics;
    private final Executor executor;

    public DeliverySender(DeliveryStore store, DeliveryPlanner planner, EventListenerClient listenerClient,
            DeliveryMetrics metrics, @Qualifier("deliveryExecutor") Executor executor) {
        this.store = store;
        this.planner = planner;
        this.listenerClient = listenerClient;
        this.metrics = metrics;
        this.executor = executor;
    }

    /** Returns how many events were attempted. */
    public int sendDue() {
        List<NotificationEvent> events = store.claimDue();
        // Calls run side by side so that a slow YOS only delays itself
        CompletableFuture<?>[] calls = planner.batches(events).stream()
                .map(batch -> CompletableFuture.runAsync(() -> send(batch), executor))
                .toArray(CompletableFuture[]::new);
        CompletableFuture.allOf(calls).join();
        return events.size();
    }

    private void send(DeliveryBatch batch) {
        List<String> eventNos = batch.events().stream().map(NotificationEvent::eventNo).toList();
        // Our correlation ids stay inside the bank, so the YOS gets an id for this call instead
        String requestId = Correlation.newId();
        try {
            listenerClient.deliver(batch, requestId);
        } catch (RuntimeException ex) {
            Set<String> exhausted = store.recordFailure(eventNos, ex.getMessage());
            batch.events().forEach(event -> recordFailure(event, exhausted, requestId, ex));
            return;
        }
        store.markDelivered(eventNos);
        batch.events().forEach(event -> recordDelivery(event, requestId));
    }

    private void recordDelivery(NotificationEvent event, String requestId) {
        metrics.delivered(event);
        Correlation.run(event.correlationId(),
                () -> log.info("Delivered event {} to YOS {} in call {}", event.eventNo(), event.yosCode(), requestId));
    }

    private void recordFailure(NotificationEvent event, Set<String> exhausted, String requestId, Exception cause) {
        metrics.failed(event);
        boolean undelivered = exhausted.contains(event.eventNo());
        if (undelivered) {
            metrics.undelivered(event);
        }
        Correlation.run(event.correlationId(),
                () -> log.warn("Delivery of event {} to YOS {} failed in call {}, undelivered={}: {}", event.eventNo(),
                        event.yosCode(), requestId, undelivered, cause.getMessage()));
    }
}
