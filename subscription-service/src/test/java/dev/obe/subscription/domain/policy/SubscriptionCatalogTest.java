package dev.obe.subscription.domain.policy;

import static org.assertj.core.api.Assertions.assertThat;

import dev.obe.contract.event.EventType;
import dev.obe.contract.event.ResourceType;
import dev.obe.subscription.domain.model.SubscriptionType;
import dev.obe.subscription.domain.model.YosRole;
import org.junit.jupiter.api.Test;

class SubscriptionCatalogTest {

    @Test
    void returnsRoleRequiredForKnownPair() {
        SubscriptionType paymentOrder = new SubscriptionType(EventType.KAYNAK_GUNCELLENDI, ResourceType.ODEME_EMRI);
        SubscriptionType balance = new SubscriptionType(EventType.KAYNAK_GUNCELLENDI, ResourceType.BAKIYE);

        assertThat(SubscriptionCatalog.requiredRole(paymentOrder)).contains(YosRole.OBH);
        assertThat(SubscriptionCatalog.requiredRole(balance)).contains(YosRole.HBH);
    }

    @Test
    void rejectsPairThatIsNotInTheTable() {
        SubscriptionType wrongPair = new SubscriptionType(EventType.AYRIK_GKD_BASARILI, ResourceType.BAKIYE);

        assertThat(SubscriptionCatalog.requiredRole(wrongPair)).isEmpty();
    }

    @Test
    void excludesBkmDeliveredEvents() {
        SubscriptionType bkmEvent = new SubscriptionType(EventType.HHS_YOS_GUNCELLENDI, ResourceType.HHS);

        assertThat(SubscriptionCatalog.requiredRole(bkmEvent)).isEmpty();
    }
}
