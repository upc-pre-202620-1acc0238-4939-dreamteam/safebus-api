package com.dreamteam.safebus.fleet.interfaces.rest;

import java.time.Instant;

public record BusCapacityResource(
    Long busId,
    Integer capacity,
    String technicalRecordReference,
    Long updatedByUserId,
    Instant updatedAt
) {}
