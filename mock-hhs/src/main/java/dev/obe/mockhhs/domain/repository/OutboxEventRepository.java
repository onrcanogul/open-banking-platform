package dev.obe.mockhhs.domain.repository;

import dev.obe.mockhhs.domain.model.OutboxEvent;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, String> {

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
