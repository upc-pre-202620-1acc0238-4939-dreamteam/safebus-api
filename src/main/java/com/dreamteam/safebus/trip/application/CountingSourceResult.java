package com.dreamteam.safebus.trip.application;

import java.time.Instant;

public record CountingSourceResult(Long sourceId, Long busId, String label, String status,
                                   Instant createdAt, Instant rotatedAt, Instant revokedAt) {}
