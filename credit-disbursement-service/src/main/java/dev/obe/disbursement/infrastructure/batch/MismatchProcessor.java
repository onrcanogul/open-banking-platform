package dev.obe.disbursement.infrastructure.batch;

import dev.obe.disbursement.application.port.out.CoreBankingClient;
import dev.obe.disbursement.application.port.out.CoreBankingClient.Step;
import dev.obe.disbursement.infrastructure.batch.Mismatch.Type;
import org.springframework.batch.infrastructure.item.ItemProcessor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Compares one disbursement with the core. Returning null drops it. A failed call to the core fails
 * the job on purpose: skipping would report a clean result for rows that were never checked.
 */
@Component
class MismatchProcessor implements ItemProcessor<DisbursementToCheck, Mismatch> {

    private static final String COUNT_REPORTED = "SELECT COUNT(*) FROM disbursement_mismatch WHERE disbursement_no = ?";

    private final CoreBankingClient core;
    private final JdbcTemplate jdbc;

    MismatchProcessor(CoreBankingClient core, JdbcTemplate jdbc) {
        this.core = core;
        this.jdbc = jdbc;
    }

    @Override
    public Mismatch process(DisbursementToCheck item) {
        if (alreadyReported(item)) {
            return null;
        }
        boolean transferred = core.isCompleted(Step.TRANSFER, item.disbursementNo());
        Type type = typeOf(item.status(), transferred);
        return type == null
                ? null
                : new Mismatch(item.disbursementNo(), type, item.status(), transferred, item.correlationId());
    }

    private Type typeOf(String status, boolean transferred) {
        return switch (status) {
            case "BASARISIZ" -> transferred ? Type.PAID_BUT_FAILED : null;
            case "TAMAMLANDI" -> transferred ? null : Type.COMPLETED_BUT_UNPAID;
            case "BELIRSIZ" -> Type.STALE_UNCERTAIN;
            default -> throw new IllegalStateException("Unexpected status " + status);
        };
    }

    private boolean alreadyReported(DisbursementToCheck item) {
        Integer count = jdbc.queryForObject(COUNT_REPORTED, Integer.class, item.disbursementNo());
        return count != null && count > 0;
    }
}
