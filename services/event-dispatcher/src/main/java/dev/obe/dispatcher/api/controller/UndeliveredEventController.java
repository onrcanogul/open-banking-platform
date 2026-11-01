package dev.obe.dispatcher.api.controller;

import dev.obe.contract.event.NotificationEvent;
import dev.obe.dispatcher.application.service.DeliveryStore;
import java.time.Instant;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Called by subscription-service, which checks that the subscription belongs to the caller. */
@RestController
@RequestMapping("/internal/undelivered-events")
class UndeliveredEventController {

    private final DeliveryStore store;

    UndeliveredEventController(DeliveryStore store) {
        this.store = store;
    }

    @GetMapping
    List<NotificationEvent> list(@RequestParam String yosCode, @RequestParam Instant from, @RequestParam Instant to,
            @RequestParam int page) {
        return store.findUndelivered(yosCode, from, to, page);
    }
}
