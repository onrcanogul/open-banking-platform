package dev.obe.mockhhs.application.service;

import dev.obe.mockhhs.application.result.PaymentOrderResult;
import dev.obe.mockhhs.domain.exception.PaymentOrderNotFoundException;
import dev.obe.mockhhs.domain.model.OutboxEvent;
import dev.obe.mockhhs.domain.model.PaymentOrder;
import dev.obe.mockhhs.domain.model.PaymentStatus;
import dev.obe.mockhhs.domain.repository.OutboxEventRepository;
import dev.obe.mockhhs.domain.repository.PaymentOrderRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentOrderService {

    private final PaymentOrderRepository orderRepository;
    private final OutboxEventRepository outboxRepository;

    public PaymentOrderService(PaymentOrderRepository orderRepository, OutboxEventRepository outboxRepository) {
        this.orderRepository = orderRepository;
        this.outboxRepository = outboxRepository;
    }

    @Transactional
    public PaymentOrderResult create(String yosCode, BigDecimal amount) {
        return PaymentOrderResult.from(orderRepository.save(PaymentOrder.create(yosCode, amount, now())));
    }

    @Transactional
    public PaymentOrderResult changeStatus(String orderNo, PaymentStatus newStatus) {
        PaymentOrder order = orderRepository.findById(orderNo)
                .orElseThrow(() -> new PaymentOrderNotFoundException(orderNo));
        Instant now = now();
        if (order.changeStatus(newStatus, now)) {
            outboxRepository.save(OutboxEvent.paymentOrderUpdated(order, now));
            PaymentOrder savedOrder = orderRepository.save(order);
            return PaymentOrderResult.from(savedOrder);
        }
        return PaymentOrderResult.from(order);
    }

    // Trimmed to the column's millisecond precision so responses match what is stored
    private Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MILLIS);
    }
}
