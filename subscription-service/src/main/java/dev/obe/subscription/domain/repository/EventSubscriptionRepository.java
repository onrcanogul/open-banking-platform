package dev.obe.subscription.domain.repository;

import dev.obe.subscription.domain.model.EventSubscription;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventSubscriptionRepository extends JpaRepository<EventSubscription, String> {

    Optional<EventSubscription> findByYosCode(String yosCode);
}
