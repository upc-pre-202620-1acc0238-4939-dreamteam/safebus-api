package com.dreamteam.safebus.fleetmonitoring.domain;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LocationFreshnessTest {

    private static final Instant NOW = Instant.parse("2030-03-01T12:00:00Z");

    @Test
    void maxAge_isThreeMinutes() {
        assertEquals(Duration.ofMinutes(3), LocationFreshness.MAX_AGE);
    }

    @Test
    void absentCapture_isUnavailable() {
        assertEquals(LocationStatus.UNAVAILABLE, LocationFreshness.classify(null, NOW));
    }

    @Test
    void capturedNow_isCurrent() {
        assertEquals(LocationStatus.CURRENT, LocationFreshness.classify(NOW, NOW));
    }

    @Test
    void twoMinutesFiftyNineSecondsOld_isCurrent() {
        assertEquals(LocationStatus.CURRENT,
            LocationFreshness.classify(NOW.minusSeconds(179), NOW));
    }

    @Test
    void exactlyThreeMinutesOld_isCurrent() {
        assertEquals(LocationStatus.CURRENT,
            LocationFreshness.classify(NOW.minusSeconds(180), NOW));
    }

    @Test
    void threeMinutesAndOneMillisecondOld_isStale() {
        assertEquals(LocationStatus.STALE,
            LocationFreshness.classify(NOW.minusSeconds(180).minusMillis(1), NOW));
    }

    @Test
    void tenMinutesOld_isStale() {
        assertEquals(LocationStatus.STALE,
            LocationFreshness.classify(NOW.minus(Duration.ofMinutes(10)), NOW));
    }

    @Test
    void capturedInTheFuture_isCurrent() {
        assertEquals(LocationStatus.CURRENT,
            LocationFreshness.classify(NOW.plusSeconds(30), NOW));
    }
}
