package com.dreamteam.safebus.trip.interfaces.rest;

import com.dreamteam.safebus.trip.application.InitializeOccupancyCommand;
import com.dreamteam.safebus.trip.application.OccupancyCommandService;
import com.dreamteam.safebus.trip.application.RecordHeartbeatCommand;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Locale;

@RestController
@RequestMapping("/api/v1")
@PreAuthorize("hasRole('DEVICE')")
@Tag(name = "Passenger count (device)")
@SecurityRequirement(name = "countingSourceKey")
public class OccupancyDeviceController {

    private final OccupancyCommandService commandService;

    public OccupancyDeviceController(OccupancyCommandService commandService) {
        this.commandService = commandService;
    }

    @PostMapping("/occupancy-initializations")
    @Operation(summary = "Confirm that the bus count starts at zero for the active shift")
    @ApiResponse(responseCode = "201", description = "Count initialized")
    @ApiResponse(responseCode = "200", description = "Identical retry; nothing changed")
    @ApiResponse(responseCode = "401", description = "No credential provided")
    @ApiResponse(responseCode = "403", description = "OCCUPANCY_SOURCE_NOT_AUTHORIZED — key, bus or shift refused")
    @ApiResponse(responseCode = "409", description = "ALREADY_INITIALIZED")
    @ApiResponse(responseCode = "422", description = "Malformed request, INVALID_INITIAL_COUNT or INVALID_CAPTURE_TIME")
    public ResponseEntity<OccupancyInitializationResource> initialize(
            @Valid @RequestBody InitializeOccupancyRequest request) {
        var result = commandService.initialize(new InitializeOccupancyCommand(
            request.initializationId().toLowerCase(Locale.ROOT), request.busId(), request.shiftId(),
            request.confirmedCount(), request.confirmedAt()));
        var resource = new OccupancyInitializationResource(result.initializationId(), result.busId(),
            result.shiftId(), result.confirmedCount(), result.confirmedAt());
        return ResponseEntity.status(result.duplicate() ? HttpStatus.OK : HttpStatus.CREATED).body(resource);
    }

    @PostMapping("/occupancy-heartbeats")
    @Operation(summary = "Report that the counting source is alive")
    @ApiResponse(responseCode = "200", description = "Heartbeat recorded with the server time")
    @ApiResponse(responseCode = "401", description = "No credential provided")
    @ApiResponse(responseCode = "403", description = "OCCUPANCY_SOURCE_NOT_AUTHORIZED — key or bus refused")
    @ApiResponse(responseCode = "422", description = "Malformed request")
    public OccupancyHeartbeatResource heartbeat(@Valid @RequestBody RecordHeartbeatRequest request) {
        var result = commandService.heartbeat(new RecordHeartbeatCommand(request.busId()));
        return new OccupancyHeartbeatResource(result.busId(), result.receivedAt());
    }
}
