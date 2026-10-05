package com.dreamteam.safebus.passenger.interfaces.rest;

public record JourneyResource(
    Long id,
    String plate, String companyName, boolean companyValidated,
    String routeName, String origin, String destination, String driverPublicName
) {}
