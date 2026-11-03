package com.dreamteam.safebus.safetycase.interfaces.rest;

import com.dreamteam.safebus.safetycase.application.ApproveGroupCommand;
import com.dreamteam.safebus.safetycase.application.ApproveGroupCommandService;
import com.dreamteam.safebus.safetycase.application.ApproveGroupResult;
import com.dreamteam.safebus.safetycase.application.DeclineGroupCommand;
import com.dreamteam.safebus.safetycase.application.DeclineGroupCommandService;
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
@RequestMapping("/api/v1/request-groups")
@PreAuthorize("hasRole('SUPERVISOR')")
@Tag(name = "Passenger Group Review")
public class RequestGroupDecisionController {

    private final ApproveGroupCommandService approveService;
    private final DeclineGroupCommandService declineService;

    public RequestGroupDecisionController(ApproveGroupCommandService approveService,
                                          DeclineGroupCommandService declineService) {
        this.approveService = approveService;
        this.declineService = declineService;
    }

    @PostMapping("/{id}/approve")
    @Operation(summary = "Approve a passenger group awaiting company approval")
    @ApiResponse(responseCode = "201", description = "Group approved, High-priority emergency activated")
    @ApiResponse(responseCode = "200", description = "Group already approved, the same emergency is returned")
    @ApiResponse(responseCode = "401", description = "No token provided")
    @ApiResponse(responseCode = "403", description = "Supervisor role required or GROUP_ACCESS_DENIED")
    @ApiResponse(responseCode = "409", description = "INVALID_TRANSITION")
    public ResponseEntity<ApprovedGroupResource> approve(@PathVariable String id) {
        ApproveGroupResult result = approveService.approve(new ApproveGroupCommand(parseId(id)));
        HttpStatus status = result.replay() ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(ApprovedGroupResourceFromResultAssembler.toResource(result));
    }

    @PostMapping("/{id}/decline")
    @Operation(summary = "Decline a passenger group awaiting company approval")
    @ApiResponse(responseCode = "200", description = "Group not approved; a repeated decline returns the stored decision")
    @ApiResponse(responseCode = "401", description = "No token provided")
    @ApiResponse(responseCode = "403", description = "Supervisor role required or GROUP_ACCESS_DENIED")
    @ApiResponse(responseCode = "409", description = "INVALID_TRANSITION")
    @ApiResponse(responseCode = "422", description = "REASON_REQUIRED, REASON_TOO_LONG or RESPONSE_TOO_LONG")
    public ResponseEntity<DeclinedGroupResource> decline(@PathVariable String id,
            @RequestBody(required = false) DeclineGroupRequest request) {
        DeclineGroupRequest body = request == null ? new DeclineGroupRequest(null, null) : request;
        return ResponseEntity.ok(DeclinedGroupResourceFromResultAssembler.toResource(
            declineService.decline(new DeclineGroupCommand(parseId(id), body.reason(), body.response()))));
    }

    // A malformed id is treated like a missing group so it cannot be told apart from a foreign one
    private static Long parseId(String raw) {
        try {
            return Long.valueOf(raw);
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
