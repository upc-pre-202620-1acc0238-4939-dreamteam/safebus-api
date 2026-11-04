package com.dreamteam.safebus.trip.interfaces.rest;

import java.time.Instant;

public record OccupancyInitializationResource(String initializationId, Long busId, Long shiftId,
                                              int confirmedCount, Instant confirmedAt) {}
