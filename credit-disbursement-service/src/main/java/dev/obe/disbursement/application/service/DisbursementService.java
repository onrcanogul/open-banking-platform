package dev.obe.disbursement.application.service;

import dev.obe.disbursement.application.result.DisbursementResult;
import dev.obe.disbursement.domain.exception.DisbursementNotFoundException;
import dev.obe.disbursement.domain.exception.IdempotencyConflictException;
import dev.obe.disbursement.domain.model.Disbursement;
import dev.obe.disbursement.domain.model.DisbursementDetails;
import dev.obe.disbursement.domain.repository.DisbursementRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DisbursementService {

    private final DisbursementRepository repository;
    private final DisbursementStore store;

    public DisbursementService(DisbursementRepository repository, DisbursementStore store) {
        this.repository = repository;
        this.store = store;
    }

    /**
     * Starts a disbursement once per key. A repeat of the same request returns the first result, so a
     * partner can safely retry after a timeout. The unique key decides between concurrent requests.
     */
    public DisbursementResult accept(DisbursementDetails details, String idempotencyKey) {
        String requestHash = details.hash();
        Optional<Disbursement> existing = find(details.partnerCode(), idempotencyKey);
        if (existing.isPresent()) {
            return repeatOf(existing.get(), requestHash, idempotencyKey);
        }
        try {
            Disbursement created = Disbursement.create(details, idempotencyKey, requestHash, now());
            return DisbursementResult.from(store.insert(created));
        } catch (DataIntegrityViolationException ex) {
            if (!isKeyViolation(ex)) {
                throw ex;
            }
            Disbursement winner = find(details.partnerCode(), idempotencyKey).orElseThrow(() -> ex);
            return repeatOf(winner, requestHash, idempotencyKey);
        }
    }

    /** Answers 404 for another partner's disbursement so its existence is not revealed. */
    @Transactional(readOnly = true)
    public DisbursementResult get(String partnerCode, String disbursementNo) {
        return repository.findById(disbursementNo)
                .filter(disbursement -> disbursement.getPartnerCode().equals(partnerCode))
                .map(DisbursementResult::from)
                .orElseThrow(() -> new DisbursementNotFoundException(disbursementNo));
    }

    private Optional<Disbursement> find(String partnerCode, String idempotencyKey) {
        return repository.findByPartnerCodeAndIdempotencyKey(partnerCode, idempotencyKey);
    }

    private DisbursementResult repeatOf(Disbursement first, String requestHash, String idempotencyKey) {
        if (!first.getRequestHash().equals(requestHash)) {
            throw new IdempotencyConflictException(idempotencyKey);
        }
        return DisbursementResult.from(first);
    }

    private boolean isKeyViolation(DataIntegrityViolationException ex) {
        return ex.getCause() instanceof ConstraintViolationException violation
                && violation.getConstraintName() != null
                && violation.getConstraintName().toUpperCase().endsWith(Disbursement.UNIQUE_KEY_CONSTRAINT);
    }

    // Trimmed to the column's millisecond precision so responses match what is stored
    private Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MILLIS);
    }
}
