package com.dreamteam.safebus.passenger.application;

public record StartJourneyCommand(Long userAccountId, String busQrCode) {}
