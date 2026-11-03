package com.dreamteam.safebus.safetycase.application;

import com.dreamteam.safebus.safetycase.domain.model.Emergency;
import com.dreamteam.safebus.safetycase.domain.model.PassengerRequestGroup;

import java.time.Instant;
import java.util.Optional;

final class GroupProgress {

    private GroupProgress() {}

    // The expiry job runs every 30 seconds, so an overdue collecting group is reported as expired already.
    static PassengerRequestProgress of(PassengerRequestGroup group, Optional<Emergency> emergency, Instant now) {
        if (emergency.isPresent()) {
            return switch (emergency.get().getStatus()) {
                case ACTIVE -> PassengerRequestProgress.ACTIVE;
                case IN_PROGRESS -> PassengerRequestProgress.IN_PROGRESS;
                case CLOSED -> PassengerRequestProgress.CLOSED;
            };
        }
        return switch (group.getStatus()) {
            case COLLECTING -> group.isWindowOverdue(now)
                ? PassengerRequestProgress.EXPIRED : PassengerRequestProgress.COLLECTING;
            case AWAITING_APPROVAL -> PassengerRequestProgress.AWAITING_APPROVAL;
            case EXPIRED -> PassengerRequestProgress.EXPIRED;
            case NOT_APPROVED -> PassengerRequestProgress.NOT_APPROVED;
            case APPROVED -> PassengerRequestProgress.ACTIVE;
            case CLOSED -> PassengerRequestProgress.CLOSED;
        };
    }
}
