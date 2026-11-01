package dev.obe.dispatcher.domain.policy;

import static org.assertj.core.api.Assertions.assertThat;

import dev.obe.contract.event.EventType;
import dev.obe.contract.event.NotificationEvent;
import dev.obe.contract.event.ResourceType;
import dev.obe.contract.subscription.SubscribedType;
import dev.obe.contract.subscription.SubscriptionView;
import dev.obe.dispatcher.domain.model.DeliveryBatch;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class DeliveryPlannerTest {

    private static final String YOS_A = "8001";
    private static final String YOS_B = "8002";

    private static final SubscribedType PAYMENT =
            new SubscribedType(EventType.KAYNAK_GUNCELLENDI, ResourceType.ODEME_EMRI);
    private static final SubscribedType BALANCE =
            new SubscribedType(EventType.KAYNAK_GUNCELLENDI, ResourceType.BAKIYE);

    private final DeliveryPlanner planner = new DeliveryPlanner();

    @Test
    void keepsSubscribedEvent() {
        NotificationEvent event = paymentEvent(YOS_A);

        List<DeliveryBatch> batches = plan(List.of(event), Map.of(YOS_A, subscription(YOS_A, PAYMENT)));

        assertThat(batches).containsExactly(new DeliveryBatch(YOS_A, List.of(event)));
    }

    @Test
    void dropsEventTypeTheYosDidNotSubscribeTo() {
        List<DeliveryBatch> batches = plan(List.of(paymentEvent(YOS_A)),
                Map.of(YOS_A, subscription(YOS_A, BALANCE)));

        assertThat(batches).isEmpty();
    }

    @Test
    void matchesEventAndResourceTypeTogether() {
        NotificationEvent sameResourceOtherEvent = event(YOS_A, EventType.AYRIK_GKD_BASARILI, ResourceType.ODEME_EMRI);

        List<DeliveryBatch> batches = plan(List.of(sameResourceOtherEvent),
                Map.of(YOS_A, subscription(YOS_A, PAYMENT)));

        assertThat(batches).isEmpty();
    }

    @Test
    void dropsEventsOfYosWithoutSubscription() {
        assertThat(plan(List.of(paymentEvent(YOS_A)), Map.of())).isEmpty();
    }

    @Test
    void separatesEventsOfDifferentYoses() {
        NotificationEvent eventA = paymentEvent(YOS_A);
        NotificationEvent eventB = paymentEvent(YOS_B);

        List<DeliveryBatch> batches = plan(List.of(eventA, eventB),
                Map.of(YOS_A, subscription(YOS_A, PAYMENT), YOS_B, subscription(YOS_B, PAYMENT)));

        assertThat(batches).containsExactlyInAnyOrder(
                new DeliveryBatch(YOS_A, List.of(eventA)),
                new DeliveryBatch(YOS_B, List.of(eventB)));
    }

    @Test
    void splitsIntoCallsOfAtMostOneHundredEvents() {
        List<NotificationEvent> events = paymentEvents(YOS_A, 250);

        List<DeliveryBatch> batches = plan(events, Map.of(YOS_A, subscription(YOS_A, PAYMENT)));

        assertThat(batches).extracting(batch -> batch.events().size()).containsExactly(100, 100, 50);
    }

    @Test
    void exactlyOneHundredEventsFitInOneCall() {
        List<NotificationEvent> events = paymentEvents(YOS_A, 100);

        List<DeliveryBatch> batches = plan(events, Map.of(YOS_A, subscription(YOS_A, PAYMENT)));

        assertThat(batches).extracting(batch -> batch.events().size()).containsExactly(100);
    }

    @Test
    void keepsReadOrderAcrossCalls() {
        List<NotificationEvent> events = paymentEvents(YOS_A, 150);

        List<DeliveryBatch> batches = plan(events, Map.of(YOS_A, subscription(YOS_A, PAYMENT)));

        assertThat(batches.stream().flatMap(batch -> batch.events().stream()).toList()).isEqualTo(events);
    }

    @Test
    void filtersBeforeCountingTowardsTheLimit() {
        List<NotificationEvent> payments = paymentEvents(YOS_A, 100);
        List<NotificationEvent> mixed = new ArrayList<>(payments);
        mixed.add(50, event(YOS_A, EventType.KAYNAK_GUNCELLENDI, ResourceType.BAKIYE));

        List<DeliveryBatch> batches = plan(mixed, Map.of(YOS_A, subscription(YOS_A, PAYMENT)));

        assertThat(batches).extracting(batch -> batch.events().size()).containsExactly(100);
    }

    @Test
    void returnsNothingForNoEvents() {
        assertThat(plan(List.of(), Map.of())).isEmpty();
    }

    private List<DeliveryBatch> plan(List<NotificationEvent> events, Map<String, SubscriptionView> subscriptions) {
        return planner.batches(planner.subscribed(events, subscriptions));
    }

    private SubscriptionView subscription(String yosCode, SubscribedType... types) {
        return new SubscriptionView(yosCode, List.of(types));
    }

    private List<NotificationEvent> paymentEvents(String yosCode, int count) {
        return IntStream.range(0, count).mapToObj(i -> paymentEvent(yosCode)).toList();
    }

    private NotificationEvent paymentEvent(String yosCode) {
        return event(yosCode, EventType.KAYNAK_GUNCELLENDI, ResourceType.ODEME_EMRI);
    }

    private NotificationEvent event(String yosCode, EventType eventType, ResourceType resourceType) {
        return new NotificationEvent(UUID.randomUUID().toString(), yosCode, eventType, resourceType,
                UUID.randomUUID().toString(), Instant.now());
    }
}
