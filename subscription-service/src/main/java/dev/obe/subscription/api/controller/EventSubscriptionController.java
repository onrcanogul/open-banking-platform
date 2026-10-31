package dev.obe.subscription.api.controller;

import dev.obe.subscription.api.dto.request.CreateSubscriptionRequest;
import dev.obe.subscription.api.dto.request.UpdateSubscriptionRequest;
import dev.obe.subscription.api.dto.response.SubscriptionResponse;
import dev.obe.subscription.api.mapper.SubscriptionApiMapper;
import dev.obe.subscription.application.command.Participant;
import dev.obe.subscription.application.service.EventSubscriptionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/olay-abonelik")
class EventSubscriptionController {

    private static final String ASPSP_CODE = "X-ASPSP-Code";
    private static final String TPP_CODE = "X-TPP-Code";

    private final EventSubscriptionService service;
    private final SubscriptionApiMapper mapper;

    EventSubscriptionController(EventSubscriptionService service, SubscriptionApiMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    SubscriptionResponse create(@RequestHeader(ASPSP_CODE) String aspspCode, @RequestHeader(TPP_CODE) String tppCode,
            @Valid @RequestBody CreateSubscriptionRequest request) {
        Participant caller = new Participant(aspspCode, tppCode);
        return mapper.toResponse(service.create(mapper.toCommand(caller, request)));
    }

    @GetMapping
    SubscriptionResponse get(@RequestHeader(ASPSP_CODE) String aspspCode, @RequestHeader(TPP_CODE) String tppCode) {
        return mapper.toResponse(service.get(new Participant(aspspCode, tppCode)));
    }

    @PutMapping("/{subscriptionNo}")
    SubscriptionResponse update(@RequestHeader(ASPSP_CODE) String aspspCode, @RequestHeader(TPP_CODE) String tppCode,
            @PathVariable String subscriptionNo, @Valid @RequestBody UpdateSubscriptionRequest request) {
        Participant caller = new Participant(aspspCode, tppCode);
        return mapper.toResponse(service.update(mapper.toCommand(caller, subscriptionNo, request)));
    }

    @DeleteMapping("/{subscriptionNo}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@RequestHeader(ASPSP_CODE) String aspspCode, @RequestHeader(TPP_CODE) String tppCode,
            @PathVariable String subscriptionNo) {
        service.delete(new Participant(aspspCode, tppCode), subscriptionNo);
    }
}
