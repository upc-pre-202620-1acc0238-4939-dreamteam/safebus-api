package com.dreamteam.safebus.safetycase.domain.model;

public enum GroupStatus {
    COLLECTING, AWAITING_APPROVAL, APPROVED, NOT_APPROVED, EXPIRED, CLOSED;

    public boolean isOpen() {
        return this == COLLECTING || this == AWAITING_APPROVAL || this == APPROVED;
    }
}
