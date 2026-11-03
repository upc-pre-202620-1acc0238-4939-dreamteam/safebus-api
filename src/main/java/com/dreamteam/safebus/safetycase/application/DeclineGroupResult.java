package com.dreamteam.safebus.safetycase.application;

import java.time.Instant;

public record DeclineGroupResult(Long groupId, String status, Instant decidedAt, String reason,
                                 String response) {
}
