package com.dreamteam.safebus.trip.interfaces.rest;

import com.dreamteam.safebus.trip.application.GetOccupancyByBusQuery;
import com.dreamteam.safebus.trip.application.OccupancyQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/buses/{busId}/occupancy")
@PreAuthorize("hasRole('SUPERVISOR')")
@Tag(name = "Occupancy")
public class OccupancyController {

    private final OccupancyQueryService queryService;

    public OccupancyController(OccupancyQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping
    @Operation(summary = "Get the passenger count of a bus with its freshness and validity")
    @ApiResponse(responseCode = "200", description = "Occupancy; status is CURRENT, STALE or UNAVAILABLE (count absent)")
    @ApiResponse(responseCode = "401", description = "No token provided")
    @ApiResponse(responseCode = "403", description = "Not a supervisor, or BUS_ACCESS_DENIED")
    public ResponseEntity<OccupancyResource> get(@PathVariable Long busId) {
        var result = queryService.getByBus(new GetOccupancyByBusQuery(busId));
        return ResponseEntity.ok()
            .cacheControl(CacheControl.noStore())
            .body(OccupancyResourceFromResultAssembler.toResource(result));
    }
}
