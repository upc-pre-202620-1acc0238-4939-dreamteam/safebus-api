package com.dreamteam.safebus.safetycase.interfaces.rest;

import com.dreamteam.safebus.safetycase.application.GetReviewQueueQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/review-queue")
@PreAuthorize("hasRole('SUPERVISOR')")
@Tag(name = "Passenger Group Review")
public class ReviewQueueController {

    private final GetReviewQueueQueryService queryService;

    public ReviewQueueController(GetReviewQueueQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping
    @Operation(summary = "List the company's pending work by priority",
        description = "Open driver emergencies first, then open approved passenger emergencies, then passenger "
            + "groups awaiting approval. Within each emergency block ACTIVE precedes IN_PROGRESS, then the "
            + "oldest received first, then id. Groups are ordered by the time they began awaiting approval, "
            + "then id.")
    @ApiResponse(responseCode = "200", description = "The three prioritized blocks")
    @ApiResponse(responseCode = "401", description = "No token provided")
    @ApiResponse(responseCode = "403", description = "Supervisor role required or REVIEW_ACCESS_DENIED")
    public ResponseEntity<ReviewQueueResource> get() {
        return ResponseEntity.ok(ReviewQueueResourceFromResultAssembler.toResource(queryService.get()));
    }
}
