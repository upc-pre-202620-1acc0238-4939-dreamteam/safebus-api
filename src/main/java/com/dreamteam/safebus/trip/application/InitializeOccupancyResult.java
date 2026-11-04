package com.dreamteam.safebus.trip.application;

import java.time.Instant;

public record InitializeOccupancyResult(String initializationId, Long busId, Long shiftId,
                                        int confirmedCount, Instant confirmedAt, boolean duplicate) {}
