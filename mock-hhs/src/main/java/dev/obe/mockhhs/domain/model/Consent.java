package dev.obe.mockhhs.domain.model;

import dev.obe.mockhhs.domain.exception.InvalidConsentStateException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Entity
@Table(name = "consent")
public class Consent {

    private static final String CANCELLED_BY_CUSTOMER_AT_HHS = "02";

    @Id
    @Column(name = "consent_no", length = 36)
    private String consentNo;

    @Column(name = "yos_code", length = 4, nullable = false, updatable = false)
    private String yosCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "consent_type", length = 24, nullable = false, updatable = false)
    private ConsentType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 24, nullable = false)
    private ConsentStatus status;

    @Column(name = "cancel_code", length = 2)
    private String cancelCode;

    @Column(name = "authorization_code", length = 255)
    private String authorizationCode;

    @Column(name = "code_expires_at")
    private Instant codeExpiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Consent() {
    }

    private Consent(String yosCode, ConsentType type, Instant now) {
        this.consentNo = UUID.randomUUID().toString();
        this.yosCode = yosCode;
        this.type = type;
        this.status = ConsentStatus.YETKI_BEKLENIYOR;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public static Consent create(String yosCode, ConsentType type, Instant now) {
        return new Consent(yosCode, type, now);
    }

    public void authorize(String code, Instant expiresAt, Instant now) {
        requireStatus(ConsentStatus.YETKI_BEKLENIYOR);
        status = ConsentStatus.YETKILENDIRILDI;
        authorizationCode = code;
        codeExpiresAt = expiresAt;
        updatedAt = now;
    }

    public void rejectAuthentication(String cancelCode, Instant now) {
        requireStatus(ConsentStatus.YETKI_BEKLENIYOR);
        cancel(cancelCode, now);
    }

    public void revokeByCustomer(Instant now) {
        requireStatus(ConsentStatus.YETKILENDIRILDI);
        cancel(CANCELLED_BY_CUSTOMER_AT_HHS, now);
    }

    /** The code exists only while the consent is authorized and the code has not expired. */
    public Optional<String> validAuthorizationCode(Instant now) {
        boolean valid = status == ConsentStatus.YETKILENDIRILDI && now.isBefore(codeExpiresAt);
        return valid ? Optional.of(authorizationCode) : Optional.empty();
    }

    private void cancel(String code, Instant now) {
        status = ConsentStatus.YETKI_IPTAL;
        cancelCode = code;
        authorizationCode = null;
        codeExpiresAt = null;
        updatedAt = now;
    }

    private void requireStatus(ConsentStatus expected) {
        if (status != expected) {
            throw new InvalidConsentStateException("Consent " + consentNo + " is " + status + ", not " + expected);
        }
    }

    public String getConsentNo() {
        return consentNo;
    }

    public String getYosCode() {
        return yosCode;
    }

    public ConsentType getType() {
        return type;
    }

    public ConsentStatus getStatus() {
        return status;
    }

    public String getCancelCode() {
        return cancelCode;
    }
}
