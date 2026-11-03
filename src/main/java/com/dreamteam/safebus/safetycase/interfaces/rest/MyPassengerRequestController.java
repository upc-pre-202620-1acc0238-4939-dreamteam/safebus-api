package com.dreamteam.safebus.safetycase.interfaces.rest;

import com.dreamteam.safebus.safetycase.application.GetMyPassengerRequestQueryService;
import com.dreamteam.safebus.safetycase.application.GetMyPassengerRequestsQueryService;
import com.dreamteam.safebus.safetycase.application.GetMyRequestEvidenceQueryService;
import com.dreamteam.safebus.safetycase.application.GetRequestEvidenceResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/passenger-requests/mine")
@PreAuthorize("hasRole('PASSENGER')")
@Tag(name = "Passenger Requests")
public class MyPassengerRequestController {

    private final GetMyPassengerRequestsQueryService listService;
    private final GetMyPassengerRequestQueryService getService;
    private final GetMyRequestEvidenceQueryService evidenceService;

    public MyPassengerRequestController(GetMyPassengerRequestsQueryService listService,
                                        GetMyPassengerRequestQueryService getService,
                                        GetMyRequestEvidenceQueryService evidenceService) {
        this.listService = listService;
        this.getService = getService;
        this.evidenceService = evidenceService;
    }

    @GetMapping
    @Operation(summary = "List the panic requests submitted by the authenticated account",
        description = "Newest first, without pagination. It does not need an active journey. Each item reports "
            + "the account's own message, an evidence reference path, the submission time, the group's "
            + "contributor count and a status: LATE, COLLECTING, AWAITING_APPROVAL, EXPIRED or NOT_APPROVED "
            + "for a request that has not become an emergency, or ACTIVE, IN_PROGRESS or CLOSED for the "
            + "linked emergency. Once the company has decided, decision carries the result, the decision "
            + "time and the user-facing response.")
    @ApiResponse(responseCode = "200", description = "The account's requests, or an empty list with a message")
    @ApiResponse(responseCode = "401", description = "No token provided")
    @ApiResponse(responseCode = "403", description = "Passenger role required")
    public MyPassengerRequestsResource list() {
        return MyPassengerRequestResourceFromResultAssembler.toResource(listService.list());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Read one panic request submitted by the authenticated account",
        description = "Works after the journey ended. Another account's request, an unknown id and a "
            + "malformed id are answered identically.")
    @ApiResponse(responseCode = "200", description = "The request with its current status and decision")
    @ApiResponse(responseCode = "401", description = "No token provided")
    @ApiResponse(responseCode = "403", description = "Passenger role required or PASSENGER_REQUEST_ACCESS_DENIED")
    public MyPassengerRequestResource get(@PathVariable String id) {
        return MyPassengerRequestResourceFromResultAssembler.toResource(getService.get(id));
    }

    // "/mine/photo" would otherwise match the supervisor-only "/{id}/photo" with id "mine"
    @GetMapping("/photo")
    @Operation(summary = "Treat the literal id \"photo\" like any other unknown request id",
        description = "Always answered with PASSENGER_REQUEST_ACCESS_DENIED.")
    @ApiResponse(responseCode = "401", description = "No token provided")
    @ApiResponse(responseCode = "403", description = "Passenger role required or PASSENGER_REQUEST_ACCESS_DENIED")
    public ResponseEntity<byte[]> literalPhotoId() {
        return photo("photo");
    }

    @GetMapping("/{id}/photo")
    @Operation(summary = "Download the incident photo of one of the account's own requests",
        description = "Served only to the account that submitted the request, never cacheable and never "
            + "through a public URL. Another account's request, an unknown id and a malformed id are "
            + "answered identically; the missing-image 404 is only reachable for an owned request.")
    @ApiResponse(responseCode = "200", description = "The stored image with its original content type")
    @ApiResponse(responseCode = "401", description = "No token provided")
    @ApiResponse(responseCode = "403", description = "Passenger role required or PASSENGER_REQUEST_ACCESS_DENIED")
    @ApiResponse(responseCode = "404", description = "EVIDENCE_NOT_FOUND: the stored image is missing")
    public ResponseEntity<byte[]> photo(@PathVariable String id) {
        GetRequestEvidenceResult result = evidenceService.get(id);
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(result.contentType()))
            .cacheControl(CacheControl.noStore())
            .header("X-Content-Type-Options", "nosniff")
            .body(result.content());
    }
}
