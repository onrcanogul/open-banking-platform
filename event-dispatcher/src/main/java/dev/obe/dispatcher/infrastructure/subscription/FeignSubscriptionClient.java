package dev.obe.dispatcher.infrastructure.subscription;

import dev.obe.contract.subscription.SubscriptionView;
import dev.obe.dispatcher.application.port.out.SubscriptionClient;
import feign.FeignException;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
class FeignSubscriptionClient implements SubscriptionClient {

    private final SubscriptionApi api;

    FeignSubscriptionClient(SubscriptionApi api) {
        this.api = api;
    }

    @Override
    public Optional<SubscriptionView> find(String yosCode) {
        try {
            return Optional.of(api.get(yosCode));
        } catch (FeignException.NotFound ex) {
            return Optional.empty();
        }
    }
}
