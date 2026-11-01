package dev.obe.mockhhs.api.error;

import dev.obe.mockhhs.domain.exception.ConsentNotFoundException;
import dev.obe.mockhhs.domain.exception.InvalidConsentStateException;
import dev.obe.mockhhs.domain.exception.PaymentOrderNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
class ApiExceptionHandler {

    @ExceptionHandler({PaymentOrderNotFoundException.class, ConsentNotFoundException.class})
    ProblemDetail handleNotFound(RuntimeException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(InvalidConsentStateException.class)
    ProblemDetail handleInvalidState(InvalidConsentStateException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    }
}
