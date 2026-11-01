package dev.obe.dispatcher.domain.model;

import dev.obe.contract.event.EventType;
import dev.obe.contract.event.NotificationEvent;
import dev.obe.contract.event.ResourceType;
import dev.obe.dispatcher.domain.policy.RetryPolicy;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "event_delivery")
public class EventDelivery {

    private static final int MAX_ERROR_LENGTH = 256;

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
    private DeliveryStatus status;

    @Column(name = "failed_attempts", nullable = false)
    private int failedAttempts;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "last_error", length = MAX_ERROR_LENGTH)
    private String lastError;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "correlation_id", length = 36, updatable = false)
    private String correlationId;

    protected EventDelivery() {
    }

    private EventDelivery(NotificationEvent event, Instant now) {
        this.eventNo = event.eventNo();
        this.yosCode = event.yosCode();
        this.eventType = event.eventType();
        this.resourceType = event.resourceType();
        this.resourceNo = event.resourceNo();
        this.occurredAt = event.occurredAt();
        this.status = DeliveryStatus.PENDING;
        this.nextAttemptAt = now;
        this.correlationId = event.correlationId();
    }

    public static EventDelivery create(NotificationEvent event, Instant now) {
        return new EventDelivery(event, now);
    }

    /** Hides the row from other senders until the time given, in case this sender dies mid-call. */
    public void claimUntil(Instant leaseEnd) {
        nextAttemptAt = leaseEnd;
    }

    public void markDelivered(Instant now) {
        status = DeliveryStatus.DELIVERED;
        finishedAt = now;
    }

    public void recordFailure(String error, Instant now) {
        failedAttempts++;
        lastError = error == null || error.length() <= MAX_ERROR_LENGTH ? error : error.substring(0, MAX_ERROR_LENGTH);
        RetryPolicy policy = RetryPolicy.of(eventType, resourceType);
        if (policy.allowsRetryAfter(failedAttempts)) {
            nextAttemptAt = now.plus(policy.delayAfter(failedAttempts));
        } else {
            status = DeliveryStatus.UNDELIVERED;
            finishedAt = now;
        }
    }

    public NotificationEvent toNotificationEvent() {
        return new NotificationEvent(eventNo, yosCode, eventType, resourceType, resourceNo, occurredAt, correlationId);
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

    public DeliveryStatus getStatus() {
        return status;
    }

    public int getFailedAttempts() {
        return failedAttempts;
    }

    public Instant getNextAttemptAt() {
        return nextAttemptAt;
    }

    public String getCorrelationId() {
        return correlationId;
    }
}
