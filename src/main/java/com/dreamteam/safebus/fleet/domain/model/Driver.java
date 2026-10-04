package com.dreamteam.safebus.fleet.domain.model;

import com.dreamteam.safebus.fleet.domain.port.CredentialGenerator;
import com.dreamteam.safebus.shared.domain.exceptions.RuleViolationException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

@Entity
public class Driver {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long companyId;

    @Column(nullable = false)
    private Long userAccountId;

    @Column(nullable = false)
    private String fullName;

    @Column(nullable = false)
    private boolean enabled;

    @Column(unique = true, nullable = false)
    private String qrCredential;

    @Column(nullable = false)
    private Instant qrCredentialExpiresAt;

    protected Driver() {}

    public static Driver create(Long companyId, Long userAccountId, String fullName,
                                 CredentialGenerator credGen, Duration ttl, Clock clock) {
        if (fullName == null || fullName.isBlank()) {
            throw new RuleViolationException("INVALID_FULL_NAME", "fullName is required");
        }
        Driver d = new Driver();
        d.companyId = companyId;
        d.userAccountId = userAccountId;
        d.fullName = fullName.trim();
        d.enabled = true;
        d.qrCredential = credGen.generate();
        d.qrCredentialExpiresAt = Instant.now(clock).plus(ttl);
        return d;
    }

    public Long getId() { return id; }
    public Long getCompanyId() { return companyId; }
    public Long getUserAccountId() { return userAccountId; }
    public String getFullName() { return fullName; }
    public boolean isEnabled() { return enabled; }
    public String getQrCredential() { return qrCredential; }
    public Instant getQrCredentialExpiresAt() { return qrCredentialExpiresAt; }
    public void disable() { this.enabled = false; }
}
