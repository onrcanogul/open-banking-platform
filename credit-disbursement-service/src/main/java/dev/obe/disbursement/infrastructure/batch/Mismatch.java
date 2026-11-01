package dev.obe.disbursement.infrastructure.batch;

/** A disbursement whose status disagrees with the core, or that stayed uncertain for too long. */
record Mismatch(String disbursementNo, Type type, String ourStatus, boolean coreTransfer, String correlationId) {

    enum Type {
        /** We told the partner it failed, but the money reached the customer. */
        PAID_BUT_FAILED,
        /** We told the partner it finished, but the money never moved. */
        COMPLETED_BUT_UNPAID,
        /** Too old to be routine, whichever way the core answers. */
        STALE_UNCERTAIN
    }
}
