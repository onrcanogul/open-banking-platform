package dev.obe.mockhhs.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public record CreatePaymentOrderRequest(
        @NotNull @Pattern(regexp = "[A-Za-z0-9]{4}") String yosCode,
        @NotNull @Positive BigDecimal amount) {
}
