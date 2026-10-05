package com.dreamteam.safebus.trip.interfaces.rest;

import com.dreamteam.safebus.trip.domain.model.ShiftStatus;

import java.time.Instant;

public record ActivateShiftResource(
    Long id,
    Long assignmentId,
    Long driverId,
    Long busId,
    Long routeId,
    ShiftStatus status,
    Instant startedAt
) {}
