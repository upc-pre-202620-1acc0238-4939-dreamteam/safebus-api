package com.dreamteam.safebus.safetycase.application;

import java.time.Instant;

public record CreateDriverEmergencyCommand(
    String id,
    Long shiftId,
    Instant activatedAt,
    Double latitude,
    Double longitude
) {}
