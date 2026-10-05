package com.dreamteam.safebus.safetycase.application;

import java.time.Instant;

public record CreateDriverEmergencyResult(
    String id,
    String status,
    String priority,
    Instant activatedAt,
    Instant receivedAt,
    boolean duplicate
) {}
