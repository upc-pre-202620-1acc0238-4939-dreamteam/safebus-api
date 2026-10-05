package com.dreamteam.safebus.trip.interfaces.rest;

import com.dreamteam.safebus.trip.application.BusLocationResult;
import com.dreamteam.safebus.trip.application.GetBusLocationQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/vehicles")
@Tag(name = "Vehicles")
public class VehicleLocationController {

    private final GetBusLocationQueryService getBusLocationQueryService;

    public VehicleLocationController(GetBusLocationQueryService getBusLocationQueryService) {
        this.getBusLocationQueryService = getBusLocationQueryService;
    }

    @GetMapping("/{id}/location")
    @PreAuthorize("hasAnyRole('SUPERVISOR', 'PASSENGER')")
    @Operation(summary = "Get the current GPS position of a bus")
    @ApiResponse(responseCode = "200", description = "Current bus position returned")
    @ApiResponse(responseCode = "401", description = "No token provided")
    @ApiResponse(responseCode = "403", description = "Driver role, or bus access denied")
    @ApiResponse(responseCode = "404", description = "LOCATION_UNAVAILABLE — no position recorded yet")
    public VehicleLocationResource getBusLocation(@PathVariable Long id) {
        BusLocationResult result = getBusLocationQueryService.getLocation(id);
        return new VehicleLocationResource(
            result.busId(),
            result.latitude(),
            result.longitude(),
            result.capturedAt(),
            result.accuracyMeters());
    }
}
