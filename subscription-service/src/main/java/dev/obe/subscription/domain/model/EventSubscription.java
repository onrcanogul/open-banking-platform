package dev.obe.subscription.domain.model;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "event_subscription")
public class EventSubscription {

    @Id
    @Column(name = "subscription_no", length = 36)
    private String subscriptionNo;

    @Column(name = "yos_code", length = 4, nullable = false, updatable = false)
    private String yosCode;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @ElementCollection
    @CollectionTable(name = "event_subscription_type", joinColumns = @JoinColumn(name = "subscription_no"))
    private Set<SubscriptionType> types = new HashSet<>();

    protected EventSubscription() {
    }

    private EventSubscription(String subscriptionNo, String yosCode, Set<SubscriptionType> types, Instant now) {
        this.subscriptionNo = subscriptionNo;
        this.yosCode = yosCode;
        this.types.addAll(types);
        this.createdAt = now;
        this.updatedAt = now;
    }

    public static EventSubscription create(String yosCode, Set<SubscriptionType> types, Instant now) {
        return new EventSubscription(UUID.randomUUID().toString(), yosCode, types, now);
    }

    public void replaceTypes(Set<SubscriptionType> newTypes, Instant now) {
        types.clear();
        types.addAll(newTypes);
        updatedAt = now;
    }

    public String getSubscriptionNo() {
        return subscriptionNo;
    }

    public String getYosCode() {
        return yosCode;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Set<SubscriptionType> getTypes() {
        return Set.copyOf(types);
    }
}
