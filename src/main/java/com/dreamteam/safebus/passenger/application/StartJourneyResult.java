package com.dreamteam.safebus.passenger.application;

public record StartJourneyResult(
    Long journeyId, boolean created,
    String plate, String companyName, boolean companyValidated,
    String routeName, String origin, String destination, String driverPublicName
) {}
