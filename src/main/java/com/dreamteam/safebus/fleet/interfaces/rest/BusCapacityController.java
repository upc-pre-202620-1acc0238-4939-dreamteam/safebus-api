package com.dreamteam.safebus.fleet.interfaces.rest;

import com.dreamteam.safebus.fleet.application.UpdateBusCapacity;
import com.dreamteam.safebus.fleet.application.UpdateBusCapacityCommand;
import com.dreamteam.safebus.fleet.domain.model.Bus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/buses")
@PreAuthorize("hasRole('SUPERVISOR')")
@Tag(name = "Buses")
public class BusCapacityController {

    private final UpdateBusCapacity service;

    public BusCapacityController(UpdateBusCapacity service) {
        this.service = service;
    }

    @PutMapping("/{id}/capacity")
    @Operation(summary = "Record the passenger capacity of a bus")
    @ApiResponse(responseCode = "200", description = "Bus capacity recorded")
    @ApiResponse(responseCode = "401", description = "No token provided")
    @ApiResponse(responseCode = "403", description = "Supervisor role required or BUS_ACCESS_DENIED")
    @ApiResponse(responseCode = "422", description = "CAPACITY_REFERENCE_REQUIRED, CAPACITY_REFERENCE_TOO_LONG, CAPACITY_REQUIRED, or INVALID_CAPACITY")
    public BusCapacityResource update(@PathVariable Long id, @RequestBody UpdateBusCapacityRequest request) {
        Bus bus = service.update(new UpdateBusCapacityCommand(id, request.capacity(), request.technicalRecordReference()));
        return new BusCapacityResource(bus.getId(), bus.getCapacity(), bus.getCapacityReference(),
            bus.getCapacityUpdatedByUserId(), bus.getCapacityUpdatedAt());
    }
}
