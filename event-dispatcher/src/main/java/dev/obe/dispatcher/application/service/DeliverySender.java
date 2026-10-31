package dev.obe.dispatcher.application.service;

import dev.obe.contract.event.NotificationEvent;
import dev.obe.dispatcher.application.port.out.EventListenerClient;
import dev.obe.dispatcher.domain.model.DeliveryBatch;
import dev.obe.dispatcher.domain.policy.DeliveryPlanner;
import java.util.List;
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
    private final Executor executor;

    public DeliverySender(DeliveryStore store, DeliveryPlanner planner, EventListenerClient listenerClient,
            @Qualifier("deliveryExecutor") Executor executor) {
        this.store = store;
        this.planner = planner;
        this.listenerClient = listenerClient;
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
        try {
            listenerClient.deliver(batch);
        } catch (RuntimeException ex) {
            log.warn("Delivery to YOS {} failed: {}", batch.yosCode(), ex.getMessage());
            store.recordFailure(eventNos, ex.getMessage());
            return;
        }
        store.markDelivered(eventNos);
    }
}
