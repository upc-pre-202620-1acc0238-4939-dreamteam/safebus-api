package com.dreamteam.safebus.trip.application;

import java.time.Instant;

public record BusLocationResult(
    Long busId,
    double latitude,
    double longitude,
    Instant capturedAt,
    double accuracyMeters
) {}
