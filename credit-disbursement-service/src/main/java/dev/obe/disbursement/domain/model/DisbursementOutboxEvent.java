package dev.obe.disbursement.domain.model;

import dev.obe.contract.event.DisbursementEvent;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "disbursement_outbox")
public class DisbursementOutboxEvent {

    private static final String PENDING = "PENDING";
    private static final String PUBLISHED = "PUBLISHED";

    @Id
    @Column(name = "event_no", length = 36)
    private String eventNo;

    @Column(name = "disbursement_no", length = 36, nullable = false, updatable = false)
    private String disbursementNo;

    @Column(name = "partner_code", length = 16, nullable = false, updatable = false)
    private String partnerCode;

    @Column(name = "final_status", length = 16, nullable = false, updatable = false)
    private String finalStatus;

    @Column(name = "failure_reason", length = 256, updatable = false)
    private String failureReason;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @Column(name = "correlation_id", length = 36, updatable = false)
    private String correlationId;

    @Column(name = "status", length = 16, nullable = false)
    private String status;

    @Column(name = "published_at")
    private Instant publishedAt;

    protected DisbursementOutboxEvent() {
    }

    private DisbursementOutboxEvent(Disbursement disbursement, Instant occurredAt) {
        this.eventNo = UUID.randomUUID().toString();
        this.disbursementNo = disbursement.getDisbursementNo();
        this.partnerCode = disbursement.getPartnerCode();
        this.finalStatus = disbursement.getStatus().name();
        this.failureReason = disbursement.getFailureReason();
        this.occurredAt = occurredAt;
        this.correlationId = disbursement.getCorrelationId();
        this.status = PENDING;
    }

    public static DisbursementOutboxEvent finished(Disbursement disbursement, Instant occurredAt) {
        return new DisbursementOutboxEvent(disbursement, occurredAt);
    }

    public void markPublished(Instant now) {
        status = PUBLISHED;
        publishedAt = now;
    }

    public DisbursementEvent toEvent() {
        return new DisbursementEvent(eventNo, disbursementNo, partnerCode, finalStatus, failureReason, occurredAt,
                correlationId);
    }

    public String getFinalStatus() {
        return finalStatus;
    }

    public String getDisbursementNo() {
        return disbursementNo;
    }

    public String getStatus() {
        return status;
    }
}
