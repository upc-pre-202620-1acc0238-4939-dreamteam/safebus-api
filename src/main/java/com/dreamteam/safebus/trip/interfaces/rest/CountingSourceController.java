package com.dreamteam.safebus.trip.interfaces.rest;

import com.dreamteam.safebus.trip.application.CountingSourceCommandService;
import com.dreamteam.safebus.trip.application.CountingSourceQueryService;
import com.dreamteam.safebus.trip.application.GetCountingSourcesByBusQuery;
import com.dreamteam.safebus.trip.application.RegisterCountingSourceCommand;
import com.dreamteam.safebus.trip.application.RevokeCountingSourceCommand;
import com.dreamteam.safebus.trip.application.RotateCountingSourceCommand;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/buses/{busId}/counting-sources")
@PreAuthorize("hasRole('SUPERVISOR')")
@Tag(name = "Counting sources")
public class CountingSourceController {

    private final CountingSourceCommandService commandService;
    private final CountingSourceQueryService queryService;

    public CountingSourceController(CountingSourceCommandService commandService,
                                    CountingSourceQueryService queryService) {
        this.commandService = commandService;
        this.queryService = queryService;
    }

    @PostMapping
    @Operation(summary = "Register the passenger-count source of a bus; its key is shown once")
    @ApiResponse(responseCode = "201", description = "Source registered; the key is in this response only")
    @ApiResponse(responseCode = "401", description = "No token provided")
    @ApiResponse(responseCode = "403", description = "Not a supervisor, or BUS_ACCESS_DENIED")
    @ApiResponse(responseCode = "409", description = "ACTIVE_SOURCE_EXISTS — the bus already has an active source")
    @ApiResponse(responseCode = "422", description = "LABEL_TOO_LONG")
    public ResponseEntity<CountingSourceCredentialResource> register(
            @PathVariable Long busId,
            @RequestBody(required = false) RegisterCountingSourceRequest request) {
        var result = commandService.register(
            new RegisterCountingSourceCommand(busId, request == null ? null : request.label()));
        return ResponseEntity.status(HttpStatus.CREATED)
            .cacheControl(CacheControl.noStore())
            .body(CountingSourceCredentialResourceFromResultAssembler.toResource(result));
    }

    @GetMapping
    @Operation(summary = "List the counting sources of a bus, without any key material")
    @ApiResponse(responseCode = "200", description = "Sources of the bus")
    @ApiResponse(responseCode = "401", description = "No token provided")
    @ApiResponse(responseCode = "403", description = "Not a supervisor, or BUS_ACCESS_DENIED")
    public List<CountingSourceResource> list(@PathVariable Long busId) {
        return queryService.listByBus(new GetCountingSourcesByBusQuery(busId)).stream()
            .map(CountingSourceResourceFromResultAssembler::toResource)
            .toList();
    }

    @PostMapping("/{sourceId}/rotation")
    @Operation(summary = "Replace the key of an active source; the previous key stops working")
    @ApiResponse(responseCode = "200", description = "Key rotated; the new key is in this response only")
    @ApiResponse(responseCode = "401", description = "No token provided")
    @ApiResponse(responseCode = "403", description = "Not a supervisor, or BUS_ACCESS_DENIED")
    @ApiResponse(responseCode = "404", description = "SOURCE_NOT_FOUND")
    @ApiResponse(responseCode = "409", description = "SOURCE_REVOKED")
    public ResponseEntity<CountingSourceCredentialResource> rotate(@PathVariable Long busId,
                                                                   @PathVariable Long sourceId) {
        var result = commandService.rotate(new RotateCountingSourceCommand(busId, sourceId));
        return ResponseEntity.ok()
            .cacheControl(CacheControl.noStore())
            .body(CountingSourceCredentialResourceFromResultAssembler.toResource(result));
    }

    @PostMapping("/{sourceId}/revocation")
    @Operation(summary = "Revoke a source; revoking an already revoked source succeeds")
    @ApiResponse(responseCode = "200", description = "Source revoked")
    @ApiResponse(responseCode = "401", description = "No token provided")
    @ApiResponse(responseCode = "403", description = "Not a supervisor, or BUS_ACCESS_DENIED")
    @ApiResponse(responseCode = "404", description = "SOURCE_NOT_FOUND")
    public CountingSourceResource revoke(@PathVariable Long busId, @PathVariable Long sourceId) {
        var result = commandService.revoke(new RevokeCountingSourceCommand(busId, sourceId));
        return CountingSourceResourceFromResultAssembler.toResource(result);
    }
}
