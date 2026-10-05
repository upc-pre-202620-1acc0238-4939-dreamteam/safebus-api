package com.dreamteam.safebus.passenger.domain.model;

import com.dreamteam.safebus.shared.domain.exceptions.RuleViolationException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Entity
public class PassengerAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private Long userAccountId;

    @Column(unique = true, nullable = false, length = 8)
    private String dni;

    @Column(nullable = false)
    private Long faceImageId;

    @Column(nullable = false, length = 20)
    private String termsVersion;

    @Column(nullable = false)
    private Instant termsAcceptedAt;

    @Column(nullable = false)
    private Instant createdAt;

    protected PassengerAccount() {}

    public static PassengerAccount register(Long userAccountId, String rawDni,
                                             Long faceImageId, String termsVersion, Clock clock) {
        String normalized = rawDni != null ? rawDni.trim() : null;
        // [0-9] matches only ASCII digits, rejecting Arabic-Indic and other Unicode digit categories
        if (normalized == null || !normalized.matches("[0-9]{8}")) {
            throw new RuleViolationException("INVALID_DNI", "dni must be exactly 8 ASCII digits");
        }
        PassengerAccount a = new PassengerAccount();
        a.userAccountId = userAccountId;
        a.dni = normalized;
        a.faceImageId = faceImageId;
        a.termsVersion = termsVersion;
        Instant now = Instant.now(clock).truncatedTo(ChronoUnit.MILLIS);
        a.termsAcceptedAt = now;
        a.createdAt = now;
        return a;
    }

    public Long getId() { return id; }
    public Long getUserAccountId() { return userAccountId; }
    public String getDni() { return dni; }
    public Long getFaceImageId() { return faceImageId; }
    public String getTermsVersion() { return termsVersion; }
    public Instant getTermsAcceptedAt() { return termsAcceptedAt; }
    public Instant getCreatedAt() { return createdAt; }
}
