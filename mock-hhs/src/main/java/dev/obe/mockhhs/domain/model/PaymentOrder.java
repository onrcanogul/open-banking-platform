package dev.obe.mockhhs.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payment_order")
public class PaymentOrder {

    @Id
    @Column(name = "order_no", length = 36)
    private String orderNo;

    @Column(name = "yos_code", length = 4, nullable = false, updatable = false)
    private String yosCode;

    @Column(name = "amount", precision = 18, scale = 2, nullable = false, updatable = false)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 16, nullable = false)
    private PaymentStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected PaymentOrder() {
    }

    private PaymentOrder(String orderNo, String yosCode, BigDecimal amount, Instant now) {
        this.orderNo = orderNo;
        this.yosCode = yosCode;
        this.amount = amount;
        this.status = PaymentStatus.GONDERILDI;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public static PaymentOrder create(String yosCode, BigDecimal amount, Instant now) {
        return new PaymentOrder(UUID.randomUUID().toString(), yosCode, amount, now);
    }

    /** Returns whether the status actually changed, since only a change produces an event. */
    public boolean changeStatus(PaymentStatus newStatus, Instant now) {
        if (status == newStatus) {
            return false;
        }
        status = newStatus;
        updatedAt = now;
        return true;
    }

    public String getOrderNo() {
        return orderNo;
    }

    public String getYosCode() {
        return yosCode;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public PaymentStatus getStatus() {
        return status;
    }
}
