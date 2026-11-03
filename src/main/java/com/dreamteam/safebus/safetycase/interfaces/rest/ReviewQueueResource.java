package com.dreamteam.safebus.safetycase.interfaces.rest;

import java.time.Instant;
import java.util.List;

public record ReviewQueueResource(
    Instant generatedAt,
    List<EmergencyResource> driverEmergencies,
    List<EmergencyResource> passengerEmergencies,
    List<GroupResource> groupsAwaitingApproval
) {

    public record EmergencyResource(String id, Long groupId, String status, String priority, Long busId,
                                    Long shiftId, Long routeId, Long driverId, Double latitude,
                                    Double longitude, Instant activatedAt, Instant receivedAt,
                                    Long responsibleSupervisorId, Instant attentionStartedAt) {}

    public record GroupResource(Long groupId, Long busId, Long shiftId, Instant windowStartedAt,
                                Instant awaitingSince, int contributorCount, int requestCount,
                                List<RequestResource> requests) {}

    public record RequestResource(String requestId, String message, Instant activatedAt, Instant receivedAt,
                                  String status, String photoPath) {}
}
