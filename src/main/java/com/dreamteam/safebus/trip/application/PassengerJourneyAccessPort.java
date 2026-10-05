package com.dreamteam.safebus.trip.application;

public interface PassengerJourneyAccessPort {
    boolean hasActiveJourneyOnBus(Long passengerUserAccountId, Long busId);
}
