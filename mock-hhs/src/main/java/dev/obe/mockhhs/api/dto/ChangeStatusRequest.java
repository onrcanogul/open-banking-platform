package dev.obe.mockhhs.api.dto;

import dev.obe.mockhhs.domain.model.PaymentStatus;
import jakarta.validation.constraints.NotNull;

public record ChangeStatusRequest(@NotNull PaymentStatus status) {
}
