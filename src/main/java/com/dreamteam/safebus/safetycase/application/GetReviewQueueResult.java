package com.dreamteam.safebus.safetycase.application;

import java.time.Instant;
import java.util.List;

public record GetReviewQueueResult(
    Instant generatedAt,
    List<EmergencyItem> driverEmergencies,
    List<EmergencyItem> passengerEmergencies,
    List<GroupItem> groupsAwaitingApproval
) {

    public record EmergencyItem(String id, Long groupId, String status, String priority, Long busId,
                                Long shiftId, Long routeId, Long driverId, Double latitude, Double longitude,
                                Instant activatedAt, Instant receivedAt, Long responsibleSupervisorId,
                                Instant attentionStartedAt) {}

    public record GroupItem(Long groupId, String reference, Long busId, Long shiftId, Instant windowStartedAt,
                            Instant awaitingSince, int contributorCount, int requestCount,
                            List<RequestItem> requests) {}

    public record RequestItem(String requestId, String message, Instant activatedAt, Instant receivedAt,
                              String status) {}
}
