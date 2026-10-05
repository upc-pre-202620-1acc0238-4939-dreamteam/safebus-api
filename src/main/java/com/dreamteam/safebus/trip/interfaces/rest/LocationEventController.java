package com.dreamteam.safebus.trip.interfaces.rest;

import com.dreamteam.safebus.trip.application.RecordLocationEventCommand;
import com.dreamteam.safebus.trip.application.RecordLocationEventCommandService;
import com.dreamteam.safebus.trip.application.RecordLocationEventResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/location-events")
@PreAuthorize("hasRole('DRIVER')")
@Tag(name = "Location Events")
public class LocationEventController {

    private final RecordLocationEventCommandService recordLocationEventCommandService;

    public LocationEventController(RecordLocationEventCommandService recordLocationEventCommandService) {
        this.recordLocationEventCommandService = recordLocationEventCommandService;
    }

    @PostMapping
    @Operation(summary = "Record a driver location event")
    @ApiResponse(responseCode = "201", description = "Location event recorded and position updated")
    @ApiResponse(responseCode = "200", description = "Identical retry — event already recorded")
    @ApiResponse(responseCode = "401", description = "No token provided")
    @ApiResponse(responseCode = "403", description = "Driver role required or not the assigned driver")
    @ApiResponse(responseCode = "409", description = "EVENT_ID_REUSED — same id with different payload")
    @ApiResponse(responseCode = "422", description = "VALIDATION_FAILED, INVALID_COORDINATES, INVALID_ACCURACY, INVALID_CAPTURE_TIME, or BUS_SHIFT_MISMATCH")
    public ResponseEntity<LocationEventResource> recordLocationEvent(
            @Valid @RequestBody RecordLocationEventRequest request) {

        RecordLocationEventResult result = recordLocationEventCommandService.record(
            new RecordLocationEventCommand(
                request.eventId(),
                request.shiftId(),
                request.busId(),
                request.capturedAt(),
                request.accuracyMeters(),
                request.latitude(),
                request.longitude()));

        LocationEventResource resource = LocationEventResourceFromResultAssembler.toResource(result);
        HttpStatus status = result.duplicate() ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(resource);
    }
}
