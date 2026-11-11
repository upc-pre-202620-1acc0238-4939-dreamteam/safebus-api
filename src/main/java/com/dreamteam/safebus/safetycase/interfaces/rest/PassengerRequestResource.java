package com.dreamteam.safebus.safetycase.interfaces.rest;

import java.time.Instant;

public record PassengerRequestResource(
    String requestId,
    String status,
    Instant receivedAt,
    String groupStatus,
    Integer contributorCount
) {}
