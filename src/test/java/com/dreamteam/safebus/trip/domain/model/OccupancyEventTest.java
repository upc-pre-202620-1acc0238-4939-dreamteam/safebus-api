package com.dreamteam.safebus.trip.domain.model;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OccupancyEventTest {

    private static final Instant NOW = Instant.parse("2030-01-01T10:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private OccupancyEvent event() {
        BusOccupancy o = BusOccupancy.create(1L, 2L, 3L);
        o.initialize("init", 7L, 0, NOW, NOW);
        Instant captured = NOW.minusSeconds(3);
        o.apply(7L, OccupancyEventType.ENTRY, 1, captured);
        return OccupancyEvent.applied("evt-1", o, OccupancyEventType.ENTRY, 1, captured, CLOCK);
    }

    @Test
    void recordsTheStateAfterTheEventAndTheServerReceiveTime() {
        OccupancyEvent e = event();

        assertEquals(1, e.getResultingCount());
        assertEquals(NOW, e.getReceivedAt());
        assertEquals(1, e.getEpoch());
        assertTrue(e.isNew());
    }

    @Test
    void identicalPayloadMatches() {
        assertTrue(event().hasSamePayload(2L, 7L, OccupancyEventType.ENTRY, 1, NOW.minusSeconds(3)));
    }

    @Test
    void anyDifferentFieldDoesNotMatch() {
        OccupancyEvent e = event();

        assertFalse(e.hasSamePayload(2L, 7L, OccupancyEventType.EXIT, 1, NOW.minusSeconds(3)));
        assertFalse(e.hasSamePayload(2L, 7L, OccupancyEventType.ENTRY, 2, NOW.minusSeconds(3)));
        assertFalse(e.hasSamePayload(2L, 7L, OccupancyEventType.ENTRY, 1, NOW.minusSeconds(4)));
        assertFalse(e.hasSamePayload(5L, 7L, OccupancyEventType.ENTRY, 1, NOW.minusSeconds(3)));
        assertFalse(e.hasSamePayload(2L, 8L, OccupancyEventType.ENTRY, 1, NOW.minusSeconds(3)));
    }
}
