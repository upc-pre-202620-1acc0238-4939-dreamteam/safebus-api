package com.dreamteam.safebus.trip.application;

import java.time.Instant;

public record RecordLocationEventCommand(
    String eventId,
    Long shiftId,
    Long busId,
    Instant capturedAt,
    double accuracyMeters,
    double latitude,
    double longitude
) {}
