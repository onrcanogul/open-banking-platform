package dev.obe.subscription.api.controller;

import dev.obe.contract.subscription.SubscriptionView;
import dev.obe.subscription.api.mapper.SubscriptionApiMapper;
import dev.obe.subscription.application.service.EventSubscriptionService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Called by other services of this bank, not by a YOS. */
@RestController
@RequestMapping("/internal/subscriptions")
class InternalSubscriptionController {

    private final EventSubscriptionService service;
    private final SubscriptionApiMapper mapper;

    InternalSubscriptionController(EventSubscriptionService service, SubscriptionApiMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    @GetMapping("/{yosCode}")
    SubscriptionView get(@PathVariable String yosCode) {
        return mapper.toView(service.getByYosCode(yosCode));
    }
}
