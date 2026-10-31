package dev.obe.subscription.common.error;

import org.springframework.http.HttpStatus;

public enum OhvpsError {
    EVENT_SUBSCRIPTION_ALREADY_EXISTS(HttpStatus.BAD_REQUEST, "TR.OHVPS.Business.EventSubscriptionAlreadyExists",
            "Event subscription already exists", "Olay aboneligi zaten mevcut"),
    // The standard uses this code for values outside the event and resource type lists
    EVENT_SUBSCRIPTION_NOT_FOUND(HttpStatus.BAD_REQUEST, "TR.OHVPS.Business.EventSubscriptionNotFound",
            "Unknown event or resource type", "Gecersiz olay veya kaynak tipi"),
    INVALID_CONTENT(HttpStatus.BAD_REQUEST, "TR.OHVPS.Business.InvalidContent",
            "Invalid content", "Gecersiz icerik"),
    INVALID_FORMAT(HttpStatus.BAD_REQUEST, "TR.OHVPS.Resource.InvalidFormat",
            "Validation error", "Sema kontrolleri basarisiz"),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "TR.OHVPS.Resource.NotFound",
            "Resource not found", "Kaynak bulunamadi");

    private final HttpStatus status;
    private final String code;
    private final String message;
    private final String messageTr;

    OhvpsError(HttpStatus status, String code, String message, String messageTr) {
        this.status = status;
        this.code = code;
        this.message = message;
        this.messageTr = messageTr;
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }

    public String message() {
        return message;
    }

    public String messageTr() {
        return messageTr;
    }
}
