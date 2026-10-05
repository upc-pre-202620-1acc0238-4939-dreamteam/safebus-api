package com.dreamteam.safebus.passenger.interfaces.rest;

import java.time.Instant;

public record EndJourneyResource(Long journeyId, String status, Instant endedAt, String endReason) {}
