package com.dreamteam.safebus.trip.interfaces.rest;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.time.Instant;

public record RecordLocationEventRequest(

    @NotBlank
    @Pattern(regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$",
             message = "eventId must be a valid UUID")
    String eventId,

    @NotNull
    Long shiftId,

    Long busId,

    @NotNull
    Instant capturedAt,

    @NotNull
    Double accuracyMeters,

    @NotNull
    Double latitude,

    @NotNull
    Double longitude
) {}
