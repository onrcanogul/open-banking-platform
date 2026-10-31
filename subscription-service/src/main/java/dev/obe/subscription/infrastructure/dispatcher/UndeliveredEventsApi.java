package dev.obe.subscription.infrastructure.dispatcher;

import dev.obe.contract.event.NotificationEvent;
import java.time.Instant;
import java.util.List;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "event-dispatcher", url = "${obe.event-dispatcher-url}")
interface UndeliveredEventsApi {

    @GetMapping("/internal/undelivered-events")
    List<NotificationEvent> list(@RequestParam("yosCode") String yosCode, @RequestParam("from") Instant from,
            @RequestParam("to") Instant to, @RequestParam("page") int page);
}
