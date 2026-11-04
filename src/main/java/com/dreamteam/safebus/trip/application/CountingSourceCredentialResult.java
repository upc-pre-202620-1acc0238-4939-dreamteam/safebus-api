package com.dreamteam.safebus.trip.application;

import java.time.Instant;

// Carries the raw key, which exists only in the response to a registration or a rotation
public record CountingSourceCredentialResult(Long sourceId, Long busId, String label, String status,
                                             Instant createdAt, Instant rotatedAt, String rawKey) {

    @Override
    public String toString() {
        return "CountingSourceCredentialResult[sourceId=" + sourceId + ", busId=" + busId
            + ", status=" + status + ", rawKey=***]";
    }
}
