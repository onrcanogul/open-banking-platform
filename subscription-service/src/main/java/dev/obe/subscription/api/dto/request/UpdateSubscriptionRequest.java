package dev.obe.subscription.api.dto.request;

import dev.obe.subscription.api.dto.ParticipantInfo;
import dev.obe.subscription.api.dto.SubscriptionTypeDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record UpdateSubscriptionRequest(
        @NotNull @Size(min = 1, max = 64) String olayAbonelikNo,
        @NotNull @Valid ParticipantInfo katilimciBlg,
        @NotEmpty List<@NotNull @Valid SubscriptionTypeDto> abonelikTipleri) {
}
