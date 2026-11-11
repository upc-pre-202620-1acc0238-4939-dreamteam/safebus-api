package com.dreamteam.safebus.passenger.application;

import java.time.Instant;

public record EndJourneyCommand(Long journeyId, Long userAccountId, String reason, Instant endedAt,
                                Integer separationDistanceMeters, Integer separationDurationSeconds) {}
