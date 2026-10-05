package com.dreamteam.safebus.passenger.application;

import com.dreamteam.safebus.passenger.domain.model.JourneyStatus;

import java.time.Instant;

public record StartJourneyResult(
    Long journeyId, boolean created, JourneyStatus status, Instant startedAt,
    String plate, String companyName, boolean companyValidated,
    String routeName, String origin, String destination, String driverPublicName
) {}
