package com.dreamteam.safebus.trip.interfaces.rest;

import com.dreamteam.safebus.trip.application.OccupancyResult;

public final class OccupancyResourceFromResultAssembler {

    private OccupancyResourceFromResultAssembler() {}

    public static OccupancyResource toResource(OccupancyResult result) {
        return new OccupancyResource(result.busId(), result.status(), result.reason(), result.count(),
            result.capacity(), result.load(), result.lastCountAt(), result.lastHeartbeatAt(),
            result.generatedAt());
    }
}
