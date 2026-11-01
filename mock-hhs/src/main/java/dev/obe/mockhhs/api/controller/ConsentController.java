package dev.obe.mockhhs.api.controller;

import dev.obe.mockhhs.api.dto.CreateConsentRequest;
import dev.obe.mockhhs.api.dto.RejectAuthenticationRequest;
import dev.obe.mockhhs.application.result.ConsentResult;
import dev.obe.mockhhs.application.service.ConsentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Simulates the customer acting in the bank's mobile application. */
@RestController
@RequestMapping("/internal/consents")
class ConsentController {

    private final ConsentService service;

    ConsentController(ConsentService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    ConsentResult create(@Valid @RequestBody CreateConsentRequest request) {
        return service.create(request.yosCode(), request.type());
    }

    @PostMapping("/{consentNo}/authorization")
    ConsentResult authorize(@PathVariable String consentNo) {
        return service.authorize(consentNo);
    }

    @PostMapping("/{consentNo}/rejection")
    ConsentResult reject(@PathVariable String consentNo, @Valid @RequestBody RejectAuthenticationRequest request) {
        return service.rejectAuthentication(consentNo, request.cancelCode());
    }

    @PostMapping("/{consentNo}/revocation")
    ConsentResult revoke(@PathVariable String consentNo) {
        return service.revokeByCustomer(consentNo);
    }
}
