package com.dreamteam.safebus.trip.interfaces.rest;

import com.dreamteam.safebus.trip.application.ActivateShiftCommand;
import com.dreamteam.safebus.trip.application.ActivateShiftCommandService;
import com.dreamteam.safebus.trip.application.GetCurrentAssignmentQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/shifts")
@PreAuthorize("hasRole('DRIVER')")
@Tag(name = "Shifts")
public class ShiftController {

    private final ActivateShiftCommandService activateShiftCommandService;
    private final GetCurrentAssignmentQueryService getCurrentAssignmentQueryService;

    public ShiftController(ActivateShiftCommandService activateShiftCommandService,
                           GetCurrentAssignmentQueryService getCurrentAssignmentQueryService) {
        this.activateShiftCommandService = activateShiftCommandService;
        this.getCurrentAssignmentQueryService = getCurrentAssignmentQueryService;
    }

    @PostMapping("/activate")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Activate a shift by scanning a QR credential")
    @ApiResponse(responseCode = "201", description = "Shift activated")
    @ApiResponse(responseCode = "401", description = "No token provided")
    @ApiResponse(responseCode = "403", description = "Driver role required")
    @ApiResponse(responseCode = "404", description = "Credential or assignment not found")
    @ApiResponse(responseCode = "409", description = "Assignment is already active")
    @ApiResponse(responseCode = "422", description = "Driver disabled, credential expired, or validation failed")
    public ActivateShiftResource activate(@Valid @RequestBody ActivateShiftRequest request) {
        return ActivateShiftResourceFromEntityAssembler.toResource(
            activateShiftCommandService.activate(
                new ActivateShiftCommand(request.assignmentId(), request.qrCredential())));
    }

    @GetMapping("/me/assignment")
    @Operation(summary = "Get the current assigned route and shift for the authenticated driver")
    @ApiResponse(responseCode = "200", description = "Current assignment returned")
    @ApiResponse(responseCode = "401", description = "No token provided")
    @ApiResponse(responseCode = "403", description = "Driver role required")
    @ApiResponse(responseCode = "404", description = "No current assignment found")
    public CurrentAssignmentResource getCurrentAssignment() {
        return CurrentAssignmentResourceFromResultAssembler.toResource(
            getCurrentAssignmentQueryService.getCurrentAssignment());
    }
}
