package dev.obe.disbursement.domain.model;

import java.util.Set;

/** The steps of a disbursement. This is the only place that says which step may follow which. */
public enum DisbursementStatus {
    TAMAMLANDI,
    BASARISIZ,
    /** A step's outcome is unknown. Retrying could pay twice, so only a check may move it on. */
    BELIRSIZ,
    MUHASEBELESTI,
    LIMIT_AYRILDI,
    ONAYLANDI,
    ALINDI;

    public boolean canMoveTo(DisbursementStatus next) {
        return allowedNext().contains(next);
    }

    public boolean isFinal() {
        return this == TAMAMLANDI || this == BASARISIZ;
    }

    private Set<DisbursementStatus> allowedNext() {
        return switch (this) {
            case ALINDI -> Set.of(ONAYLANDI, BASARISIZ);
            case ONAYLANDI -> Set.of(LIMIT_AYRILDI, BASARISIZ, BELIRSIZ);
            case LIMIT_AYRILDI -> Set.of(MUHASEBELESTI, BASARISIZ, BELIRSIZ);
            case MUHASEBELESTI -> Set.of(TAMAMLANDI, BASARISIZ, BELIRSIZ);
            case BELIRSIZ -> Set.of(TAMAMLANDI, BASARISIZ);
            case TAMAMLANDI, BASARISIZ -> Set.of();
        };
    }
}
