package dev.obe.disbursement.infrastructure.messaging;

import dev.obe.contract.event.DisbursementEvent;
import dev.obe.contract.event.NotificationTopics;
import dev.obe.disbursement.infrastructure.config.PartnerProperties;
import dev.obe.observability.Correlation;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Tells a partner how a disbursement ended. Delivery is at least once, so the partner must use the
 * event number to drop repeats. An error here leaves the offset behind and the event comes again.
 */
@Component
class PartnerNotificationConsumer {

    private static final Logger log = LoggerFactory.getLogger(PartnerNotificationConsumer.class);

    private final PartnerProperties properties;
    private final RestClient restClient;

    PartnerNotificationConsumer(PartnerProperties properties, RestClient.Builder builder,
            @Value("${obe.notification.connect-timeout}") Duration connectTimeout,
            @Value("${obe.notification.read-timeout}") Duration readTimeout) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeout);
        requestFactory.setReadTimeout(readTimeout);
        this.properties = properties;
        this.restClient = builder.requestFactory(requestFactory).build();
    }

    @KafkaListener(topics = NotificationTopics.DISBURSEMENTS)
    void consume(DisbursementEvent event) {
        PartnerProperties.Partner partner = properties.partners().get(event.partnerCode());
        if (partner == null || partner.webhookUrl() == null) {
            log.warn("Partner {} has no webhook, skipped event {}", event.partnerCode(), event.eventNo());
            return;
        }
        Correlation.run(event.correlationId(), () -> {
            restClient.post()
                    .uri(partner.webhookUrl())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(PartnerNotification.from(event))
                    .retrieve()
                    .toBodilessEntity();
            log.info("Told partner {} that {} is {}", event.partnerCode(), event.disbursementNo(), event.status());
        });
    }
}
