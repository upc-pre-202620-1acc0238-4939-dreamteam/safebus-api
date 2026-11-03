package com.dreamteam.safebus.safetycase.interfaces.rest;

import java.time.Instant;

public record MyPassengerRequestResource(
    String requestId,
    String message,
    String evidenceReference,
    Instant submittedAt,
    String groupReference,
    Integer contributorCount,
    String status,
    Decision decision
) {
    public record Decision(String result, Instant decidedAt, String response) {}
}
