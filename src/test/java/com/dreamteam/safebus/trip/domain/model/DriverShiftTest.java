package com.dreamteam.safebus.trip.domain.model;

import com.dreamteam.safebus.shared.domain.model.GeoPoint;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNull;

class DriverShiftTest {

    private static final Instant T1 = Instant.parse("2025-07-01T08:00:00Z");
    private static final Clock FIXED = Clock.fixed(T1, ZoneOffset.UTC);

    @Test
    void start_setsAllFieldsCorrectly() {
        DriverShift ds = DriverShift.start(10L, 20L, 30L, 40L, FIXED);

        assertEquals(10L, ds.getAssignmentId());
        assertEquals(20L, ds.getDriverId());
        assertEquals(30L, ds.getBusId());
        assertEquals(40L, ds.getRouteId());
        assertEquals(ShiftStatus.ACTIVE, ds.getStatus());
        assertEquals(T1, ds.getStartedAt());
        assertNull(ds.getClosedAt());
    }

    @Test
    void close_activeShift_closesWithTimeAndLastKnownPosition() {
        DriverShift ds = DriverShift.start(10L, 20L, 30L, 40L, FIXED);
        GeoPoint point = new GeoPoint(-12.046, -77.042);
        Instant capturedAt = T1.plusSeconds(60);
        Instant closeAt = Instant.parse("2025-07-01T16:00:00.123456789Z");

        assertTrue(ds.close(closeAt, point, capturedAt));

        assertEquals(ShiftStatus.CLOSED, ds.getStatus());
        assertEquals(Instant.parse("2025-07-01T16:00:00.123Z"), ds.getClosedAt());
        assertSame(point, ds.getLastKnownPoint());
        assertEquals(capturedAt, ds.getLastKnownCapturedAt());
    }

    @Test
    void close_withoutLastPosition_closesWithNullPosition() {
        DriverShift ds = DriverShift.start(10L, 20L, 30L, 40L, FIXED);

        assertTrue(ds.close(T1.plusSeconds(3600), null, null));

        assertEquals(ShiftStatus.CLOSED, ds.getStatus());
        assertEquals(T1.plusSeconds(3600), ds.getClosedAt());
        assertNull(ds.getLastKnownPoint());
        assertNull(ds.getLastKnownCapturedAt());
    }

    @Test
    void close_alreadyClosed_returnsFalseAndChangesNothing() {
        DriverShift ds = DriverShift.start(10L, 20L, 30L, 40L, FIXED);
        GeoPoint first = new GeoPoint(-12.0, -77.0);
        Instant firstCapturedAt = T1.plusSeconds(10);
        ds.close(T1.plusSeconds(100), first, firstCapturedAt);

        boolean again = ds.close(T1.plusSeconds(200), new GeoPoint(1.0, 2.0), T1.plusSeconds(150));

        assertFalse(again);
        assertEquals(ShiftStatus.CLOSED, ds.getStatus());
        assertEquals(T1.plusSeconds(100), ds.getClosedAt());
        assertSame(first, ds.getLastKnownPoint());
        assertEquals(firstCapturedAt, ds.getLastKnownCapturedAt());
    }
}
