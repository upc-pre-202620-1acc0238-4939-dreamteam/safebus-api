package com.dreamteam.safebus.fleet.application;

import java.time.Instant;

public record CreateShiftAssignmentCommand(
    Long driverId,
    Long busId,
    Long routeId,
    Instant plannedStart,
    Instant plannedEnd
) {}
