package com.dreamteam.safebus.trip.interfaces.rest;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.time.Instant;

public record InitializeOccupancyRequest(

    @NotNull
    @Pattern(regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$",
             message = "initializationId must be a valid UUID")
    String initializationId,

    @NotNull
    Long busId,

    @NotNull
    Long shiftId,

    @NotNull
    Integer confirmedCount,

    @NotNull
    Instant confirmedAt
) {}
