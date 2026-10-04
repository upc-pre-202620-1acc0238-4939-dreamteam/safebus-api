package com.dreamteam.safebus.fleet.domain.model;

import com.dreamteam.safebus.shared.domain.exceptions.RuleViolationException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

import java.util.Locale;

@Entity
public class Route {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long companyId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String origin;

    @Column(nullable = false)
    private String destination;

    @Column(nullable = false)
    private boolean enabled;

    protected Route() {}

    public static Route create(Long companyId, String name, String origin, String destination) {
        requireNonBlank(name, "NAME");
        requireNonBlank(origin, "ORIGIN");
        requireNonBlank(destination, "DESTINATION");
        Route r = new Route();
        r.companyId = companyId;
        r.name = name.trim();
        r.origin = origin.trim();
        r.destination = destination.trim();
        r.enabled = true;
        return r;
    }

    private static void requireNonBlank(String value, String fieldCode) {
        if (value == null || value.isBlank()) {
            throw new RuleViolationException("INVALID_" + fieldCode,
                fieldCode.toLowerCase(Locale.ROOT) + " is required");
        }
    }

    public Long getId() { return id; }
    public Long getCompanyId() { return companyId; }
    public String getName() { return name; }
    public String getOrigin() { return origin; }
    public String getDestination() { return destination; }
    public boolean isEnabled() { return enabled; }
    public void disable() { this.enabled = false; }
}
