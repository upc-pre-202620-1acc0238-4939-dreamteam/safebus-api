package com.dreamteam.safebus.safetycase.interfaces.rest;

import com.dreamteam.safebus.safetycase.application.CreateDriverEmergencyCommand;
import com.dreamteam.safebus.safetycase.application.CreateDriverEmergencyCommandService;
import com.dreamteam.safebus.safetycase.application.CreateDriverEmergencyResult;
import com.dreamteam.safebus.safetycase.application.GetDriverEmergencyQueryService;
import com.dreamteam.safebus.safetycase.application.GetDriverEmergencyResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/driver-emergencies")
@PreAuthorize("hasRole('DRIVER')")
@Tag(name = "Driver Emergencies")
public class DriverEmergencyController {

    private final CreateDriverEmergencyCommandService createService;
    private final GetDriverEmergencyQueryService      getService;

    public DriverEmergencyController(CreateDriverEmergencyCommandService createService,
                                      GetDriverEmergencyQueryService getService) {
        this.createService = createService;
        this.getService    = getService;
    }

    @PostMapping
    @Operation(summary = "Trigger a driver emergency alert")
    @ApiResponse(responseCode = "201", description = "Emergency created")
    @ApiResponse(responseCode = "200", description = "Identical retry — emergency already active")
    @ApiResponse(responseCode = "401", description = "No token provided")
    @ApiResponse(responseCode = "403", description = "Driver role required or SHIFT_NOT_AUTHORIZED")
    @ApiResponse(responseCode = "409", description = "EMERGENCY_ID_REUSED")
    @ApiResponse(responseCode = "422", description = "VALIDATION_FAILED, INVALID_COORDINATES, INCOMPLETE_COORDINATES, or INVALID_ACTIVATION_TIME")
    public ResponseEntity<DriverEmergencyCreatedResource> create(
            @Valid @RequestBody CreateDriverEmergencyRequest request) {

        CreateDriverEmergencyResult result = createService.create(new CreateDriverEmergencyCommand(
            request.id(), request.shiftId(), request.activatedAt(),
            request.latitude(), request.longitude()));

        DriverEmergencyCreatedResource resource = new DriverEmergencyCreatedResource(
            result.id(), result.status(), result.priority(),
            result.activatedAt(), result.receivedAt());

        HttpStatus status = result.duplicate() ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(resource);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get driver emergency status")
    @ApiResponse(responseCode = "200", description = "Emergency status")
    @ApiResponse(responseCode = "401", description = "No token provided")
    @ApiResponse(responseCode = "403", description = "Driver role required or EMERGENCY_ACCESS_DENIED")
    public ResponseEntity<DriverEmergencyStatusResource> getStatus(@PathVariable String id) {
        GetDriverEmergencyResult result = getService.getForDriver(id);
        DriverEmergencyStatusResource resource = new DriverEmergencyStatusResource(
            result.id(), result.status(),
            result.activatedAt(), result.receivedAt(),
            result.attentionStartedAt(), result.closedAt(),
            result.userResponse());
        return ResponseEntity.ok(resource);
    }
}
