package com.dreamteam.safebus.trip.interfaces.rest;

import com.dreamteam.safebus.trip.domain.model.DriverShift;

public class CloseShiftResourceFromEntityAssembler {

    private CloseShiftResourceFromEntityAssembler() {}

    public static CloseShiftResource toResource(DriverShift shift) {
        return new CloseShiftResource(shift.getId(), shift.getStatus(), shift.getClosedAt());
    }
}
