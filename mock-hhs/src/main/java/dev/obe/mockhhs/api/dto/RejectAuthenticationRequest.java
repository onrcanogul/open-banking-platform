package dev.obe.mockhhs.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/** Allows the cancel codes the standard lists for a failure after the customer logged in. */
public record RejectAuthenticationRequest(@NotNull @Pattern(regexp = "0[7-9]|1[0-4]|99") String cancelCode) {
}
