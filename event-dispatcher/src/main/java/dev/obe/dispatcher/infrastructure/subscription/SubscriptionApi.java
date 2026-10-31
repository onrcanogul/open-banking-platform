package dev.obe.dispatcher.infrastructure.subscription;

import dev.obe.contract.subscription.SubscriptionView;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "subscription-service", url = "${obe.subscription-service-url}")
interface SubscriptionApi {

    @GetMapping("/internal/subscriptions/{yosCode}")
    SubscriptionView get(@PathVariable("yosCode") String yosCode);
}
