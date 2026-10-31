package dev.obe.subscription.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SubscriptionTypeDto(
        @NotBlank @Size(max = 36) String olayTipi,
        @NotBlank @Size(max = 36) String kaynakTipi) {
}
