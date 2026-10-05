package com.dreamteam.safebus.trip.interfaces.rest;

import com.dreamteam.safebus.trip.application.RecordLocationEventResult;

public class LocationEventResourceFromResultAssembler {

    private LocationEventResourceFromResultAssembler() {}

    public static LocationEventResource toResource(RecordLocationEventResult result) {
        return new LocationEventResource(
            result.eventId(),
            result.shiftId(),
            result.busId(),
            result.receivedAt(),
            result.currentPositionUpdated());
    }
}
