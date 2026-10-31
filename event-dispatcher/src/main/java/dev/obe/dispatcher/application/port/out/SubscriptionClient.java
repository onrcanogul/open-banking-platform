package dev.obe.dispatcher.application.port.out;

import dev.obe.contract.subscription.SubscriptionView;
import java.util.Optional;

public interface SubscriptionClient {

    Optional<SubscriptionView> find(String yosCode);
}
