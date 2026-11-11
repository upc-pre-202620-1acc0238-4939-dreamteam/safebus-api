package com.dreamteam.safebus.safetycase.application;

import java.time.Instant;

public record MyPassengerRequestResult(
    String requestId,
    String message,
    String evidenceReference,
    Instant submittedAt,
    String groupReference,
    Integer contributorCount,
    PassengerRequestProgress status,
    Decision decision
) {
    public record Decision(String result, Instant decidedAt, String response) {}
}
