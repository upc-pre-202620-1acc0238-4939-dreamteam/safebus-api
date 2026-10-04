package com.dreamteam.safebus.fleet.interfaces.rest;

import com.dreamteam.safebus.fleet.domain.model.AssignmentStatus;

import java.time.Instant;

public record ShiftAssignmentResource(
    Long id,
    Long driverId,
    Long busId,
    Long routeId,
    Instant plannedStart,
    Instant plannedEnd,
    AssignmentStatus status,
    Long createdByUserId,
    Instant createdAt
) {}
