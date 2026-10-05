package com.dreamteam.safebus.safetycase.application;

import java.time.Instant;

public record CloseEmergencyResult(
    String id,
    String status,
    Instant closedAt,
    String outcome
) {}
