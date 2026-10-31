package dev.obe.subscription.application.port.out;

import dev.obe.subscription.domain.model.YosRole;
import java.util.Set;

public record YosProfile(Set<YosRole> roles, boolean listenerApiProvided) {
}
