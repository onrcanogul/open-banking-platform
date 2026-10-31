package dev.obe.mockyos.api.controller;

import dev.obe.contract.listener.EventNotificationRequest;
import dev.obe.mockyos.application.ReceivedEventStore;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
class EventListenerController {

    private static final Logger log = LoggerFactory.getLogger(EventListenerController.class);

    private final ReceivedEventStore store;

    EventListenerController(ReceivedEventStore store) {
        this.store = store;
    }

    @PostMapping("/olay-dinleme")
    ResponseEntity<Void> receive(@RequestBody EventNotificationRequest request) {
        if (request.olaylar().size() > EventNotificationRequest.MAX_EVENTS) {
            return ResponseEntity.badRequest().build();
        }
        store.add(request);
        request.olaylar().forEach(event -> log.info("Received {} {} {}", event.olayTipi(), event.kaynakTipi(),
                event.kaynakNo()));
        return ResponseEntity.status(HttpStatus.ACCEPTED).build();
    }

    @GetMapping("/internal/received")
    List<EventNotificationRequest> received() {
        return store.all();
    }
}
