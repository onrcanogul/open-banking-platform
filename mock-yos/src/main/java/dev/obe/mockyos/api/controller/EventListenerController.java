package dev.obe.mockyos.api.controller;

import dev.obe.contract.listener.EventNotification;
import dev.obe.mockyos.application.OutageSwitch;
import dev.obe.mockyos.application.ReceivedEventStore;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
class EventListenerController {

    private static final Logger log = LoggerFactory.getLogger(EventListenerController.class);

    private final ReceivedEventStore store;
    private final OutageSwitch outage;

    EventListenerController(ReceivedEventStore store, OutageSwitch outage) {
        this.store = store;
        this.outage = outage;
    }

    @PostMapping("/internal/outage")
    void setOutage(@RequestParam boolean down) {
        outage.set(down);
    }

    @PostMapping("/olay-dinleme")
    ResponseEntity<Void> receive(@RequestBody EventNotification request) {
        if (outage.isDown()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
        if (request.olaylar().size() > EventNotification.MAX_EVENTS) {
            return ResponseEntity.badRequest().build();
        }
        store.add(request);
        request.olaylar().forEach(event -> log.info("Received {} {} {}", event.olayTipi(), event.kaynakTipi(),
                event.kaynakNo()));
        return ResponseEntity.status(HttpStatus.ACCEPTED).build();
    }

    @GetMapping("/internal/received")
    List<EventNotification> received() {
        return store.all();
    }
}
