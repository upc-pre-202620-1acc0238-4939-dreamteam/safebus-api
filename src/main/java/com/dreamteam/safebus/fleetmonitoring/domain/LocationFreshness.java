package com.dreamteam.safebus.fleetmonitoring.domain;

import java.time.Duration;
import java.time.Instant;

public final class LocationFreshness {

    public static final Duration MAX_AGE = Duration.ofMinutes(3);

    private LocationFreshness() {}

    public static LocationStatus classify(Instant capturedAt, Instant now) {
        if (capturedAt == null) {
            return LocationStatus.UNAVAILABLE;
        }
        if (Duration.between(capturedAt, now).compareTo(MAX_AGE) > 0) {
            return LocationStatus.STALE;
        }
        return LocationStatus.CURRENT;
    }
}
