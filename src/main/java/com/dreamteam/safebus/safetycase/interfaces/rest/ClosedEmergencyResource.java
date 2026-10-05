package com.dreamteam.safebus.safetycase.interfaces.rest;

import java.time.Instant;

public record ClosedEmergencyResource(
    String id,
    String status,
    Instant closedAt,
    String outcome
) {}
