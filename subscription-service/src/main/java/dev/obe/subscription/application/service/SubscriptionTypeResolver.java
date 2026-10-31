package dev.obe.subscription.application.service;

import dev.obe.subscription.application.command.RequestedType;
import dev.obe.subscription.application.port.out.YosDirectory;
import dev.obe.subscription.application.port.out.YosProfile;
import dev.obe.subscription.domain.model.EventType;
import dev.obe.subscription.domain.model.ResourceType;
import dev.obe.subscription.domain.policy.SubscriptionCatalog;
import dev.obe.subscription.domain.model.SubscriptionType;
import dev.obe.subscription.domain.model.YosRole;
import dev.obe.subscription.common.error.OhvpsError;
import dev.obe.subscription.common.error.OhvpsException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Turns requested strings into subscription types the given YOS is allowed to hold. */
@Component
class SubscriptionTypeResolver {

    private final YosDirectory yosDirectory;

    SubscriptionTypeResolver(YosDirectory yosDirectory) {
        this.yosDirectory = yosDirectory;
    }

    void requireListenerApi(String yosCode) {
        if (!profileOf(yosCode).listenerApiProvided()) {
            throw new OhvpsException(OhvpsError.INVALID_CONTENT, "YOS does not provide the listener API");
        }
    }

    Set<SubscriptionType> resolve(String yosCode, List<RequestedType> requested) {
        YosProfile profile = profileOf(yosCode);
        Set<SubscriptionType> types = new HashSet<>();
        for (RequestedType item : requested) {
            SubscriptionType type = parse(item);
            YosRole requiredRole = SubscriptionCatalog.requiredRole(type).orElseThrow(
                    () -> new OhvpsException(OhvpsError.INVALID_CONTENT, "Pair is not available: " + type));
            if (!profile.roles().contains(requiredRole)) {
                throw new OhvpsException(OhvpsError.INVALID_CONTENT, "YOS role does not allow: " + type);
            }
            if (isSeparateAuthEvent(type.eventType()) && !profile.listenerApiProvided()) {
                throw new OhvpsException(OhvpsError.INVALID_CONTENT, "Listener API is required for: " + type);
            }
            types.add(type);
        }
        return types;
    }

    private YosProfile profileOf(String yosCode) {
        return yosDirectory.find(yosCode).orElseThrow(
                () -> new OhvpsException(OhvpsError.INVALID_CONTENT, "Unknown YOS: " + yosCode));
    }

    private SubscriptionType parse(RequestedType item) {
        try {
            return new SubscriptionType(EventType.valueOf(item.eventType()), ResourceType.valueOf(item.resourceType()));
        } catch (IllegalArgumentException ex) {
            throw new OhvpsException(OhvpsError.EVENT_SUBSCRIPTION_NOT_FOUND);
        }
    }

    private boolean isSeparateAuthEvent(EventType type) {
        return type == EventType.AYRIK_GKD_BASARILI || type == EventType.AYRIK_GKD_BASARISIZ;
    }
}
