package com.dreamteam.safebus.safetycase.interfaces.rest;

import com.dreamteam.safebus.safetycase.application.CloseEmergencyCommand;
import com.dreamteam.safebus.safetycase.application.CloseEmergencyCommandService;
import com.dreamteam.safebus.safetycase.application.CloseEmergencyResult;
import com.dreamteam.safebus.safetycase.application.StartAttentionCommandService;
import com.dreamteam.safebus.safetycase.application.StartAttentionResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/emergencies")
@PreAuthorize("hasRole('SUPERVISOR')")
@Tag(name = "Emergency Attention")
public class EmergencyAttentionController {

    private final StartAttentionCommandService startAttentionService;
    private final CloseEmergencyCommandService closeEmergencyService;

    public EmergencyAttentionController(StartAttentionCommandService startAttentionService,
                                         CloseEmergencyCommandService closeEmergencyService) {
        this.startAttentionService = startAttentionService;
        this.closeEmergencyService = closeEmergencyService;
    }

    @PostMapping("/{id}/start-attention")
    @Operation(summary = "Start attention on a driver emergency")
    @ApiResponse(responseCode = "200", description = "Attention started, status In progress")
    @ApiResponse(responseCode = "401", description = "No token provided")
    @ApiResponse(responseCode = "403", description = "Supervisor role required or EMERGENCY_ACCESS_DENIED")
    @ApiResponse(responseCode = "409", description = "INVALID_TRANSITION")
    public ResponseEntity<StartAttentionResource> startAttention(@PathVariable String id) {
        StartAttentionResult result = startAttentionService.startAttention(id);
        return ResponseEntity.ok(new StartAttentionResource(
            result.id(), result.status(),
            result.responsibleSupervisorId(), result.attentionStartedAt()));
    }

    @PostMapping("/{id}/close")
    @Operation(summary = "Close an emergency with an outcome")
    @ApiResponse(responseCode = "200", description = "Emergency closed")
    @ApiResponse(responseCode = "401", description = "No token provided")
    @ApiResponse(responseCode = "403", description = "Supervisor role required or EMERGENCY_ACCESS_DENIED")
    @ApiResponse(responseCode = "409", description = "INVALID_TRANSITION")
    @ApiResponse(responseCode = "422", description = "ATTENTION_NOT_STARTED, OUTCOME_REQUIRED, OUTCOME_TOO_LONG, or RESPONSE_TOO_LONG")
    public ResponseEntity<ClosedEmergencyResource> close(@PathVariable String id,
            @RequestBody CloseEmergencyRequest request) {
        CloseEmergencyResult result = closeEmergencyService.close(
            new CloseEmergencyCommand(id, request.outcome(), request.response()));
        return ResponseEntity.ok(new ClosedEmergencyResource(
            result.id(), result.status(), result.closedAt(), result.outcome()));
    }
}
