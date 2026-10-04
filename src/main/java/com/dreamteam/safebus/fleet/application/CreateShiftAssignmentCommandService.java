package com.dreamteam.safebus.fleet.application;

import com.dreamteam.safebus.fleet.domain.model.ShiftAssignment;

public interface CreateShiftAssignmentCommandService {
    ShiftAssignment create(CreateShiftAssignmentCommand command);
}
