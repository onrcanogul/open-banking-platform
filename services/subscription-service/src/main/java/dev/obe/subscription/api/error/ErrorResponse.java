package dev.obe.subscription.api.error;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ErrorResponse(
        String path,
        int httpCode,
        String httpMessage,
        String moreInformation,
        String moreInformationTr,
        List<FieldError> fieldErrors,
        String errorCode) {

    public record FieldError(String objectName, String field, String message, String code) {
    }
}
