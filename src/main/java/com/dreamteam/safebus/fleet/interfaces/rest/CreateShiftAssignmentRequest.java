package com.dreamteam.safebus.fleet.interfaces.rest;

import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record CreateShiftAssignmentRequest(
    @NotNull Long driverId,
    @NotNull Long busId,
    @NotNull Long routeId,
    @NotNull Instant plannedStart,
    @NotNull Instant plannedEnd
) {}
