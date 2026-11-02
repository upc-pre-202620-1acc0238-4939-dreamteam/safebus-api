package com.dreamteam.safebus.safetycase.application;

import java.time.Instant;

public record RegisterPassengerRequestResult(String requestId, String status, Instant receivedAt,
                                             String groupStatus, Integer contributorCount,
                                             boolean duplicate) {
}
