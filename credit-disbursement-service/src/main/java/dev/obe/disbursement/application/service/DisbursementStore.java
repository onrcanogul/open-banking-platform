package dev.obe.disbursement.application.service;

import dev.obe.disbursement.domain.exception.DisbursementNotFoundException;
import dev.obe.disbursement.domain.model.Disbursement;
import dev.obe.disbursement.domain.model.DisbursementStatus;
import dev.obe.disbursement.domain.repository.DisbursementRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Database steps of a disbursement, each in its own short transaction, because no transaction may stay
 * open during a call to the core. Inserting uses a new transaction so that a rejected insert can be
 * followed by reading the winner.
 */
@Service
class DisbursementStore {

    private static final Set<DisbursementStatus> WORKABLE = Set.of(DisbursementStatus.ALINDI,
            DisbursementStatus.ONAYLANDI, DisbursementStatus.LIMIT_AYRILDI, DisbursementStatus.MUHASEBELESTI,
            DisbursementStatus.TELAFI_EDILIYOR, DisbursementStatus.BELIRSIZ);

    private final DisbursementRepository repository;
    private final int claimSize;
    private final Duration claimLease;

    DisbursementStore(DisbursementRepository repository, @Value("${obe.saga.claim-size}") int claimSize,
            @Value("${obe.saga.claim-lease}") Duration claimLease) {
        this.repository = repository;
        this.claimSize = claimSize;
        this.claimLease = claimLease;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    Disbursement insert(Disbursement disbursement) {
        return repository.saveAndFlush(disbursement);
    }

    /** Takes due rows for this worker. The lease returns them to the queue if the worker dies. */
    @Transactional
    List<String> claimDue() {
        Instant now = now();
        List<String> numbers = repository.findDueNumbers(WORKABLE, now, PageRequest.ofSize(claimSize));
        if (numbers.isEmpty()) {
            return List.of();
        }
        List<Disbursement> claimed = repository.lockDue(numbers, now);
        claimed.forEach(disbursement -> disbursement.claimUntil(now.plus(claimLease)));
        return claimed.stream().map(Disbursement::getDisbursementNo).toList();
    }

    @Transactional(readOnly = true)
    Disbursement load(String disbursementNo) {
        return repository.findById(disbursementNo)
                .orElseThrow(() -> new DisbursementNotFoundException(disbursementNo));
    }

    /** Stored after each step, so a worker that dies resumes from the last finished step. */
    @Transactional
    void moveTo(String disbursementNo, DisbursementStatus next) {
        load(disbursementNo).moveTo(next, now());
    }

    @Transactional
    void fail(String disbursementNo, String reason) {
        load(disbursementNo).fail(reason, now());
    }

    @Transactional
    void startCompensation(String disbursementNo, String reason) {
        load(disbursementNo).startCompensation(reason, now());
    }

    @Transactional
    void finishCompensation(String disbursementNo) {
        load(disbursementNo).finishCompensation(now());
    }

    @Transactional
    void markUncertain(String disbursementNo) {
        load(disbursementNo).markUncertain(now());
    }

    private Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MILLIS);
    }
}
