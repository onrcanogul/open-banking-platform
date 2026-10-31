package dev.obe.subscription.api.mapper;

import dev.obe.contract.subscription.SubscribedType;
import dev.obe.contract.subscription.SubscriptionView;
import dev.obe.subscription.api.dto.ParticipantInfo;
import dev.obe.subscription.api.dto.SubscriptionTypeDto;
import dev.obe.subscription.api.dto.request.CreateSubscriptionRequest;
import dev.obe.subscription.api.dto.request.UpdateSubscriptionRequest;
import dev.obe.subscription.api.dto.response.SubscriptionResponse;
import dev.obe.subscription.application.command.CreateSubscriptionCommand;
import dev.obe.subscription.application.command.Participant;
import dev.obe.subscription.application.command.RequestedType;
import dev.obe.subscription.application.command.UpdateSubscriptionCommand;
import dev.obe.subscription.application.result.SubscriptionResult;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class SubscriptionApiMapper {

    public CreateSubscriptionCommand toCommand(Participant caller, CreateSubscriptionRequest request) {
        return new CreateSubscriptionCommand(caller, toParticipant(request.katilimciBlg()),
                toRequestedTypes(request.abonelikTipleri()));
    }

    public UpdateSubscriptionCommand toCommand(Participant caller, String subscriptionNo,
            UpdateSubscriptionRequest request) {
        return new UpdateSubscriptionCommand(caller, toParticipant(request.katilimciBlg()), subscriptionNo,
                request.olayAbonelikNo(), toRequestedTypes(request.abonelikTipleri()));
    }

    public SubscriptionResponse toResponse(SubscriptionResult result) {
        ParticipantInfo participant = new ParticipantInfo(result.participant().hhsCode(),
                result.participant().yosCode());
        List<SubscriptionTypeDto> types = result.types().stream()
                .map(type -> new SubscriptionTypeDto(type.eventType().name(), type.resourceType().name()))
                .toList();
        return new SubscriptionResponse(result.subscriptionNo(), result.createdAt(), result.updatedAt(), participant,
                types);
    }

    public SubscriptionView toView(SubscriptionResult result) {
        List<SubscribedType> types = result.types().stream()
                .map(type -> new SubscribedType(type.eventType(), type.resourceType()))
                .toList();
        return new SubscriptionView(result.participant().yosCode(), types);
    }

    private Participant toParticipant(ParticipantInfo info) {
        return new Participant(info.hhsKod(), info.yosKod());
    }

    private List<RequestedType> toRequestedTypes(List<SubscriptionTypeDto> types) {
        return types.stream()
                .map(type -> new RequestedType(type.olayTipi(), type.kaynakTipi()))
                .toList();
    }
}
