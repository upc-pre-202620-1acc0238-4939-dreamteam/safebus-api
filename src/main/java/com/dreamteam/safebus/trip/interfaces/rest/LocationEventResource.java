package com.dreamteam.safebus.trip.interfaces.rest;

import java.time.Instant;

public record LocationEventResource(
    String eventId,
    Long shiftId,
    Long busId,
    Instant receivedAt,
    boolean currentPositionUpdated
) {}
