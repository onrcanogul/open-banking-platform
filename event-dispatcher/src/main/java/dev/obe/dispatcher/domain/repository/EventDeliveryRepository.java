package dev.obe.dispatcher.domain.repository;

import dev.obe.contract.event.EventType;
import dev.obe.contract.event.ResourceType;
import dev.obe.dispatcher.domain.model.EventDelivery;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

public interface EventDeliveryRepository extends JpaRepository<EventDelivery, String> {

    /** Selection and locking are separate because Oracle rejects FOR UPDATE with a row limit. */
    @Query("""
            select d.eventNo from EventDelivery d
            where d.status = dev.obe.dispatcher.domain.model.DeliveryStatus.PENDING
              and d.nextAttemptAt <= :now
            order by d.nextAttemptAt
            """)
    List<String> findDueEventNos(@Param("now") Instant now, Pageable page);

    /** A lock timeout of -2 means SKIP LOCKED: rows held by another sender are left to it. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query("""
            select d from EventDelivery d
            where d.eventNo in :eventNos
              and d.status = dev.obe.dispatcher.domain.model.DeliveryStatus.PENDING
              and d.nextAttemptAt <= :now
            order by d.occurredAt
            """)
    List<EventDelivery> lockDue(@Param("eventNos") List<String> eventNos, @Param("now") Instant now);

    @Query("""
            select d from EventDelivery d
            where d.status = dev.obe.dispatcher.domain.model.DeliveryStatus.UNDELIVERED
              and d.yosCode = :yosCode
              and d.occurredAt >= :from
              and d.occurredAt <= :to
            order by d.occurredAt, d.eventNo
            """)
    List<EventDelivery> findUndelivered(@Param("yosCode") String yosCode, @Param("from") Instant from,
            @Param("to") Instant to, Pageable page);

    /** The standard allows one undelivered record per YOS, resource and event type. */
    @Modifying
    @Query("""
            update EventDelivery d
            set d.status = dev.obe.dispatcher.domain.model.DeliveryStatus.SUPERSEDED
            where d.status = dev.obe.dispatcher.domain.model.DeliveryStatus.UNDELIVERED
              and d.yosCode = :yosCode
              and d.eventType = :eventType
              and d.resourceType = :resourceType
              and d.resourceNo = :resourceNo
              and d.eventNo <> :keptEventNo
            """)
    int supersedeUndelivered(@Param("yosCode") String yosCode, @Param("eventType") EventType eventType,
            @Param("resourceType") ResourceType resourceType, @Param("resourceNo") String resourceNo,
            @Param("keptEventNo") String keptEventNo);
}
