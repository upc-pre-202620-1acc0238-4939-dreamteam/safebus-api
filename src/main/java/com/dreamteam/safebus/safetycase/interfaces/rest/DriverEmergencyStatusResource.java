package com.dreamteam.safebus.safetycase.interfaces.rest;

import java.time.Instant;

public record DriverEmergencyStatusResource(
    String id,
    String status,
    Instant activatedAt,
    Instant receivedAt,
    Instant attentionStartedAt,
    Instant closedAt,
    String response
) {}
