package com.dreamteam.safebus.trip.interfaces.rest;

import java.time.Instant;

public record CountingSourceResource(Long sourceId, Long busId, String label, String status,
                                     Instant createdAt, Instant rotatedAt, Instant revokedAt) {}
