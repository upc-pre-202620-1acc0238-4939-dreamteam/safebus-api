package com.dreamteam.safebus.trip.interfaces.rest;

import com.dreamteam.safebus.trip.domain.model.OccupancyEventType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.time.Instant;

public record RecordOccupancyEventRequest(

    @NotNull
    @Pattern(regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$",
             message = "eventId must be a valid UUID")
    String eventId,

    @NotNull
    Long busId,

    @NotNull
    Long shiftId,

    @NotNull
    @Min(1)
    Long sequence,

    @NotNull
    OccupancyEventType type,

    @NotNull
    Instant capturedAt
) {}
