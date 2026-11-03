package com.dreamteam.safebus.safetycase.interfaces.rest;

import java.time.Instant;

public record ApprovedGroupResource(
    Long groupId,
    String emergencyId,
    String status,
    String priority,
    Instant approvedAt
) {}
