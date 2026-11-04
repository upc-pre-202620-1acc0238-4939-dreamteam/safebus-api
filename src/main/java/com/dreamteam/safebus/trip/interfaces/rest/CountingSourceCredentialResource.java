package com.dreamteam.safebus.trip.interfaces.rest;

import java.time.Instant;

// The key is shown here once and never again
public record CountingSourceCredentialResource(Long sourceId, Long busId, String label, String status,
                                               Instant createdAt, Instant rotatedAt, String key) {

    @Override
    public String toString() {
        return "CountingSourceCredentialResource[sourceId=" + sourceId + ", busId=" + busId
            + ", status=" + status + ", key=***]";
    }
}
