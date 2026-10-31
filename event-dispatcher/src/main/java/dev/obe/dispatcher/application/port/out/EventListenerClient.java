package dev.obe.dispatcher.application.port.out;

import dev.obe.dispatcher.domain.model.DeliveryBatch;

public interface EventListenerClient {

    /** Returns only after the YOS answered 202, and throws otherwise. */
    void deliver(DeliveryBatch batch);
}
