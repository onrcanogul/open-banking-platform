package dev.obe.mockhhs.domain.model;

import dev.obe.contract.event.EventType;
import dev.obe.contract.event.NotificationEvent;
import dev.obe.contract.event.ResourceType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "outbox_event")
public class OutboxEvent {

    @Id
    @Column(name = "event_no", length = 36)
    private String eventNo;

    @Column(name = "yos_code", length = 4, nullable = false, updatable = false)
    private String yosCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", length = 36, nullable = false, updatable = false)
    private EventType eventType;

    @Enumerated(EnumType.STRING)
    @Column(name = "resource_type", length = 36, nullable = false, updatable = false)
    private ResourceType resourceType;

    @Column(name = "resource_no", length = 128, nullable = false, updatable = false)
    private String resourceNo;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 16, nullable = false)
    private OutboxStatus status;

    @Column(name = "published_at")
    private Instant publishedAt;

    protected OutboxEvent() {
    }

    private OutboxEvent(String yosCode, EventType eventType, ResourceType resourceType, String resourceNo,
            Instant occurredAt) {
        this.eventNo = UUID.randomUUID().toString();
        this.yosCode = yosCode;
        this.eventType = eventType;
        this.resourceType = resourceType;
        this.resourceNo = resourceNo;
        this.occurredAt = occurredAt;
        this.status = OutboxStatus.PENDING;
    }

    public static OutboxEvent paymentOrderUpdated(PaymentOrder order, Instant occurredAt) {
        return new OutboxEvent(order.getYosCode(), EventType.KAYNAK_GUNCELLENDI, ResourceType.ODEME_EMRI,
                order.getOrderNo(), occurredAt);
    }

    public static OutboxEvent consentEvent(Consent consent, EventType eventType, Instant occurredAt) {
        return new OutboxEvent(consent.getYosCode(), eventType, consent.getType().resourceType(),
                consent.getConsentNo(), occurredAt);
    }

    public void markPublished(Instant now) {
        status = OutboxStatus.PUBLISHED;
        publishedAt = now;
    }

    public NotificationEvent toNotificationEvent() {
        return new NotificationEvent(eventNo, yosCode, eventType, resourceType, resourceNo, occurredAt);
    }

    public String getEventNo() {
        return eventNo;
    }

    public String getYosCode() {
        return yosCode;
    }

    public EventType getEventType() {
        return eventType;
    }

    public ResourceType getResourceType() {
        return resourceType;
    }

    public String getResourceNo() {
        return resourceNo;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public OutboxStatus getStatus() {
        return status;
    }
}
