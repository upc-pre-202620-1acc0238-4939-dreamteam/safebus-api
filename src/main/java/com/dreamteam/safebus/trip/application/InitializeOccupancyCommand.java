package com.dreamteam.safebus.trip.application;

import java.time.Instant;

public record InitializeOccupancyCommand(String initializationId, Long busId, Long shiftId,
                                         int confirmedCount, Instant confirmedAt) {}
