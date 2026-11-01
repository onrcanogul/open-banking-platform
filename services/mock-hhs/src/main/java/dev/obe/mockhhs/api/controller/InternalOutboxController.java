package dev.obe.mockhhs.api.controller;

import dev.obe.contract.event.NotificationEvent;
import dev.obe.mockhhs.application.service.OutboxMaintenanceService;
import java.time.Instant;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Lets the dispatcher reconcile deliveries without reaching into this service's schema. */
@RestController
@RequestMapping("/internal/outbox")
class InternalOutboxController {

    private final OutboxMaintenanceService service;

    InternalOutboxController(OutboxMaintenanceService service) {
        this.service = service;
    }

    @GetMapping("/published")
    List<NotificationEvent> published(@RequestParam Instant from, @RequestParam Instant to,
            @RequestParam int page) {
        return service.findPublished(from, to, page);
    }
}
