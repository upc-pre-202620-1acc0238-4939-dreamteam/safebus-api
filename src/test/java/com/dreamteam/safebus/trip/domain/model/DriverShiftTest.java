package com.dreamteam.safebus.trip.domain.model;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
}
