package com.dreamteam.safebus.trip.application;

public interface GetBusLocationQueryService {
    BusLocationResult getLocation(Long busId);
}
