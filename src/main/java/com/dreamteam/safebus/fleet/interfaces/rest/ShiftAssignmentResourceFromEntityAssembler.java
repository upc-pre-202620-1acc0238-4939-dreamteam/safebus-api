package com.dreamteam.safebus.fleet.interfaces.rest;

import com.dreamteam.safebus.fleet.domain.model.ShiftAssignment;

public class ShiftAssignmentResourceFromEntityAssembler {

    private ShiftAssignmentResourceFromEntityAssembler() {}

    public static ShiftAssignmentResource toResource(ShiftAssignment sa) {
        return new ShiftAssignmentResource(
            sa.getId(),
            sa.getDriverId(),
            sa.getBusId(),
            sa.getRouteId(),
            sa.getPlannedStart(),
            sa.getPlannedEnd(),
            sa.getStatus(),
            sa.getCreatedByUserId(),
            sa.getCreatedAt());
    }
}
