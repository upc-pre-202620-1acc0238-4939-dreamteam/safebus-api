package com.dreamteam.safebus.trip.application;

import com.dreamteam.safebus.trip.domain.model.OccupancyEventType;
import com.dreamteam.safebus.trip.domain.model.OccupancyLoad;

public record RecordOccupancyEventResult(String eventId, long sequence, OccupancyEventType type,
                                         int count, OccupancyLoad load, boolean duplicate) {}
