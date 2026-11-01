package dev.obe.disbursement.application.port.out;

import dev.obe.contract.event.DisbursementEvent;
import java.util.List;

public interface DisbursementEventPublisher {

    /** Returns only after the broker accepted every event, and throws otherwise. */
    void publish(List<DisbursementEvent> events);
}
