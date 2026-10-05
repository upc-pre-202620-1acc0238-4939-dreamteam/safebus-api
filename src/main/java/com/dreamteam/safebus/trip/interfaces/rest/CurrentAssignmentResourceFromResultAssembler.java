package com.dreamteam.safebus.trip.interfaces.rest;

import com.dreamteam.safebus.trip.application.CurrentAssignmentResult;

public class CurrentAssignmentResourceFromResultAssembler {

    private CurrentAssignmentResourceFromResultAssembler() {}

    public static CurrentAssignmentResource toResource(CurrentAssignmentResult result) {
        return new CurrentAssignmentResource(
            result.assignmentId(),
            result.status(),
            result.busPlate(),
            result.routeName(),
            result.origin(),
            result.destination(),
            result.plannedStart(),
            result.plannedEnd());
    }
}
