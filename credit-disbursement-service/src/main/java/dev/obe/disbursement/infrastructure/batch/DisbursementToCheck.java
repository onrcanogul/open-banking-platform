package dev.obe.disbursement.infrastructure.batch;

record DisbursementToCheck(String disbursementNo, String status, String correlationId) {
}
