package dev.obe.disbursement.application.service;

import dev.obe.disbursement.domain.model.Disbursement;
import dev.obe.disbursement.domain.repository.DisbursementRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Saves in a transaction of its own, so a rejected insert can be followed by reading the winner. */
@Service
class DisbursementStore {

    private final DisbursementRepository repository;

    DisbursementStore(DisbursementRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    Disbursement insert(Disbursement disbursement) {
        return repository.saveAndFlush(disbursement);
    }
}
