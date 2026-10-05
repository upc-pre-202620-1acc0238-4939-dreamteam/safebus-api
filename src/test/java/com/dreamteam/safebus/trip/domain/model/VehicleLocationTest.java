package com.dreamteam.safebus.trip.domain.model;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

class VehicleLocationTest {

    private static final Clock FIXED =
        Clock.fixed(Instant.parse("2030-01-01T10:00:00Z"), ZoneOffset.UTC);

    private LocationEvent event(String id, Instant capturedAt) {
        return LocationEvent.create(id, 1L, 10L, -12.046, -77.042, 5.0, capturedAt, FIXED);
    }

    @Test
    void applyIfNewer_firstSample_returnsTrue() {
        VehicleLocation vl = VehicleLocation.empty(10L);
        LocationEvent e = event("evt-1", Instant.parse("2030-01-01T09:00:00Z"));

        boolean applied = vl.applyIfNewer(e, FIXED);

        assertTrue(applied);
        assertEquals(e.getCapturedAt(), vl.getCapturedAt());
        assertEquals("evt-1", vl.getLastEventId());
        assertNotNull(vl.getPoint());
    }

    @Test
    void applyIfNewer_newerEvent_returnsTrue() {
        VehicleLocation vl = VehicleLocation.empty(10L);
        vl.applyIfNewer(event("evt-1", Instant.parse("2030-01-01T08:00:00Z")), FIXED);

        LocationEvent newer = event("evt-2", Instant.parse("2030-01-01T09:00:00Z"));
        boolean applied = vl.applyIfNewer(newer, FIXED);

        assertTrue(applied);
        assertEquals(newer.getCapturedAt(), vl.getCapturedAt());
        assertEquals("evt-2", vl.getLastEventId());
    }

    @Test
    void applyIfNewer_olderEvent_returnsFalse() {
        VehicleLocation vl = VehicleLocation.empty(10L);
        LocationEvent first = event("evt-1", Instant.parse("2030-01-01T09:00:00Z"));
        vl.applyIfNewer(first, FIXED);

        LocationEvent older = event("evt-2", Instant.parse("2030-01-01T08:00:00Z"));
        boolean applied = vl.applyIfNewer(older, FIXED);

        assertFalse(applied);
        assertEquals(first.getCapturedAt(), vl.getCapturedAt());
        assertEquals("evt-1", vl.getLastEventId());
    }

    @Test
    void applyIfNewer_equalCapturedAt_returnsFalse() {
        VehicleLocation vl = VehicleLocation.empty(10L);
        Instant t = Instant.parse("2030-01-01T09:00:00Z");
        vl.applyIfNewer(event("evt-1", t), FIXED);

        boolean applied = vl.applyIfNewer(event("evt-2", t), FIXED);

        assertFalse(applied);
        assertEquals("evt-1", vl.getLastEventId());
    }
}
