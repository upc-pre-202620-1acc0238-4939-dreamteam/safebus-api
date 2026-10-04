package com.dreamteam.safebus.shared.interfaces.rest;

import com.dreamteam.safebus.shared.domain.exceptions.ConflictException;
import com.dreamteam.safebus.shared.domain.exceptions.ForbiddenOperationException;
import com.dreamteam.safebus.shared.domain.exceptions.NotFoundException;
import com.dreamteam.safebus.shared.domain.exceptions.RuleViolationException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/test-exceptions")
@Profile("test")
public class TestExceptionController {

    @GetMapping("/not-found")
    public void notFound() {
        throw new NotFoundException("resource not found");
    }

    @GetMapping("/conflict")
    public void conflict() {
        throw new ConflictException("resource already exists");
    }

    @GetMapping("/rule-violation")
    public void ruleViolation() {
        throw new RuleViolationException("rule violated");
    }

    @GetMapping("/forbidden")
    public void forbidden() {
        throw new ForbiddenOperationException("operation not allowed");
    }

    @GetMapping("/custom-code")
    public void customCode() {
        throw new RuleViolationException("INVALID_TRANSITION", "Closure requires started attention");
    }

    public record ValidatedRequest(@NotBlank String name) {}

    @PostMapping("/validated")
    public ResponseEntity<Void> validated(@Valid @RequestBody ValidatedRequest body) {
        return ResponseEntity.ok().build();
    }
}
