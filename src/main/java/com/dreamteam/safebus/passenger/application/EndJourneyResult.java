package com.dreamteam.safebus.passenger.application;

import com.dreamteam.safebus.passenger.domain.model.JourneyEndReason;
import com.dreamteam.safebus.passenger.domain.model.JourneyStatus;

import java.time.Instant;

public record EndJourneyResult(Long journeyId, JourneyStatus status, Instant endedAt, JourneyEndReason endReason) {}
