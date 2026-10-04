package com.dreamteam.safebus.fleet.interfaces.rest;

import com.dreamteam.safebus.fleet.application.CreateDriverCommand;
import com.dreamteam.safebus.fleet.application.CreateDriverCommandService;
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
@RequestMapping("/api/v1/drivers")
@PreAuthorize("hasRole('SUPERVISOR')")
@Tag(name = "Drivers")
public class DriverController {

    private final CreateDriverCommandService commandService;

    public DriverController(CreateDriverCommandService commandService) {
        this.commandService = commandService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a driver")
    @ApiResponse(responseCode = "201", description = "Driver created")
    @ApiResponse(responseCode = "401", description = "No token provided")
    @ApiResponse(responseCode = "403", description = "Supervisor role required")
    @ApiResponse(responseCode = "409", description = "Login ID already taken")
    @ApiResponse(responseCode = "422", description = "Password too short or validation failed")
    public DriverResource create(@Valid @RequestBody CreateDriverRequest request) {
        var driver = commandService.create(
            new CreateDriverCommand(request.fullName(), request.loginId(), request.initialPassword()));
        return DriverResourceFromEntityAssembler.toResource(driver, request.loginId());
    }
}
