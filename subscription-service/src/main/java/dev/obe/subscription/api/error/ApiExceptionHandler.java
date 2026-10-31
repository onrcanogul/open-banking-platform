package dev.obe.subscription.api.error;

import dev.obe.subscription.common.error.OhvpsError;
import dev.obe.subscription.common.error.OhvpsException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
class ApiExceptionHandler {

    private static final String FIELD_INVALID = "TR.OHVPS.Field.Invalid";
    private static final String FIELD_MISSING = "TR.OHVPS.Field.Missing";

    @ExceptionHandler(OhvpsException.class)
    ResponseEntity<ErrorResponse> handleOhvps(OhvpsException ex, HttpServletRequest request) {
        return response(ex.error(), ex.getMessage(), List.of(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorResponse> handleInvalidBody(MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<ErrorResponse.FieldError> fields = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> new ErrorResponse.FieldError(e.getObjectName(), e.getField(), e.getDefaultMessage(),
                        e.getRejectedValue() == null ? FIELD_MISSING : FIELD_INVALID))
                .toList();
        return response(OhvpsError.INVALID_FORMAT, OhvpsError.INVALID_FORMAT.message(), fields, request);
    }

    @ExceptionHandler({MissingRequestHeaderException.class, HttpMessageNotReadableException.class})
    ResponseEntity<ErrorResponse> handleUnreadableRequest(Exception ex, HttpServletRequest request) {
        return response(OhvpsError.INVALID_FORMAT, OhvpsError.INVALID_FORMAT.message(), List.of(), request);
    }

    private ResponseEntity<ErrorResponse> response(OhvpsError error, String detail,
            List<ErrorResponse.FieldError> fields, HttpServletRequest request) {
        ErrorResponse body = new ErrorResponse(request.getRequestURI(), error.status().value(),
                error.status().getReasonPhrase(), detail, error.messageTr(), fields, error.code());
        return ResponseEntity.status(error.status()).body(body);
    }
}
