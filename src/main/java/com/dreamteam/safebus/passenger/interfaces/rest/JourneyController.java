package com.dreamteam.safebus.passenger.interfaces.rest;

import com.dreamteam.safebus.passenger.application.EndJourneyCommand;
import com.dreamteam.safebus.passenger.application.EndJourneyCommandService;
import com.dreamteam.safebus.passenger.application.EndJourneyResult;
import com.dreamteam.safebus.passenger.application.StartJourneyCommand;
import com.dreamteam.safebus.passenger.application.StartJourneyCommandService;
import com.dreamteam.safebus.passenger.application.StartJourneyResult;
import com.dreamteam.safebus.shared.application.CurrentUserProvider;
import com.dreamteam.safebus.shared.domain.exceptions.ForbiddenOperationException;
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
    @ApiResponse(responseCode = "200", description = "Existing active journey on same bus returned (same body as 201)")
    @ApiResponse(responseCode = "401", description = "No token provided")
    @ApiResponse(responseCode = "403", description = "Passenger role required")
    @ApiResponse(responseCode = "409", description = "Active journey on a different bus (ACTIVE_JOURNEY_EXISTS)")
    @ApiResponse(responseCode = "422", description = "BUS_QR_INVALID (also when the body or busQrCode is missing) or BUS_NOT_IN_SERVICE")
    public ResponseEntity<JourneyResource> start(@RequestBody(required = false) StartJourneyRequest request) {
        Long userId = currentUserProvider.current().userId();
        String qrCode = request == null ? null : request.busQrCode();
        StartJourneyResult result = startService.start(new StartJourneyCommand(userId, qrCode));
        HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(new JourneyResource(
            result.journeyId(), result.status().name(), result.startedAt(),
            new JourneyResource.BusResource(result.plate(), result.companyName(),
                result.companyValidated() ? "VALIDATED" : "NOT_VALIDATED"),
            new JourneyResource.RouteResource(result.routeName(), result.origin(), result.destination()),
            result.driverPublicName()));
    }

    @PostMapping("/{id}/end")
    @Operation(summary = "End a passenger journey",
        description = "reason is optional and defaults to MANUAL; AUTOMATIC_SEPARATION may carry the app's "
            + "original endedAt and the separation summary. A repeated call returns the original end data "
            + "and changes nothing.")
    @ApiResponse(responseCode = "200", description = "Journey ended (or already ended: original data returned)")
    @ApiResponse(responseCode = "401", description = "No token provided")
    @ApiResponse(responseCode = "403", description = "Passenger role required or JOURNEY_ACCESS_DENIED "
        + "(foreign, unknown or malformed id)")
    @ApiResponse(responseCode = "422", description = "INVALID_END_REASON, INVALID_END_TIME or INVALID_END_SUMMARY")
    public EndJourneyResource end(@PathVariable String id, @RequestBody(required = false) EndJourneyRequest request) {
        Long userId = currentUserProvider.current().userId();
        EndJourneyRequest body = request == null ? new EndJourneyRequest(null, null, null) : request;
        EndJourneyRequest.Summary summary =
            body.summary() == null ? new EndJourneyRequest.Summary(null, null) : body.summary();
        EndJourneyResult result = endService.end(new EndJourneyCommand(parseJourneyId(id), userId, body.reason(),
            body.endedAt(), summary.separationDistanceMeters(), summary.separationDurationSeconds()));
        EndJourneyResource.Summary resource = result.summary() == null ? null
            : new EndJourneyResource.Summary(result.summary().distanceMeters(), result.summary().durationSeconds());
        return new EndJourneyResource(result.journeyId(), result.status().name(),
            result.endedAt(), result.endReason().name(), resource);
    }

    private static Long parseJourneyId(String id) {
        try {
            return Long.valueOf(id);
        } catch (NumberFormatException ex) {
            throw new ForbiddenOperationException("JOURNEY_ACCESS_DENIED", "journey not found or access denied");
        }
    }
}
