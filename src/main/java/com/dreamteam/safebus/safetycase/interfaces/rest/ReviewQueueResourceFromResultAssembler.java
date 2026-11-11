package com.dreamteam.safebus.safetycase.interfaces.rest;

import com.dreamteam.safebus.safetycase.application.GetReviewQueueResult;

public final class ReviewQueueResourceFromResultAssembler {

    static final String PHOTO_PATH = "/api/v1/passenger-requests/%s/photo";

    private ReviewQueueResourceFromResultAssembler() {}

    public static ReviewQueueResource toResource(GetReviewQueueResult result) {
        return new ReviewQueueResource(result.generatedAt(),
            result.driverEmergencies().stream().map(ReviewQueueResourceFromResultAssembler::toResource).toList(),
            result.passengerEmergencies().stream().map(ReviewQueueResourceFromResultAssembler::toResource).toList(),
            result.groupsAwaitingApproval().stream().map(ReviewQueueResourceFromResultAssembler::toResource).toList());
    }

    private static ReviewQueueResource.EmergencyResource toResource(GetReviewQueueResult.EmergencyItem e) {
        return new ReviewQueueResource.EmergencyResource(e.id(), e.groupId(), e.status(), e.priority(),
            e.busId(), e.shiftId(), e.routeId(), e.driverId(), e.latitude(), e.longitude(), e.activatedAt(),
            e.receivedAt(), e.responsibleSupervisorId(), e.attentionStartedAt());
    }

    private static ReviewQueueResource.GroupResource toResource(GetReviewQueueResult.GroupItem g) {
        return new ReviewQueueResource.GroupResource(g.groupId(), g.reference(), g.busId(), g.shiftId(), g.windowStartedAt(),
            g.awaitingSince(), g.contributorCount(), g.requestCount(),
            g.requests().stream().map(r -> new ReviewQueueResource.RequestResource(r.requestId(), r.message(),
                r.activatedAt(), r.receivedAt(), r.status(), PHOTO_PATH.formatted(r.requestId()))).toList());
    }
}
