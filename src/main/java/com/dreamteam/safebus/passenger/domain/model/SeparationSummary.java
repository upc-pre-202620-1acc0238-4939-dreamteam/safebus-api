package com.dreamteam.safebus.passenger.domain.model;

import com.dreamteam.safebus.shared.domain.exceptions.RuleViolationException;

public record SeparationSummary(int distanceMeters, int durationSeconds) {

    public SeparationSummary {
        if (distanceMeters <= 0 || durationSeconds <= 0) {
            throw new RuleViolationException("INVALID_END_SUMMARY",
                "separation distance and duration must be greater than zero");
        }
    }

    public static SeparationSummary ofNullable(Integer distanceMeters, Integer durationSeconds) {
        if (distanceMeters == null && durationSeconds == null) {
            return null;
        }
        if (distanceMeters == null || durationSeconds == null) {
            throw new RuleViolationException("INVALID_END_SUMMARY",
                "separation distance and duration must be provided together");
        }
        return new SeparationSummary(distanceMeters, durationSeconds);
    }
}
