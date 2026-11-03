package com.dreamteam.safebus.safetycase.application;

public enum PassengerRequestProgress {
    LATE("Late"),
    COLLECTING("Collecting requests"),
    AWAITING_APPROVAL("Awaiting company approval"),
    EXPIRED("Expired"),
    NOT_APPROVED("Not approved"),
    ACTIVE("Active"),
    IN_PROGRESS("In progress"),
    CLOSED("Closed");

    private final String label;

    PassengerRequestProgress(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
