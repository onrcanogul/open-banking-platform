package dev.obe.subscription.api.dto.response;

import dev.obe.subscription.api.dto.ParticipantInfo;
import dev.obe.subscription.api.dto.SubscriptionTypeDto;
import java.time.Instant;
import java.util.List;

public record SubscriptionResponse(
        String olayAbonelikNo,
        Instant olusturmaZamani,
        Instant guncellemeZamani,
        ParticipantInfo katilimciBlg,
        List<SubscriptionTypeDto> abonelikTipleri) {
}
