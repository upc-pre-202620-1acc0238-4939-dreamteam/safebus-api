package com.dreamteam.safebus.passenger.interfaces.rest;

import java.time.Instant;

public record JourneyResource(
    Long journeyId, String status, Instant startedAt,
    BusResource bus, RouteResource route, String driverPublicName
) {
    public record BusResource(String plate, String companyName, String companyValidationStatus) {}

    public record RouteResource(String name, String origin, String destination) {}
}
