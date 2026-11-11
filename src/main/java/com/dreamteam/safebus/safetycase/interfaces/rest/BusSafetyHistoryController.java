package com.dreamteam.safebus.safetycase.interfaces.rest;

import com.dreamteam.safebus.safetycase.application.GetBusSafetyHistoryQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Bus Safety History")
public class BusSafetyHistoryController {
    private final GetBusSafetyHistoryQueryService queryService;

    public BusSafetyHistoryController(GetBusSafetyHistoryQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping("/api/v1/vehicles/{id}/safety-history")
    @PreAuthorize("hasRole('PASSENGER')")
    @Operation(summary = "Get the public safety history for the active journey bus and shift",
        description = "A group of passenger requests that became an emergency appears once, under the group's "
            + "stable GR- reference; driver emergencies keep their EM- reference. The optional shiftId must "
            + "match the shift of the active journey. A different bus or shift, a malformed id and an ended "
            + "journey are all answered with the same BUS_ACCESS_DENIED. An empty history only states that no "
            + "reports are recorded.")
    @ApiResponse(responseCode = "200", description = "Redacted bus safety history")
    @ApiResponse(responseCode = "401", description = "No token provided")
    @ApiResponse(responseCode = "403", description = "Passenger role required or BUS_ACCESS_DENIED")
    public ResponseEntity<BusSafetyHistoryResource> getHistory(@PathVariable String id,
                                                             @RequestParam(required = false) String shiftId) {
        var result = queryService.getForBus(id, shiftId);
        var items = result.items().stream()
            .map(item -> new BusSafetyHistoryResource.Item(
                item.reference(), item.source(), item.recordedAt(), item.state(), item.stateLabel(),
                item.requestCount()))
            .toList();
        return ResponseEntity.ok(new BusSafetyHistoryResource(
            result.busId(), result.shiftId(), items, result.message()));
    }
}

