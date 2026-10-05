package com.dreamteam.safebus.trip.interfaces.rest;

import com.dreamteam.safebus.trip.domain.model.ShiftStatus;

import java.time.Instant;

public record CloseShiftResource(
    Long shiftId,
    ShiftStatus status,
    Instant closedAt
) {}
