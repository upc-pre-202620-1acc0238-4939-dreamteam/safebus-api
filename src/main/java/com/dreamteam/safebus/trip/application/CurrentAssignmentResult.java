package com.dreamteam.safebus.trip.application;

import java.time.Instant;

public record CurrentAssignmentResult(Long assignmentId, String status, String busPlate,
                                      String routeName, String origin, String destination,
                                      Instant plannedStart, Instant plannedEnd) {}
