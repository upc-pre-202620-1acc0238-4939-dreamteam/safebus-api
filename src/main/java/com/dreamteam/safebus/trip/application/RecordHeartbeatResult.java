package com.dreamteam.safebus.trip.application;

import java.time.Instant;

public record RecordHeartbeatResult(Long busId, Instant receivedAt) {}
