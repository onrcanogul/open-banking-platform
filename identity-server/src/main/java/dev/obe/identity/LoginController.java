package dev.obe.identity;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** Only shows the page. Spring Security handles the form post. */
@Controller
class LoginController {

    @GetMapping("/login")
    String login() {
        return "login";
    }
}
