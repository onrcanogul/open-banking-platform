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

    public PaymentOrderResult changeStatus(String orderNo, PaymentStatus newStatus) {
        PaymentOrder order = orderRepository.findById(orderNo)
                .orElseThrow(() -> new PaymentOrderNotFoundException(orderNo));
        Instant now = now();
        // TODO(exercise): apply order.changeStatus(newStatus, now). When it returns true, also write
        // OutboxEvent.paymentOrderUpdated(order, now). The status change and the event must be stored
        // together: if either write fails, neither may remain in the database.
        throw new UnsupportedOperationException("Status change is not implemented yet");
    }

    // Trimmed to the column's millisecond precision so responses match what is stored
    private Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MILLIS);
    }
}
