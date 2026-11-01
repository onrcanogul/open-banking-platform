package dev.obe.disbursement.domain.repository;

import dev.obe.disbursement.domain.model.Disbursement;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DisbursementRepository extends JpaRepository<Disbursement, String> {

    Optional<Disbursement> findByPartnerCodeAndIdempotencyKey(String partnerCode, String idempotencyKey);
}
