package dev.obe.disbursement.api.controller;

import dev.obe.disbursement.api.dto.CreateDisbursementRequest;
import dev.obe.disbursement.application.result.DisbursementResult;
import dev.obe.disbursement.application.service.DisbursementService;
import dev.obe.disbursement.domain.model.DisbursementDetails;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/disbursements")
class DisbursementController {

    private static final String PARTNER_CODE = "X-Partner-Code";

    private final DisbursementService service;

    DisbursementController(DisbursementService service) {
        this.service = service;
    }

    /** Answers 202 because the money has not moved yet. The partner follows the status. */
    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    DisbursementResult create(@RequestHeader(PARTNER_CODE) @Size(max = 16) String partnerCode,
            @RequestHeader("Idempotency-Key") @Size(min = 1, max = 64) String idempotencyKey,
            @Valid @RequestBody CreateDisbursementRequest request) {
        DisbursementDetails details = new DisbursementDetails(partnerCode, request.customerNo(), request.accountNo(),
                request.amount(), request.currency());
        return service.accept(details, idempotencyKey);
    }

    @GetMapping("/{disbursementNo}")
    DisbursementResult get(@RequestHeader(PARTNER_CODE) String partnerCode, @PathVariable String disbursementNo) {
        return service.get(partnerCode, disbursementNo);
    }
}
