package com.dreamteam.safebus.safetycase.application;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/** Announces that a case was committed; the relay forwards it once the surrounding transaction (if any) commits. */
@Component
public class SupervisorNotifier {

    private final ApplicationEventPublisher publisher;

    public SupervisorNotifier(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    public void driverEmergencyCreated(String emergencyId) {
        publisher.publishEvent(SupervisorNotificationRequested.driverEmergency(emergencyId));
    }

    public void groupReviewRequired(Long groupId) {
        publisher.publishEvent(SupervisorNotificationRequested.groupReviewRequired(groupId));
    }

    public void groupApproved(Long groupId, String emergencyId) {
        publisher.publishEvent(SupervisorNotificationRequested.groupApproved(groupId, emergencyId));
    }
}
