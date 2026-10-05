package com.dreamteam.safebus.safetycase.interfaces.rest;

import java.time.Instant;

public record DriverEmergencyCreatedResource(
    String id,
    String status,
    String priority,
    Instant activatedAt,
    Instant receivedAt
) {}
