package com.dreamteam.safebus.fleetmonitoring.interfaces.rest;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

public record FleetOverviewResource(Instant generatedAt, List<BusResource> buses) {

    public record BusResource(
        Long busId,
        String plate,
        boolean enabled,
        @Schema(description = "Recorded passenger capacity (US12); null when never recorded")
        Integer capacity,
        OccupancyResource occupancy,
        @Schema(description = "Active shift of the bus; null when the bus has no active shift")
        ShiftResource shift,
        @Schema(description = "Route of the active shift; null when the bus has no active shift")
        RouteResource route,
        @Schema(description = "Driver of the active shift; null when the bus has no active shift")
        DriverResource driver,
        LocationResource location,
        @Schema(description = "Open emergencies (ACTIVE or IN_PROGRESS) of the bus, newest received first")
        List<EmergencyResource> emergencies,
        @Schema(description = "Passenger request groups with their approval states. Always empty for now: "
            + "it depends on a story that is not implemented yet (US08)")
        List<Object> passengerGroups) {}

    public record OccupancyResource(
        @Schema(description = "Passenger count. Always null for now: it depends on a story that is not "
            + "implemented yet (US19)")
        Integer count,
        @Schema(description = "Recorded bus capacity (US12); null when never recorded")
        Integer capacity,
        @Schema(description = "Always UNAVAILABLE for now: the passenger count is not implemented yet (US19)",
            allowableValues = "UNAVAILABLE")
        String status) {}

    public record ShiftResource(Long shiftId, Instant startedAt) {}

    public record RouteResource(String name, String origin, String destination) {}

    public record DriverResource(String fullName) {}

    public record LocationResource(
        @Schema(description = "CURRENT when the sample is at most three minutes old, STALE when it is older "
            + "(coordinates and capturedAt are kept), UNAVAILABLE when the bus has no sample "
            + "(every other field is null)",
            allowableValues = {"CURRENT", "STALE", "UNAVAILABLE"})
        String status,
        Double latitude,
        Double longitude,
        Double accuracyMeters,
        @Schema(description = "Capture time of the sample; for STALE it is the last capture time")
        Instant capturedAt) {}

    public record EmergencyResource(
        String id,
        String source,
        String priority,
        @Schema(allowableValues = {"ACTIVE", "IN_PROGRESS"})
        String status,
        Long shiftId,
        Instant activatedAt,
        Instant receivedAt,
        Instant attentionStartedAt) {}
}
