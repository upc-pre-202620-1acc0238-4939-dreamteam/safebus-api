package com.dreamteam.safebus.safetycase.interfaces.rest;

import com.dreamteam.safebus.safetycase.application.RegisterPassengerRequest;
import com.dreamteam.safebus.safetycase.application.RegisterPassengerRequestCommand;
import com.dreamteam.safebus.safetycase.application.RegisterPassengerRequestResult;
import com.dreamteam.safebus.shared.domain.exceptions.RuleViolationException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;

@RestController
@RequestMapping("/api/v1/passenger-requests")
@PreAuthorize("hasRole('PASSENGER')")
@Tag(name = "Passenger Requests")
public class PassengerRequestController {

    private final RegisterPassengerRequest registerPassengerRequest;

    public PassengerRequestController(RegisterPassengerRequest registerPassengerRequest) {
        this.registerPassengerRequest = registerPassengerRequest;
    }

    @PostMapping(consumes = "multipart/form-data")
    @Operation(summary = "Send a panic request with a message and an incident photo",
        description = "Idempotent by id. An identical retry answers 200 with the stored requestId, status and "
            + "receivedAt; groupStatus and contributorCount reflect the current state of the group at the time "
            + "of the retry, so they can differ from the first response. A late request (more than 5 minutes "
            + "old, or sent for an ended journey) is stored with status LATE and has no group, so groupStatus "
            + "and contributorCount are null.")
    @ApiResponse(responseCode = "201", description = "Request recorded (COUNTED, ATTACHED or LATE)")
    @ApiResponse(responseCode = "200", description = "Identical retry of a recorded request")
    @ApiResponse(responseCode = "401", description = "No token provided")
    @ApiResponse(responseCode = "403", description = "Passenger role required or JOURNEY_ACCESS_DENIED")
    @ApiResponse(responseCode = "409", description = "REQUEST_ID_REUSED or DUPLICATE_CONTRIBUTION")
    @ApiResponse(responseCode = "422", description = "INVALID_REQUEST, INVALID_REQUEST_ID, MESSAGE_REQUIRED, "
        + "MESSAGE_TOO_LONG, EVIDENCE_PHOTO_REQUIRED, EVIDENCE_PHOTO_INVALID, EVIDENCE_PHOTO_TOO_LARGE, "
        + "INVALID_ACTIVATION_TIME, ACTIVATION_AFTER_JOURNEY_END, BUS_NOT_FOUND or UPLOAD_TOO_LARGE")
    public ResponseEntity<PassengerRequestResource> register(
            @RequestParam(required = false) String id,
            @RequestParam(required = false) String journeyId,
            @RequestParam(required = false) String message,
            @RequestParam(required = false) String activatedAt,
            @RequestPart(required = false) MultipartFile photo) throws IOException {

        Long parsedJourneyId = parseJourneyId(journeyId);
        Instant parsedActivatedAt = parseActivatedAt(activatedAt);
        byte[] photoBytes = photo != null && !photo.isEmpty() ? photo.getBytes() : null;

        RegisterPassengerRequestResult result = registerPassengerRequest.register(
            new RegisterPassengerRequestCommand(id, parsedJourneyId, message, parsedActivatedAt, photoBytes));

        HttpStatus status = result.duplicate() ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(PassengerRequestResourceFromResultAssembler.toResource(result));
    }

    private static Long parseJourneyId(String journeyId) {
        if (journeyId == null || journeyId.isBlank()) {
            throw new RuleViolationException("INVALID_REQUEST", "journeyId is required");
        }
        try {
            return Long.valueOf(journeyId.trim());
        } catch (NumberFormatException ex) {
            throw new RuleViolationException("INVALID_REQUEST", "journeyId must be a number");
        }
    }

    private static Instant parseActivatedAt(String activatedAt) {
        if (activatedAt == null || activatedAt.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(activatedAt.trim()).toInstant();
        } catch (DateTimeParseException ex) {
            throw new RuleViolationException("INVALID_REQUEST", "activatedAt must be an ISO-8601 instant");
        }
    }
}
