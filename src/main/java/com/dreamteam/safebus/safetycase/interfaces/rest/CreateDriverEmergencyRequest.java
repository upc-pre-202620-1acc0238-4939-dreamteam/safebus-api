package com.dreamteam.safebus.safetycase.interfaces.rest;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.time.Instant;

public record CreateDriverEmergencyRequest(
    @NotBlank
    @Pattern(regexp = "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}",
             message = "must be a valid UUID")
    String id,

    @NotNull
    Long shiftId,

    @NotNull
    Instant activatedAt,

    Double latitude,
    Double longitude
) {}
