package dev.obe.dispatcher.infrastructure.messaging;

import dev.obe.dispatcher.application.service.DeliverySender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "obe.delivery.scheduler-enabled", havingValue = "true")
class DeliveryScheduler {

    private static final Logger log = LoggerFactory.getLogger(DeliveryScheduler.class);

    private final DeliverySender sender;

    DeliveryScheduler(DeliverySender sender) {
        this.sender = sender;
    }

    @Scheduled(fixedDelayString = "${obe.delivery.poll-interval}")
    void run() {
        try {
            while (sender.sendDue() > 0) {
                log.debug("Sent a round of due deliveries");
            }
        } catch (RuntimeException ex) {
            // Claimed rows come back when their lease ends, so nothing is lost
            log.error("Delivery round failed", ex);
        }
    }
}
