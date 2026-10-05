package com.dreamteam.safebus.safetycase.application;

import java.time.Instant;

public record GetDriverEmergencyResult(
    String id,
    String status,
    Instant activatedAt,
    Instant receivedAt,
    Instant attentionStartedAt,
    Instant closedAt,
    String userResponse
) {}
