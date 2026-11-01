package dev.obe.mockhhs.application.result;

import dev.obe.mockhhs.domain.model.PaymentOrder;
import dev.obe.mockhhs.domain.model.PaymentStatus;

public record PaymentOrderResult(String orderNo, PaymentStatus status) {

    public static PaymentOrderResult from(PaymentOrder order) {
        return new PaymentOrderResult(order.getOrderNo(), order.getStatus());
    }
}
