package com.dreamteam.safebus.trip.interfaces.rest;

import java.time.Instant;

public record OccupancyHeartbeatResource(Long busId, Instant receivedAt) {}
