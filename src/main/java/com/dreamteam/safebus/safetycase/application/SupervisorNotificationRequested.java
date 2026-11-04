package com.dreamteam.safebus.safetycase.application;

public record SupervisorNotificationRequested(Kind kind, String emergencyId, Long groupId) {

    public enum Kind { DRIVER_EMERGENCY }

    public static SupervisorNotificationRequested driverEmergency(String emergencyId) {
        return new SupervisorNotificationRequested(Kind.DRIVER_EMERGENCY, emergencyId, null);
    }
}
