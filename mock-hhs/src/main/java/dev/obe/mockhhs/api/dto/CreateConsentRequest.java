package dev.obe.mockhhs.api.dto;

import dev.obe.mockhhs.domain.model.ConsentType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record CreateConsentRequest(
        @NotNull @Pattern(regexp = "[A-Za-z0-9]{4}") String yosCode,
        @NotNull ConsentType type) {
}
