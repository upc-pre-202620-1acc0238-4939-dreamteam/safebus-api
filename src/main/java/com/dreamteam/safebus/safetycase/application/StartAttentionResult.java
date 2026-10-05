package com.dreamteam.safebus.safetycase.application;

import java.time.Instant;

public record StartAttentionResult(
    String id,
    String status,
    Long responsibleSupervisorId,
    Instant attentionStartedAt
) {}
