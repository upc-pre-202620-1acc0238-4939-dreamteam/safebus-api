package com.dreamteam.safebus.trip.interfaces.rest;

import java.time.Instant;

public record VehicleLocationResource(
    Long busId,
    double latitude,
    double longitude,
    Instant capturedAt,
    double accuracyMeters
) {}
