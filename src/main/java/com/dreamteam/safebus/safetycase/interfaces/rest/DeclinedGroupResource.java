package com.dreamteam.safebus.safetycase.interfaces.rest;

import java.time.Instant;

public record DeclinedGroupResource(
    Long groupId,
    String status,
    Instant decidedAt,
    String reason,
    String response
) {}
