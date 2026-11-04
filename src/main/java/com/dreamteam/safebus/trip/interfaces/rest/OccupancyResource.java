package com.dreamteam.safebus.trip.interfaces.rest;

import com.dreamteam.safebus.trip.domain.model.OccupancyLoad;
import com.dreamteam.safebus.trip.domain.model.OccupancyStatus;
import com.dreamteam.safebus.trip.domain.model.UnavailableReason;

import java.time.Instant;

public record OccupancyResource(Long busId, OccupancyStatus status, UnavailableReason reason, Integer count,
                                Integer capacity, OccupancyLoad load, Instant lastCountAt,
                                Instant lastHeartbeatAt, Instant generatedAt) {}
