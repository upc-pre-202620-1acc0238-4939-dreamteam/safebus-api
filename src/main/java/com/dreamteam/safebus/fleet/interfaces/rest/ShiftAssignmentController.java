package com.dreamteam.safebus.fleet.interfaces.rest;

import com.dreamteam.safebus.fleet.application.CreateShiftAssignmentCommand;
import com.dreamteam.safebus.fleet.application.CreateShiftAssignmentCommandService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/shift-assignments")
@PreAuthorize("hasRole('SUPERVISOR')")
@Tag(name = "Shift Assignments")
public class ShiftAssignmentController {

    private final CreateShiftAssignmentCommandService commandService;

    public ShiftAssignmentController(CreateShiftAssignmentCommandService commandService) {
        this.commandService = commandService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a shift assignment")
    @ApiResponse(responseCode = "201", description = "Shift assignment created")
    @ApiResponse(responseCode = "401", description = "No token provided")
    @ApiResponse(responseCode = "403", description = "Supervisor role required")
    @ApiResponse(responseCode = "409", description = "Driver or bus has an overlapping assignment")
    @ApiResponse(responseCode = "422", description = "Invalid period, resource not in company, resource disabled, or validation failed")
    public ShiftAssignmentResource create(@Valid @RequestBody CreateShiftAssignmentRequest request) {
        return ShiftAssignmentResourceFromEntityAssembler.toResource(
            commandService.create(new CreateShiftAssignmentCommand(
                request.driverId(), request.busId(), request.routeId(),
                request.plannedStart(), request.plannedEnd())));
    }
}
