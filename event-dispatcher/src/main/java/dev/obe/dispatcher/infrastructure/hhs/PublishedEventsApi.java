package dev.obe.dispatcher.infrastructure.hhs;

import dev.obe.contract.event.NotificationEvent;
import java.time.Instant;
import java.util.List;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "mock-hhs", url = "${obe.mock-hhs-url}")
interface PublishedEventsApi {

    @GetMapping("/internal/outbox/published")
    List<NotificationEvent> list(@RequestParam("from") Instant from, @RequestParam("to") Instant to,
            @RequestParam("page") int page);
}
