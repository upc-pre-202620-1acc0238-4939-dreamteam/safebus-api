package com.dreamteam.safebus.trip.domain.model;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.*;

class LocationEventTest {

    private static final Clock FIXED =
        Clock.fixed(Instant.parse("2030-01-01T10:00:00Z"), ZoneOffset.UTC);

    private LocationEvent event(Instant capturedAt) {
        return LocationEvent.create("evt-uuid-001", 1L, 10L,
            -12.046, -77.042, 5.0, capturedAt, FIXED);
    }

    @Test
    void capturedAt_truncatedToMilliseconds() {
        Instant withNanos = Instant.parse("2030-01-01T08:00:00.123456789Z");
        LocationEvent e = event(withNanos);
        assertEquals(withNanos.truncatedTo(ChronoUnit.MILLIS), e.getCapturedAt());
        assertNotEquals(withNanos, e.getCapturedAt());
    }

    @Test
    void hasSamePayloadAs_identicalPayload_returnsTrue() {
        Instant t = Instant.parse("2030-01-01T08:00:00.123Z");
        LocationEvent e = event(t);
        assertTrue(e.hasSamePayloadAs(1L, t, 5.0, -12.046, -77.042));
    }

    @Test
    void hasSamePayloadAs_capturedAtDiffersByNanosOnly_returnsTrue() {
        Instant base = Instant.parse("2030-01-01T08:00:00.123Z");
        Instant withNanos = base.plusNanos(456);
        LocationEvent e = event(base);
        // Both truncate to the same millis, so payload is identical
        assertTrue(e.hasSamePayloadAs(1L, withNanos, 5.0, -12.046, -77.042));
    }

    @Test
    void hasSamePayloadAs_differentShift_returnsFalse() {
        Instant t = Instant.parse("2030-01-01T08:00:00Z");
        LocationEvent e = event(t);
        assertFalse(e.hasSamePayloadAs(2L, t, 5.0, -12.046, -77.042));
    }

    @Test
    void hasSamePayloadAs_differentCapturedAt_returnsFalse() {
        Instant t = Instant.parse("2030-01-01T08:00:00Z");
        LocationEvent e = event(t);
        assertFalse(e.hasSamePayloadAs(1L, t.plusSeconds(1), 5.0, -12.046, -77.042));
    }

    @Test
    void hasSamePayloadAs_differentAccuracy_returnsFalse() {
        Instant t = Instant.parse("2030-01-01T08:00:00Z");
        LocationEvent e = event(t);
        assertFalse(e.hasSamePayloadAs(1L, t, 10.0, -12.046, -77.042));
    }

    @Test
    void hasSamePayloadAs_differentLatitude_returnsFalse() {
        Instant t = Instant.parse("2030-01-01T08:00:00Z");
        LocationEvent e = event(t);
        assertFalse(e.hasSamePayloadAs(1L, t, 5.0, -11.0, -77.042));
    }

    @Test
    void hasSamePayloadAs_differentLongitude_returnsFalse() {
        Instant t = Instant.parse("2030-01-01T08:00:00Z");
        LocationEvent e = event(t);
        assertFalse(e.hasSamePayloadAs(1L, t, 5.0, -12.046, -76.0));
    }
}
