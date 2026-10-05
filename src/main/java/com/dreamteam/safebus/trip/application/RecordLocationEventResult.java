package com.dreamteam.safebus.trip.application;

import java.time.Instant;

public record RecordLocationEventResult(
    String eventId,
    Long shiftId,
    Long busId,
    Instant receivedAt,
    boolean currentPositionUpdated,
    boolean duplicate
) {}
