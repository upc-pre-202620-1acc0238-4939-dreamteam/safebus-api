package com.dreamteam.safebus.trip.interfaces.rest;

import java.time.Instant;

public record CurrentAssignmentResource(
    Long assignmentId,
    String status,
    String busPlate,
    String routeName,
    String origin,
    String destination,
    Instant plannedStart,
    Instant plannedEnd
) {}
