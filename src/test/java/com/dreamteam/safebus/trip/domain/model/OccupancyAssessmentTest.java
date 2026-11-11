package com.dreamteam.safebus.trip.domain.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class OccupancyAssessmentTest {

    private static final Instant NOW = Instant.parse("2030-01-01T10:00:00Z");
    private static final Long SHIFT = 7L;

    private BusOccupancy consistentWithCount(int count) {
        BusOccupancy o = BusOccupancy.create(1L, 2L, 3L);
        o.initialize("init", SHIFT, 0, NOW, NOW);
        for (int i = 1; i <= count; i++) {
            o.apply(SHIFT, OccupancyEventType.ENTRY, i, NOW);
        }
        return o;
    }

    @Test
    void noOccupancyRowIsUnavailableAsNotInitialized() {
        OccupancyAssessment a = OccupancyAssessment.of(null, SHIFT, 40, NOW);

        assertEquals(OccupancyStatus.UNAVAILABLE, a.status());
        assertEquals(UnavailableReason.NOT_INITIALIZED, a.reason());
        assertNull(a.count());
        assertNull(a.load());
    }

    @Test
    void notInitializedIsUnavailable() {
        OccupancyAssessment a = OccupancyAssessment.of(BusOccupancy.create(1L, 2L, 3L), SHIFT, 40, NOW);

        assertEquals(UnavailableReason.NOT_INITIALIZED, a.reason());
    }

    @Test
    void aGapIsUnavailableWithoutTheCount() {
        BusOccupancy o = consistentWithCount(1);
        o.apply(SHIFT, OccupancyEventType.ENTRY, 5, NOW);
        o.recordHeartbeat(NOW);

        OccupancyAssessment a = OccupancyAssessment.of(o, SHIFT, 40, NOW);

        assertEquals(OccupancyStatus.UNAVAILABLE, a.status());
        assertEquals(UnavailableReason.SEQUENCE_GAP, a.reason());
        assertNull(a.count());
    }

    @Test
    void anInconsistentCountIsUnavailable() {
        BusOccupancy o = consistentWithCount(0);
        o.apply(SHIFT, OccupancyEventType.EXIT, 1, NOW);

        OccupancyAssessment a = OccupancyAssessment.of(o, SHIFT, 40, NOW);

        assertEquals(UnavailableReason.INCONSISTENT_COUNT, a.reason());
        assertNull(a.count());
    }

    @Test
    void noActiveShiftOrAnotherShiftIsUnavailable() {
        BusOccupancy o = consistentWithCount(2);
        o.recordHeartbeat(NOW);

        assertEquals(OccupancyStatus.UNAVAILABLE, OccupancyAssessment.of(o, null, 40, NOW).status());
        OccupancyAssessment other = OccupancyAssessment.of(o, 99L, 40, NOW);
        assertEquals(OccupancyStatus.UNAVAILABLE, other.status());
        assertEquals(UnavailableReason.NOT_INITIALIZED, other.reason());
        assertNull(other.count());
    }

    @Test
    void aFreshHeartbeatMakesTheCountCurrent() {
        BusOccupancy o = consistentWithCount(12);
        o.recordHeartbeat(NOW.minusSeconds(30));

        OccupancyAssessment a = OccupancyAssessment.of(o, SHIFT, 40, NOW);

        assertEquals(OccupancyStatus.CURRENT, a.status());
        assertNull(a.reason());
        assertEquals(12, a.count());
        assertEquals(OccupancyLoad.WITHIN_CAPACITY, a.load());
    }

    @Test
    void aHeartbeatExactlyTwoMinutesOldIsStillCurrent() {
        BusOccupancy o = consistentWithCount(1);
        o.recordHeartbeat(NOW.minusSeconds(120));

        assertEquals(OccupancyStatus.CURRENT, OccupancyAssessment.of(o, SHIFT, 40, NOW).status());
    }

    @Test
    void aHeartbeatOlderThanTwoMinutesIsStale() {
        BusOccupancy o = consistentWithCount(1);
        o.recordHeartbeat(NOW.minusSeconds(121));

        assertEquals(OccupancyStatus.STALE, OccupancyAssessment.of(o, SHIFT, 40, NOW).status());
    }

    @Test
    void noHeartbeatAtAllIsStale() {
        BusOccupancy o = consistentWithCount(1);

        assertEquals(OccupancyStatus.STALE, OccupancyAssessment.of(o, SHIFT, 40, NOW).status());
    }

    @Test
    void aCountAboveCapacityIsOverCapacity() {
        BusOccupancy o = consistentWithCount(3);
        o.recordHeartbeat(NOW);

        OccupancyAssessment a = OccupancyAssessment.of(o, SHIFT, 2, NOW);

        assertEquals(OccupancyStatus.CURRENT, a.status());
        assertEquals(3, a.count());
        assertEquals(OccupancyLoad.OVER_CAPACITY, a.load());
    }

    @Test
    void aCountEqualToCapacityIsWithinCapacity() {
        BusOccupancy o = consistentWithCount(2);
        o.recordHeartbeat(NOW);

        assertEquals(OccupancyLoad.WITHIN_CAPACITY, OccupancyAssessment.of(o, SHIFT, 2, NOW).load());
    }

    @Test
    void anUnknownCapacityGivesCapacityUnknown() {
        BusOccupancy o = consistentWithCount(2);
        o.recordHeartbeat(NOW);

        assertEquals(OccupancyLoad.CAPACITY_UNKNOWN, OccupancyAssessment.of(o, SHIFT, null, NOW).load());
    }
}
