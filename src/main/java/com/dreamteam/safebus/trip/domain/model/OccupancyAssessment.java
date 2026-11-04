package com.dreamteam.safebus.trip.domain.model;

import java.time.Duration;
import java.time.Instant;

// What a reader may be told about a bus count; an unavailable count is never exposed
public record OccupancyAssessment(OccupancyStatus status, UnavailableReason reason,
                                  Integer count, OccupancyLoad load) {

    public static final Duration STALE_AFTER = Duration.ofMinutes(2);

    public static OccupancyAssessment of(BusOccupancy occupancy, Long activeShiftId,
                                         Integer capacity, Instant now) {
        if (occupancy == null) {
            return unavailable(UnavailableReason.NOT_INITIALIZED);
        }
        switch (occupancy.getConsistency()) {
            case GAP:
                return unavailable(UnavailableReason.SEQUENCE_GAP);
            case INCONSISTENT:
                return unavailable(UnavailableReason.INCONSISTENT_COUNT);
            case NOT_INITIALIZED:
                return unavailable(UnavailableReason.NOT_INITIALIZED);
            default:
                break;
        }
        if (activeShiftId == null || !activeShiftId.equals(occupancy.getShiftId())) {
            return unavailable(UnavailableReason.NOT_INITIALIZED);
        }
        Integer count = occupancy.getCount();
        OccupancyLoad load = loadOf(count, capacity);
        Instant heartbeat = occupancy.getLastHeartbeatAt();
        boolean stale = heartbeat == null || Duration.between(heartbeat, now).compareTo(STALE_AFTER) > 0;
        return new OccupancyAssessment(stale ? OccupancyStatus.STALE : OccupancyStatus.CURRENT, null, count, load);
    }

    private static OccupancyAssessment unavailable(UnavailableReason reason) {
        return new OccupancyAssessment(OccupancyStatus.UNAVAILABLE, reason, null, null);
    }

    private static OccupancyLoad loadOf(Integer count, Integer capacity) {
        if (count == null) {
            return null;
        }
        if (capacity == null) {
            return OccupancyLoad.CAPACITY_UNKNOWN;
        }
        return count > capacity ? OccupancyLoad.OVER_CAPACITY : OccupancyLoad.WITHIN_CAPACITY;
    }
}
