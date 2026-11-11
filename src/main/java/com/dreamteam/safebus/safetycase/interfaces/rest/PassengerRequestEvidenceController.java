package com.dreamteam.safebus.safetycase.interfaces.rest;

import com.dreamteam.safebus.safetycase.application.GetRequestEvidenceQueryService;
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
@RequestMapping("/api/v1/passenger-requests")
@PreAuthorize("hasRole('SUPERVISOR')")
@Tag(name = "Passenger Group Review")
public class PassengerRequestEvidenceController {

    private final GetRequestEvidenceQueryService queryService;

    public PassengerRequestEvidenceController(GetRequestEvidenceQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping("/{id}/photo")
    @Operation(summary = "Download the incident photo of a request that belongs to a company group",
        description = "Served only to a supervisor of the group's company, never cacheable and never through "
            + "a public URL.")
    @ApiResponse(responseCode = "200", description = "The stored image with its original content type")
    @ApiResponse(responseCode = "401", description = "No token provided")
    @ApiResponse(responseCode = "403", description = "Supervisor role required or EVIDENCE_ACCESS_DENIED")
    @ApiResponse(responseCode = "404", description = "EVIDENCE_NOT_FOUND: the stored image is missing")
    public ResponseEntity<byte[]> photo(@PathVariable String id) {
        GetRequestEvidenceResult result = queryService.get(id);
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(result.contentType()))
            .cacheControl(CacheControl.noStore())
            .header("X-Content-Type-Options", "nosniff")
            .body(result.content());
    }
}
