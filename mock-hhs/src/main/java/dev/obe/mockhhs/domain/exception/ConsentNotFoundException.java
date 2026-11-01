package dev.obe.mockhhs.domain.exception;

public class ConsentNotFoundException extends RuntimeException {

    public ConsentNotFoundException(String consentNo) {
        super("Consent not found: " + consentNo);
    }
}
