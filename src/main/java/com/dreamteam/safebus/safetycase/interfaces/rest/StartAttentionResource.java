package com.dreamteam.safebus.safetycase.interfaces.rest;

import java.time.Instant;

public record StartAttentionResource(
    String id,
    String status,
    Long responsibleSupervisorId,
    Instant attentionStartedAt
) {}
