package com.dreamteam.safebus.trip.interfaces.rest;

import com.dreamteam.safebus.trip.domain.model.DriverShift;

public class ActivateShiftResourceFromEntityAssembler {

    private ActivateShiftResourceFromEntityAssembler() {}

    public static ActivateShiftResource toResource(DriverShift shift) {
        return new ActivateShiftResource(
            shift.getId(),
            shift.getAssignmentId(),
            shift.getDriverId(),
            shift.getBusId(),
            shift.getRouteId(),
            shift.getStatus(),
            shift.getStartedAt());
    }
}
