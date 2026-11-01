package dev.obe.disbursement.domain.model;

import dev.obe.disbursement.domain.exception.InvalidTransitionException;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.type.NumericBooleanConverter;

@Entity
@Table(name = "disbursement")
public class Disbursement {

    public static final String UNIQUE_KEY_CONSTRAINT = "UQ_DISBURSEMENT_KEY";

    private static final int MAX_REASON_LENGTH = 256;

    @Id
    @Column(name = "disbursement_no", length = 36)
    private String disbursementNo;

    @Column(name = "partner_code", length = 16, nullable = false, updatable = false)
    private String partnerCode;

    @Column(name = "idempotency_key", length = 64, nullable = false, updatable = false)
    private String idempotencyKey;

    @Column(name = "request_hash", length = 64, nullable = false, updatable = false)
    private String requestHash;

    @Column(name = "customer_no", length = 32, nullable = false, updatable = false)
    private String customerNo;

    @Column(name = "account_no", length = 34, nullable = false, updatable = false)
    private String accountNo;

    @Column(name = "amount", precision = 18, scale = 2, nullable = false, updatable = false)
    private BigDecimal amount;

    @Column(name = "currency", length = 3, nullable = false, updatable = false)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 16, nullable = false)
    private DisbursementStatus status;

    @Column(name = "failure_reason", length = MAX_REASON_LENGTH)
    private String failureReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "next_step_at", nullable = false)
    private Instant nextStepAt;

    @Column(name = "correlation_id", length = 36, updatable = false)
    private String correlationId;

    /** Stored as a number, because Oracle versions before 23 have no boolean column type. */
    @Convert(converter = NumericBooleanConverter.class)
    @Column(name = "accounting_posted", nullable = false)
    private boolean accountingPosted;

    @Enumerated(EnumType.STRING)
    @Column(name = "uncertain_from", length = 16)
    private DisbursementStatus uncertainFrom;

    /** Makes the slower of two concurrent updates fail instead of silently overwriting the other. */
    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected Disbursement() {
    }

    private Disbursement(DisbursementDetails details, String idempotencyKey, String requestHash, String correlationId,
            Instant now) {
        this.disbursementNo = UUID.randomUUID().toString();
        this.partnerCode = details.partnerCode();
        this.idempotencyKey = idempotencyKey;
        this.requestHash = requestHash;
        this.customerNo = details.customerNo();
        this.accountNo = details.accountNo();
        this.amount = details.amount();
        this.currency = details.currency();
        this.status = DisbursementStatus.ALINDI;
        this.createdAt = now;
        this.updatedAt = now;
        this.nextStepAt = now;
        this.correlationId = correlationId;
    }

    public static Disbursement create(DisbursementDetails details, String idempotencyKey, String requestHash,
            String correlationId, Instant now) {
        return new Disbursement(details, idempotencyKey, requestHash, correlationId, now);
    }

    /** Hides the row from other workers until the time given, in case this worker dies mid-step. */
    public void claimUntil(Instant leaseEnd) {
        nextStepAt = leaseEnd;
    }

    public void moveTo(DisbursementStatus next, Instant now) {
        if (!status.canMoveTo(next)) {
            throw new InvalidTransitionException("Disbursement " + disbursementNo + " cannot move from " + status
                    + " to " + next);
        }
        if (next == DisbursementStatus.MUHASEBELESTI) {
            accountingPosted = true;
        }
        status = next;
        updatedAt = now;
    }

    public void fail(String reason, Instant now) {
        moveTo(DisbursementStatus.BASARISIZ, now);
        failureReason = shorten(reason);
    }

    public void startCompensation(String reason, Instant now) {
        moveTo(DisbursementStatus.TELAFI_EDILIYOR, now);
        failureReason = shorten(reason);
    }

    public void finishCompensation(Instant now) {
        moveTo(DisbursementStatus.BASARISIZ, now);
    }

    // A long message must never stop a state change, since compensation depends on saving this
    private String shorten(String reason) {
        return reason == null || reason.length() <= MAX_REASON_LENGTH ? reason : reason.substring(0, MAX_REASON_LENGTH);
    }

    /** Remembers the step that was running, because that is what the core must be asked about. */
    public void markUncertain(Instant now) {
        DisbursementStatus running = status;
        moveTo(DisbursementStatus.BELIRSIZ, now);
        uncertainFrom = running;
    }

    public String getDisbursementNo() {
        return disbursementNo;
    }

    public String getPartnerCode() {
        return partnerCode;
    }

    public String getRequestHash() {
        return requestHash;
    }

    public String getCustomerNo() {
        return customerNo;
    }

    public String getAccountNo() {
        return accountNo;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public DisbursementStatus getStatus() {
        return status;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public boolean isAccountingPosted() {
        return accountingPosted;
    }

    public DisbursementStatus getUncertainFrom() {
        return uncertainFrom;
    }
}
