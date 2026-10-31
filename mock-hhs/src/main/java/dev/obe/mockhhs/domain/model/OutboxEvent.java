package dev.obe.mockhhs.domain.model;

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

    private static final String RESOURCE_UPDATED = "KAYNAK_GUNCELLENDI";
    private static final String PAYMENT_ORDER = "ODEME_EMRI";

    @Id
    @Column(name = "event_no", length = 36)
    private String eventNo;

    @Column(name = "yos_code", length = 4, nullable = false, updatable = false)
    private String yosCode;

    @Column(name = "event_type", length = 36, nullable = false, updatable = false)
    private String eventType;

    @Column(name = "resource_type", length = 36, nullable = false, updatable = false)
    private String resourceType;

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

    private OutboxEvent(String yosCode, String eventType, String resourceType, String resourceNo, Instant occurredAt) {
        this.eventNo = UUID.randomUUID().toString();
        this.yosCode = yosCode;
        this.eventType = eventType;
        this.resourceType = resourceType;
        this.resourceNo = resourceNo;
        this.occurredAt = occurredAt;
        this.status = OutboxStatus.PENDING;
    }

    public static OutboxEvent paymentOrderUpdated(PaymentOrder order, Instant occurredAt) {
        return new OutboxEvent(order.getYosCode(), RESOURCE_UPDATED, PAYMENT_ORDER, order.getOrderNo(), occurredAt);
    }

    public String getEventNo() {
        return eventNo;
    }

    public String getYosCode() {
        return yosCode;
    }

    public String getEventType() {
        return eventType;
    }

    public String getResourceType() {
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
