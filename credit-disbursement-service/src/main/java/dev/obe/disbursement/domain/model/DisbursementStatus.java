package dev.obe.disbursement.domain.model;

import java.util.Set;

/** The steps of a disbursement. This is the only place that says which step may follow which. */
public enum DisbursementStatus {
    ALINDI,
    ONAYLANDI,
    LIMIT_AYRILDI,
    MUHASEBELESTI,
    TAMAMLANDI,
    /** A step was refused after earlier steps took effect, and those are being undone. */
    TELAFI_EDILIYOR,
    /** A step's outcome is unknown. Going on or undoing could both be wrong, so the core is asked. */
    BELIRSIZ,
    BASARISIZ;

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
            case LIMIT_AYRILDI -> Set.of(MUHASEBELESTI, TELAFI_EDILIYOR, BELIRSIZ);
            case MUHASEBELESTI -> Set.of(TAMAMLANDI, TELAFI_EDILIYOR, BELIRSIZ);
            case BELIRSIZ -> Set.of(LIMIT_AYRILDI, MUHASEBELESTI, TAMAMLANDI, TELAFI_EDILIYOR);
            case TELAFI_EDILIYOR -> Set.of(BASARISIZ);
            case TAMAMLANDI, BASARISIZ -> Set.of();
        };
    }
}
