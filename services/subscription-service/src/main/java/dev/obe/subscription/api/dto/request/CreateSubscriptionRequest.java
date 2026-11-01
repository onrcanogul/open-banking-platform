package dev.obe.subscription.api.dto.request;

import dev.obe.subscription.api.dto.ParticipantInfo;
import dev.obe.subscription.api.dto.SubscriptionTypeDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record CreateSubscriptionRequest(
        @NotNull @Valid ParticipantInfo katilimciBlg,
        @NotEmpty List<@NotNull @Valid SubscriptionTypeDto> abonelikTipleri) {
}
