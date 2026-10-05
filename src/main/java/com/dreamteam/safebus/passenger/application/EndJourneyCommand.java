package com.dreamteam.safebus.passenger.application;

public record EndJourneyCommand(Long journeyId, Long userAccountId, String reason) {}
