package dev.obe.subscription.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record ParticipantInfo(
        @NotNull @Pattern(regexp = "[A-Za-z0-9]{4}") String hhsKod,
        @NotNull @Pattern(regexp = "[A-Za-z0-9]{4}") String yosKod) {
}
