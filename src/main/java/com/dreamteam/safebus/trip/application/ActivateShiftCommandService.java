package com.dreamteam.safebus.trip.application;

import com.dreamteam.safebus.trip.domain.model.DriverShift;

public interface ActivateShiftCommandService {
    DriverShift activate(ActivateShiftCommand command);
}
