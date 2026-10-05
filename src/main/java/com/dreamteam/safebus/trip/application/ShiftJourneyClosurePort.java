package com.dreamteam.safebus.trip.application;

import java.time.Instant;

public interface ShiftJourneyClosurePort {
    int endJourneysOfShift(Long shiftId, Instant now);
}
