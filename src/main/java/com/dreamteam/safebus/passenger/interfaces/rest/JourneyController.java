package com.dreamteam.safebus.passenger.interfaces.rest;

import com.dreamteam.safebus.passenger.application.EndJourneyCommand;
import com.dreamteam.safebus.passenger.application.EndJourneyCommandService;
import com.dreamteam.safebus.passenger.application.EndJourneyResult;
import com.dreamteam.safebus.passenger.application.StartJourneyCommand;
import com.dreamteam.safebus.passenger.application.StartJourneyCommandService;
import com.dreamteam.safebus.passenger.application.StartJourneyResult;
import com.dreamteam.safebus.shared.application.CurrentUserProvider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/journeys")
@PreAuthorize("hasRole('PASSENGER')")
@Tag(name = "Journeys")
public class JourneyController {

    private final StartJourneyCommandService startService;
    private final EndJourneyCommandService endService;
    private final CurrentUserProvider currentUserProvider;

    public JourneyController(StartJourneyCommandService startService,
                              EndJourneyCommandService endService,
                              CurrentUserProvider currentUserProvider) {
        this.startService = startService;
        this.endService = endService;
        this.currentUserProvider = currentUserProvider;
    }

    @PostMapping
    @Operation(summary = "Start or resume a passenger journey")
    @ApiResponse(responseCode = "201", description = "Journey started")
    @ApiResponse(responseCode = "200", description = "Existing active journey on same bus returned")
    @ApiResponse(responseCode = "401", description = "No token provided")
    @ApiResponse(responseCode = "403", description = "Passenger role required")
    @ApiResponse(responseCode = "409", description = "Active journey on a different bus (ACTIVE_JOURNEY_EXISTS)")
    @ApiResponse(responseCode = "422", description = "BUS_QR_INVALID or BUS_NOT_IN_SERVICE")
    public ResponseEntity<JourneyResource> start(@RequestBody StartJourneyRequest request) {
        Long userId = currentUserProvider.current().userId();
        StartJourneyResult result = startService.start(new StartJourneyCommand(userId, request.busQrCode()));
        HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(new JourneyResource(
            result.journeyId(),
            result.plate(), result.companyName(), result.companyValidated(),
            result.routeName(), result.origin(), result.destination(), result.driverPublicName()));
    }

    @PostMapping("/{id}/end")
    @Operation(summary = "End a passenger journey")
    @ApiResponse(responseCode = "200", description = "Journey ended (or already ended — idempotent)")
    @ApiResponse(responseCode = "401", description = "No token provided")
    @ApiResponse(responseCode = "403", description = "Passenger role required or journey access denied")
    @ApiResponse(responseCode = "422", description = "INVALID_END_REASON")
    public EndJourneyResource end(@PathVariable Long id, @RequestBody EndJourneyRequest request) {
        Long userId = currentUserProvider.current().userId();
        EndJourneyResult result = endService.end(new EndJourneyCommand(id, userId, request.reason()));
        return new EndJourneyResource(result.journeyId(), result.changed());
    }
}
