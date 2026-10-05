package com.dreamteam.safebus.shared.interfaces.rest;

import com.dreamteam.safebus.shared.application.CurrentUserProvider;
import com.dreamteam.safebus.shared.domain.exceptions.ForbiddenOperationException;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/test-security")
@Profile("test")
public class TestAuthController {

    private final CurrentUserProvider currentUserProvider;

    public TestAuthController(CurrentUserProvider currentUserProvider) {
        this.currentUserProvider = currentUserProvider;
    }

    @GetMapping("/supervisor-only")
    @PreAuthorize("hasRole('SUPERVISOR')")
    public ResponseEntity<String> supervisorOnly() {
        return ResponseEntity.ok("ok");
    }

    @GetMapping("/company/{companyId}")
    public ResponseEntity<String> companyResource(@PathVariable Long companyId) {
        var current = currentUserProvider.current();
        if (!companyId.equals(current.companyId())) {
            throw new ForbiddenOperationException("Access denied to company resource");
        }
        return ResponseEntity.ok("ok");
    }
}
