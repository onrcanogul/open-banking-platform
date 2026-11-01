package dev.obe.mockhhs.application.result;

import dev.obe.mockhhs.domain.model.Consent;
import dev.obe.mockhhs.domain.model.ConsentStatus;

public record ConsentResult(String consentNo, ConsentStatus status, String cancelCode) {

    public static ConsentResult from(Consent consent) {
        return new ConsentResult(consent.getConsentNo(), consent.getStatus(), consent.getCancelCode());
    }
}
