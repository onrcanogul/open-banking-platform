package dev.obe.mockhhs.domain.exception;

public class PaymentOrderNotFoundException extends RuntimeException {

    public PaymentOrderNotFoundException(String orderNo) {
        super("Payment order not found: " + orderNo);
    }
}
