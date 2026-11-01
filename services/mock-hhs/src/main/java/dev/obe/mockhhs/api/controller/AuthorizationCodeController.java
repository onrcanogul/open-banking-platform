package dev.obe.mockhhs.api.controller;

import dev.obe.mockhhs.application.result.AuthorizationCodeResult;
import dev.obe.mockhhs.application.service.ConsentService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Where a YOS collects the code after AYRIK_GKD_BASARILI. */
@RestController
class AuthorizationCodeController {

    private final ConsentService service;

    AuthorizationCodeController(ConsentService service) {
        this.service = service;
    }

    @GetMapping("/yetkilendirme-kodu")
    AuthorizationCodeResult get(@RequestHeader("X-TPP-Code") String tppCode, @RequestParam String rizaNo,
            @RequestParam String rizaTip) {
        return service.getAuthorizationCode(tppCode, rizaNo, rizaTip);
    }
}
