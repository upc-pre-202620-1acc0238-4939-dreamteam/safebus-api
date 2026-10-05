package com.dreamteam.safebus.fleetmonitoring.application;

import com.dreamteam.safebus.fleetmonitoring.domain.LocationStatus;

import java.time.Instant;
import java.util.List;

public record FleetOverviewResult(Instant generatedAt, List<BusOverview> buses) {

    public record BusOverview(Long busId, String plate, boolean enabled, Integer capacity,
                              ShiftOverview shift, RouteOverview route, DriverOverview driver,
                              LocationOverview location, List<EmergencyOverview> emergencies) {}

    public record ShiftOverview(Long shiftId, Instant startedAt) {}

    public record RouteOverview(String name, String origin, String destination) {}

    public record DriverOverview(String fullName) {}

    public record LocationOverview(LocationStatus status, Double latitude, Double longitude,
                                   Double accuracyMeters, Instant capturedAt) {}

    public record EmergencyOverview(String id, String source, String priority, String status,
                                    Long shiftId, Instant activatedAt, Instant receivedAt,
                                    Instant attentionStartedAt) {}
}
