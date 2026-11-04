package com.dreamteam.safebus.trip.interfaces.rest;

import com.dreamteam.safebus.trip.domain.model.OccupancyEventType;
import com.dreamteam.safebus.trip.domain.model.OccupancyLoad;

public record OccupancyEventResource(String eventId, long sequence, OccupancyEventType type,
                                     int count, OccupancyLoad load) {}
