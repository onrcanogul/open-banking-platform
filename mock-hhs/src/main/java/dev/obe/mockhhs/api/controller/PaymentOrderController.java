package dev.obe.mockhhs.api.controller;

import dev.obe.mockhhs.api.dto.ChangeStatusRequest;
import dev.obe.mockhhs.api.dto.CreatePaymentOrderRequest;
import dev.obe.mockhhs.application.result.PaymentOrderResult;
import dev.obe.mockhhs.application.service.PaymentOrderService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Simulates core banking actions so events can be triggered by hand. */
@RestController
@RequestMapping("/internal/payment-orders")
class PaymentOrderController {

    private final PaymentOrderService service;

    PaymentOrderController(PaymentOrderService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    PaymentOrderResult create(@Valid @RequestBody CreatePaymentOrderRequest request) {
        return service.create(request.yosCode(), request.amount());
    }

    @PatchMapping("/{orderNo}/status")
    PaymentOrderResult changeStatus(@PathVariable String orderNo, @Valid @RequestBody ChangeStatusRequest request) {
        return service.changeStatus(orderNo, request.status());
    }
}
