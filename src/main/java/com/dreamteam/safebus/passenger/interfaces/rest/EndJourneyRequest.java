package com.dreamteam.safebus.passenger.interfaces.rest;

import java.time.Instant;

public record EndJourneyRequest(String reason, Instant endedAt, Summary summary) {
    public record Summary(Integer separationDistanceMeters, Integer separationDurationSeconds) {}
}
