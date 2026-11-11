package com.dreamteam.safebus.safetycase.application;

public record SupervisorNotificationRequested(Kind kind, String emergencyId, Long groupId) {

    public enum Kind { DRIVER_EMERGENCY, GROUP_REVIEW_REQUIRED, GROUP_APPROVED }

    public static SupervisorNotificationRequested driverEmergency(String emergencyId) {
        return new SupervisorNotificationRequested(Kind.DRIVER_EMERGENCY, emergencyId, null);
    }

    public static SupervisorNotificationRequested groupReviewRequired(Long groupId) {
        return new SupervisorNotificationRequested(Kind.GROUP_REVIEW_REQUIRED, null, groupId);
    }

    public static SupervisorNotificationRequested groupApproved(Long groupId, String emergencyId) {
        return new SupervisorNotificationRequested(Kind.GROUP_APPROVED, emergencyId, groupId);
    }
}
