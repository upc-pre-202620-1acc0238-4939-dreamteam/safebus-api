package com.dreamteam.safebus.fleet.interfaces.rest;

import com.dreamteam.safebus.fleet.application.CreateBusCommand;
import com.dreamteam.safebus.fleet.application.CreateBusCommandService;
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
@RequestMapping("/api/v1/buses")
@PreAuthorize("hasRole('SUPERVISOR')")
@Tag(name = "Buses")
public class BusController {

    private final CreateBusCommandService commandService;

    public BusController(CreateBusCommandService commandService) {
        this.commandService = commandService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a bus")
    @ApiResponse(responseCode = "201", description = "Bus created")
    @ApiResponse(responseCode = "401", description = "No token provided")
    @ApiResponse(responseCode = "403", description = "Supervisor role required")
    @ApiResponse(responseCode = "409", description = "Plate already taken")
    @ApiResponse(responseCode = "422", description = "Validation failed")
    public BusResource create(@Valid @RequestBody CreateBusRequest request) {
        return BusResourceFromEntityAssembler.toResource(
            commandService.create(new CreateBusCommand(request.plate())));
    }
}
