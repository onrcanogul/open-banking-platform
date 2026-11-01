package dev.obe.subscription.api.controller;

import dev.obe.contract.listener.EventNotification;
import dev.obe.subscription.api.RateLimitHeaders;
import dev.obe.subscription.api.dto.request.CreateSubscriptionRequest;
import dev.obe.subscription.api.dto.request.UpdateSubscriptionRequest;
import dev.obe.subscription.api.dto.response.SubscriptionResponse;
import dev.obe.subscription.api.mapper.SubscriptionApiMapper;
import dev.obe.subscription.application.command.Participant;
import dev.obe.subscription.application.service.EventSubscriptionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.Instant;
import java.time.OffsetDateTime;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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

    @GetMapping("/{subscriptionNo}/iletilemeyen-olaylar")
    ResponseEntity<EventNotification> undelivered(@RequestHeader(ASPSP_CODE) String aspspCode,
            @RequestHeader(TPP_CODE) String tppCode, @PathVariable String subscriptionNo,
            @RequestParam(defaultValue = "1") @Min(1) @Max(999) int syfNo,
            @RequestParam(required = false) OffsetDateTime olyZmnBslTrh,
            @RequestParam(required = false) OffsetDateTime olyZmnBtsTrh) {
        Participant caller = new Participant(aspspCode, tppCode);
        EventNotification notification = mapper.toNotification(caller, service.listUndelivered(caller, subscriptionNo,
                toInstant(olyZmnBslTrh), toInstant(olyZmnBtsTrh), syfNo));
        return ResponseEntity.ok()
                .header(RateLimitHeaders.LIMIT, RateLimitHeaders.UNDELIVERED_QUERY_LIMIT)
                .header(RateLimitHeaders.REMAINING, RateLimitHeaders.UNDELIVERED_QUERY_REMAINING)
                .body(notification);
    }

    @DeleteMapping("/{subscriptionNo}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@RequestHeader(ASPSP_CODE) String aspspCode, @RequestHeader(TPP_CODE) String tppCode,
            @PathVariable String subscriptionNo) {
        service.delete(new Participant(aspspCode, tppCode), subscriptionNo);
    }

    private Instant toInstant(OffsetDateTime time) {
        return time == null ? null : time.toInstant();
    }
}
