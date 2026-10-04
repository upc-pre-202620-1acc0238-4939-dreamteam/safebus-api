package com.dreamteam.safebus.fleet.interfaces.rest;

import com.dreamteam.safebus.fleet.application.CreateRouteCommand;
import com.dreamteam.safebus.fleet.application.CreateRouteCommandService;
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
@RequestMapping("/api/v1/routes")
@PreAuthorize("hasRole('SUPERVISOR')")
@Tag(name = "Routes")
public class RouteController {

    private final CreateRouteCommandService commandService;

    public RouteController(CreateRouteCommandService commandService) {
        this.commandService = commandService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a route")
    @ApiResponse(responseCode = "201", description = "Route created")
    @ApiResponse(responseCode = "401", description = "No token provided")
    @ApiResponse(responseCode = "403", description = "Supervisor role required")
    @ApiResponse(responseCode = "422", description = "Validation failed")
    public RouteResource create(@Valid @RequestBody CreateRouteRequest request) {
        return RouteResourceFromEntityAssembler.toResource(
            commandService.create(new CreateRouteCommand(
                request.name(), request.origin(), request.destination())));
    }
}
