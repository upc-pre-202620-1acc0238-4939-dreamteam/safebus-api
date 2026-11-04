package com.dreamteam.safebus.trip.application;

public interface OccupancyQueryService {
    OccupancyResult getByBus(GetOccupancyByBusQuery query);
}
