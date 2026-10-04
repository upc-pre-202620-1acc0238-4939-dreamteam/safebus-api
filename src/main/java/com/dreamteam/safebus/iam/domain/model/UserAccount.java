package com.dreamteam.safebus.iam.domain.model;

import com.dreamteam.safebus.shared.domain.exceptions.RuleViolationException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Clock;
import java.time.Instant;

@Entity
@Table(name = "user_accounts")
public class UserAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, length = 50, nullable = false)
    private String loginId;

    @Column(nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserRole role;

    private Long companyId;

    @Column(nullable = false)
    private boolean enabled;

    @Column(nullable = false)
    private Instant createdAt;

    protected UserAccount() {
    }

    public static UserAccount create(String loginId, String passwordHash, UserRole role, Long companyId, Clock clock) {
        if (loginId == null || loginId.isBlank()) {
            throw new RuleViolationException("INVALID_LOGIN_ID", "loginId cannot be blank");
        }
        if (loginId.trim().length() > 50) {
            throw new RuleViolationException("INVALID_LOGIN_ID", "loginId exceeds 50 characters");
        }
        if (passwordHash == null || passwordHash.isBlank()) {
            throw new RuleViolationException("INVALID_PASSWORD_HASH", "passwordHash cannot be blank");
        }
        if ((role == UserRole.DRIVER || role == UserRole.SUPERVISOR) && companyId == null) {
            throw new RuleViolationException("COMPANY_REQUIRED", "DRIVER and SUPERVISOR require a companyId");
        }
        if (role == UserRole.PASSENGER && companyId != null) {
            throw new RuleViolationException("COMPANY_FORBIDDEN", "PASSENGER must not have a companyId");
        }
        UserAccount account = new UserAccount();
        account.loginId = loginId.trim();
        account.passwordHash = passwordHash;
        account.role = role;
        account.companyId = companyId;
        account.enabled = true;
        account.createdAt = Instant.now(clock);
        return account;
    }

    public void disable() {
        this.enabled = false;
    }

    public void enable() {
        this.enabled = true;
    }

    public Long getId() { return id; }
    public String getLoginId() { return loginId; }
    public String getPasswordHash() { return passwordHash; }
    public UserRole getRole() { return role; }
    public Long getCompanyId() { return companyId; }
    public boolean isEnabled() { return enabled; }
    public Instant getCreatedAt() { return createdAt; }
}
