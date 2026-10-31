package dev.obe.subscription.application.service;

import dev.obe.subscription.application.command.CreateSubscriptionCommand;
import dev.obe.subscription.application.command.Participant;
import dev.obe.subscription.application.command.UpdateSubscriptionCommand;
import dev.obe.subscription.application.result.SubscriptionResult;
import dev.obe.subscription.common.error.OhvpsError;
import dev.obe.subscription.common.error.OhvpsException;
import dev.obe.subscription.domain.model.EventSubscription;
import dev.obe.subscription.domain.model.SubscriptionType;
import dev.obe.subscription.domain.repository.EventSubscriptionRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EventSubscriptionService {

    private final EventSubscriptionRepository repository;
    private final SubscriptionTypeResolver typeResolver;
    private final String hhsCode;

    public EventSubscriptionService(EventSubscriptionRepository repository, SubscriptionTypeResolver typeResolver,
            @Value("${obe.hhs-code}") String hhsCode) {
        this.repository = repository;
        this.typeResolver = typeResolver;
        this.hhsCode = hhsCode;
    }

    @Transactional
    public SubscriptionResult create(CreateSubscriptionCommand command) {
        requireSameParticipant(command.caller(), command.declared());
        String yosCode = command.caller().yosCode();
        typeResolver.requireListenerApi(yosCode);
        Set<SubscriptionType> types = typeResolver.resolve(yosCode, command.types());
        EventSubscription subscription = EventSubscription.create(yosCode, types, now());
        // TODO(exercise): persist the subscription and return SubscriptionResult.from(saved, hhsCode).
        // A second subscription of the same YOS must become EVENT_SUBSCRIPTION_ALREADY_EXISTS,
        // including when two requests arrive at the same time.
        throw new UnsupportedOperationException("Subscription creation is not implemented yet");
    }

    @Transactional(readOnly = true)
    public SubscriptionResult get(Participant caller) {
        requireThisHhs(caller);
        EventSubscription subscription = repository.findByYosCode(caller.yosCode())
                .orElseThrow(() -> new OhvpsException(OhvpsError.RESOURCE_NOT_FOUND));
        return SubscriptionResult.from(subscription, hhsCode);
    }

    @Transactional
    public SubscriptionResult update(UpdateSubscriptionCommand command) {
        requireSameParticipant(command.caller(), command.declared());
        if (!command.subscriptionNo().equals(command.declaredSubscriptionNo())) {
            throw new OhvpsException(OhvpsError.INVALID_CONTENT, "olayAbonelikNo does not match the path");
        }
        String yosCode = command.caller().yosCode();
        EventSubscription subscription = findOwned(yosCode, command.subscriptionNo());
        subscription.replaceTypes(typeResolver.resolve(yosCode, command.types()), now());
        return SubscriptionResult.from(subscription, hhsCode);
    }

    @Transactional
    public void delete(Participant caller, String subscriptionNo) {
        requireThisHhs(caller);
        repository.delete(findOwned(caller.yosCode(), subscriptionNo));
    }

    // Answers 404 for another YOS's subscription so its existence is not revealed
    private EventSubscription findOwned(String yosCode, String subscriptionNo) {
        return repository.findById(subscriptionNo)
                .filter(subscription -> subscription.getYosCode().equals(yosCode))
                .orElseThrow(() -> new OhvpsException(OhvpsError.RESOURCE_NOT_FOUND));
    }

    private void requireSameParticipant(Participant caller, Participant declared) {
        requireThisHhs(caller);
        if (!caller.equals(declared)) {
            throw new OhvpsException(OhvpsError.INVALID_CONTENT, "katilimciBlg does not match the request headers");
        }
    }

    private void requireThisHhs(Participant caller) {
        if (!hhsCode.equals(caller.hhsCode())) {
            throw new OhvpsException(OhvpsError.INVALID_CONTENT, "Request is not addressed to this HHS");
        }
    }

    // Trimmed to the column's millisecond precision so responses match what is stored
    private Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MILLIS);
    }
}
