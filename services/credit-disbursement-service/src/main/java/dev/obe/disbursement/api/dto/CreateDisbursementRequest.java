package dev.obe.disbursement.api.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record CreateDisbursementRequest(
        @NotBlank @Size(max = 32) String customerNo,
        @NotBlank @Size(max = 34) String accountNo,
        @NotNull @Positive @Digits(integer = 16, fraction = 2) BigDecimal amount,
        @NotNull @Pattern(regexp = "TRY") String currency) {
}
