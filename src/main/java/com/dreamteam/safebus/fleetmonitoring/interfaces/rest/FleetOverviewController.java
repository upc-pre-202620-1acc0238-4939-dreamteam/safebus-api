package com.dreamteam.safebus.fleetmonitoring.interfaces.rest;

import com.dreamteam.safebus.fleetmonitoring.application.GetFleetOverviewQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/fleet")
@Tag(name = "Fleet Monitoring")
public class FleetOverviewController {

    private final GetFleetOverviewQueryService queryService;

    public FleetOverviewController(GetFleetOverviewQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping("/overview")
    @PreAuthorize("hasRole('SUPERVISOR')")
    @Operation(summary = "Consult the active buses of the supervisor's company with their location, "
        + "occupancy and open emergencies")
    @ApiResponse(responseCode = "200", description = "Fleet overview returned")
    @ApiResponse(responseCode = "401", description = "No token provided")
    @ApiResponse(responseCode = "403", description = "Supervisor role required, or FLEET_ACCESS_DENIED")
    public FleetOverviewResource getOverview() {
        return FleetOverviewResourceFromResultAssembler.toResource(queryService.getOverview());
    }
}
