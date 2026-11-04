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
        @Schema(description = "Active shift of the bus; null when the bus has no active shift")
        ShiftResource shift,
        @Schema(description = "Route of the active shift; null when the bus has no active shift")
        RouteResource route,
        @Schema(description = "Driver of the active shift; null when the bus has no active shift")
        DriverResource driver,
        LocationResource location,
        OccupancyResource occupancy,
        @Schema(description = "Open emergencies (ACTIVE or IN_PROGRESS) of the bus, newest received first")
        List<EmergencyResource> emergencies,
        @Schema(description = "Open passenger request groups of the bus (COLLECTING, AWAITING_APPROVAL or "
            + "APPROVED), oldest window first; carries counts and states only, never identities, messages "
            + "or evidence")
        List<PassengerGroupResource> passengerGroups) {}

    public record OccupancyResource(
        @Schema(description = "CURRENT, STALE (heartbeat older than two minutes, count kept) or UNAVAILABLE "
            + "(count and load are null and reason says why)",
            allowableValues = {"CURRENT", "STALE", "UNAVAILABLE"})
        String status,
        @Schema(description = "Only when UNAVAILABLE", allowableValues = {"NOT_INITIALIZED", "SEQUENCE_GAP",
            "INCONSISTENT_COUNT"})
        String reason,
        @Schema(description = "Passenger count; null when UNAVAILABLE")
        Integer count,
        @Schema(description = "Recorded bus capacity (US12); null when never recorded")
        Integer capacity,
        @Schema(description = "Null when UNAVAILABLE", allowableValues = {"WITHIN_CAPACITY", "OVER_CAPACITY",
            "CAPACITY_UNKNOWN"})
        String load,
        Instant lastCountAt,
        Instant lastHeartbeatAt) {}

    public record PassengerGroupResource(
        @Schema(allowableValues = {"COLLECTING", "AWAITING_APPROVAL", "APPROVED"})
        String status,
        int requestCount,
        Instant windowStartedAt,
        Instant windowExpiresAt,
        @Schema(description = "When the group started awaiting company approval; null before that")
        Instant awaitingSince,
        @Schema(description = "Emergency raised by the approval; null until the group is approved")
        GroupEmergencyResource emergency) {}

    public record GroupEmergencyResource(@Schema(allowableValues = {"ACTIVE", "IN_PROGRESS"}) String status) {}

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
