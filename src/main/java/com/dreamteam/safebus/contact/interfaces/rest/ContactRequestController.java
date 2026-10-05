package com.dreamteam.safebus.contact.interfaces.rest;

import com.dreamteam.safebus.contact.application.RegisterContactRequest;
import com.dreamteam.safebus.contact.application.RegisterContactRequestCommand;
import com.dreamteam.safebus.contact.application.RegisterContactRequestResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/contact-requests")
@PreAuthorize("permitAll()")
@Tag(name = "Contact Requests")
public class ContactRequestController {

    private final RegisterContactRequest service;

    public ContactRequestController(RegisterContactRequest service) {
        this.service = service;
    }

    @PostMapping
    @Operation(summary = "Submit a company contact request")
    @ApiResponse(responseCode = "201", description = "Contact request registered")
    @ApiResponse(responseCode = "200", description = "Identical retry; original receipt returned")
    @ApiResponse(responseCode = "409", description = "SUBMISSION_ID_REUSED")
    @ApiResponse(responseCode = "422", description = "VALIDATION_FAILED, INVALID_COMPANY_NAME, INVALID_CONTACT_NAME, INVALID_EMAIL, or CONSENT_REQUIRED")
    public ResponseEntity<ContactRequestReceiptResource> register(@Valid @RequestBody RegisterContactRequestRequest request) {
        RegisterContactRequestResult result = service.register(new RegisterContactRequestCommand(
            request.submissionId(), request.companyName(), request.contactName(), request.email(), request.consent()));
        ContactRequestReceiptResource resource = new ContactRequestReceiptResource(result.receiptReference(), result.receivedAt());
        HttpStatus status = result.duplicate() ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(resource);
    }
}
