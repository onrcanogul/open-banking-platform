package dev.obe.mockhhs.application.service;

import dev.obe.contract.event.EventType;
import dev.obe.mockhhs.application.result.AuthorizationCodeResult;
import dev.obe.mockhhs.application.result.ConsentResult;
import dev.obe.mockhhs.domain.exception.ConsentNotFoundException;
import dev.obe.mockhhs.domain.exception.InvalidConsentStateException;
import dev.obe.mockhhs.domain.model.Consent;
import dev.obe.mockhhs.domain.model.ConsentType;
import dev.obe.mockhhs.domain.model.OutboxEvent;
import dev.obe.mockhhs.domain.repository.ConsentRepository;
import dev.obe.mockhhs.domain.repository.OutboxEventRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Every state change writes its event in the same transaction, as with payment orders. */
@Service
public class ConsentService {

    private final ConsentRepository consentRepository;
    private final OutboxEventRepository outboxRepository;
    private final Duration codeLifetime;

    public ConsentService(ConsentRepository consentRepository, OutboxEventRepository outboxRepository,
            @Value("${obe.consent.authorization-code-lifetime}") Duration codeLifetime) {
        this.consentRepository = consentRepository;
        this.outboxRepository = outboxRepository;
        this.codeLifetime = codeLifetime;
    }

    @Transactional
    public ConsentResult create(String yosCode, ConsentType type) {
        return ConsentResult.from(consentRepository.save(Consent.create(yosCode, type, now())));
    }

    /** The customer confirmed in the bank's mobile application. */
    @Transactional
    public ConsentResult authorize(String consentNo) {
        Consent consent = find(consentNo);
        Instant now = now();
        consent.authorize(UUID.randomUUID().toString(), now.plus(codeLifetime), now);
        outboxRepository.save(OutboxEvent.consentEvent(consent, EventType.AYRIK_GKD_BASARILI, now));
        return ConsentResult.from(consent);
    }

    /** The bank refused after the customer logged in, or the customer gave up. */
    @Transactional
    public ConsentResult rejectAuthentication(String consentNo, String cancelCode) {
        Consent consent = find(consentNo);
        Instant now = now();
        consent.rejectAuthentication(cancelCode, now);
        outboxRepository.save(OutboxEvent.consentEvent(consent, EventType.AYRIK_GKD_BASARISIZ, now));
        return ConsentResult.from(consent);
    }

    /** The customer revoked the consent through the bank. Only account consents have this event. */
    @Transactional
    public ConsentResult revokeByCustomer(String consentNo) {
        Consent consent = find(consentNo);
        if (consent.getType() != ConsentType.ACCOUNT_INFORMATION) {
            throw new InvalidConsentStateException("Only account information consents can be revoked here");
        }
        Instant now = now();
        consent.revokeByCustomer(now);
        outboxRepository.save(OutboxEvent.consentEvent(consent, EventType.KAYNAK_GUNCELLENDI, now));
        return ConsentResult.from(consent);
    }

    /** Every failure looks the same to the caller, so a YOS cannot probe another YOS's consents. */
    @Transactional(readOnly = true)
    public AuthorizationCodeResult getAuthorizationCode(String tppCode, String consentNo, String consentTypeCode) {
        Consent consent = consentRepository.findById(consentNo)
                .filter(found -> found.getYosCode().equals(tppCode))
                .filter(found -> found.getType().code().equals(consentTypeCode))
                .orElseThrow(() -> new ConsentNotFoundException(consentNo));
        String code = consent.validAuthorizationCode(now())
                .orElseThrow(() -> new ConsentNotFoundException(consentNo));
        return new AuthorizationCodeResult(code, consent.getConsentNo(), consent.getStatus().code());
    }

    private Consent find(String consentNo) {
        return consentRepository.findById(consentNo).orElseThrow(() -> new ConsentNotFoundException(consentNo));
    }

    // Trimmed to the column's millisecond precision so responses match what is stored
    private Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MILLIS);
    }
}
