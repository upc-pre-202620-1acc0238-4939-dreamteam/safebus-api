package com.dreamteam.safebus.contact.interfaces.rest;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record RegisterContactRequestRequest(
    @NotBlank
    @Pattern(regexp = "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}",
             message = "must be a valid UUID")
    String submissionId,

    @NotBlank
    String companyName,

    @NotBlank
    String contactName,

    @NotBlank
    String email,

    @NotNull
    Boolean consent
) {}
