package dev.obe.disbursement.domain.repository;

import dev.obe.disbursement.domain.model.Disbursement;
import dev.obe.disbursement.domain.model.DisbursementStatus;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

public interface DisbursementRepository extends JpaRepository<Disbursement, String> {

    Optional<Disbursement> findByPartnerCodeAndIdempotencyKey(String partnerCode, String idempotencyKey);

    List<Disbursement> findAllBy(Pageable page);

    /** Selection and locking are separate because Oracle rejects FOR UPDATE with a row limit. */
    @Query("""
            select d.disbursementNo from Disbursement d
            where d.status in :statuses and d.nextStepAt <= :now
            order by d.nextStepAt
            """)
    List<String> findDueNumbers(@Param("statuses") Collection<DisbursementStatus> statuses,
            @Param("now") Instant now, Pageable page);

    /** A lock timeout of -2 means SKIP LOCKED: rows held by another worker are left to it. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query("""
            select d from Disbursement d
            where d.disbursementNo in :numbers and d.nextStepAt <= :now
            """)
    List<Disbursement> lockDue(@Param("numbers") List<String> numbers, @Param("now") Instant now);
}
