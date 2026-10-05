package com.dreamteam.safebus.fleet.domain.model;

import com.dreamteam.safebus.shared.domain.exceptions.RuleViolationException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

@Entity
public class Company {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false)
    private boolean validated;

    protected Company() {}

    public static Company create(String name) {
        if (name == null || name.isBlank()) {
            throw new RuleViolationException("INVALID_NAME", "name is required");
        }
        String trimmed = name.trim();
        if (trimmed.length() > 100) {
            throw new RuleViolationException("INVALID_NAME", "name exceeds 100 characters");
        }
        Company c = new Company();
        c.name = trimmed;
        c.validated = true;
        return c;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public boolean isValidated() { return validated; }
}
