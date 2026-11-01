package dev.obe.mockhhs.domain.repository;

import dev.obe.mockhhs.domain.model.OutboxEvent;
import dev.obe.mockhhs.domain.model.OutboxStatus;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, String> {

    long countByStatus(OutboxStatus status);

    @Query("""
            select e from OutboxEvent e
            where e.status = dev.obe.mockhhs.domain.model.OutboxStatus.PUBLISHED
              and e.occurredAt >= :from
              and e.occurredAt <= :to
            order by e.occurredAt, e.eventNo
            """)
    List<OutboxEvent> findPublished(@Param("from") Instant from, @Param("to") Instant to, Pageable page);

    /** Removes at most one batch, so a large cleanup never holds a long transaction. */
    @Modifying
    @Query(value = """
            DELETE FROM outbox_event
            WHERE status = 'PUBLISHED' AND published_at < :cutoff AND ROWNUM <= :batchSize
            """, nativeQuery = true)
    int deletePublishedBefore(@Param("cutoff") LocalDateTime cutoff, @Param("batchSize") int batchSize);

    /** Selection and locking are separate because Oracle rejects FOR UPDATE with a row limit. */
    @Query("""
            select e.eventNo from OutboxEvent e
            where e.status = dev.obe.mockhhs.domain.model.OutboxStatus.PENDING
            order by e.occurredAt
            """)
    List<String> findPendingEventNos(Pageable page);

    /** A lock timeout of -2 means SKIP LOCKED: rows held by another relay are left to it. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query("""
            select e from OutboxEvent e
            where e.eventNo in :eventNos
              and e.status = dev.obe.mockhhs.domain.model.OutboxStatus.PENDING
            order by e.occurredAt
            """)
    List<OutboxEvent> lockPending(@Param("eventNos") List<String> eventNos);
}
