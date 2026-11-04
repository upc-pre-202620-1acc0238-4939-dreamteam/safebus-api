package com.dreamteam.safebus.trip.application;

import com.dreamteam.safebus.trip.domain.model.OccupancyEventType;

import java.time.Instant;

public record RecordOccupancyEventCommand(String eventId, Long busId, Long shiftId, long sequence,
                                          OccupancyEventType type, Instant capturedAt) {}
