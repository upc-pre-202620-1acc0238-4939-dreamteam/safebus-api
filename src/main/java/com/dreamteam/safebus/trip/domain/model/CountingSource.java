package com.dreamteam.safebus.trip.domain.model;

import com.dreamteam.safebus.shared.domain.exceptions.ConflictException;
import com.dreamteam.safebus.shared.domain.exceptions.RuleViolationException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

// A registered passenger-count source for one bus. Only the hash of its key is stored.
@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"bus_id", "active_marker"}))
public class CountingSource {

    public static final int MAX_LABEL = 60;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long companyId;

    @Column(name = "bus_id", nullable = false)
    private Long busId;

    @Column(length = MAX_LABEL)
    private String label;

    @Column(name = "key_hash", nullable = false, unique = true, length = 64)
    private String keyHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CountingSourceStatus status;

    // 1 while the source is active, null once revoked; the unique constraint allows several NULLs
    @Column(name = "active_marker")
    private Integer activeMarker;

    @Column(nullable = false)
    private Long createdByUserId;

    @Column(nullable = false)
    private Instant createdAt;

    @Column
    private Instant rotatedAt;

    @Column
    private Instant revokedAt;

    @Version
    private Long version;

    protected CountingSource() {}

    public static CountingSource register(Long companyId, Long busId, String label, String keyHash,
                                          Long createdByUserId, Clock clock) {
        CountingSource s = new CountingSource();
        s.companyId = companyId;
        s.busId = busId;
        s.label = normalizeLabel(label);
        s.keyHash = keyHash;
        s.status = CountingSourceStatus.ACTIVE;
        s.activeMarker = 1;
        s.createdByUserId = createdByUserId;
        s.createdAt = Instant.now(clock).truncatedTo(ChronoUnit.MILLIS);
        return s;
    }

    public void rotate(String newKeyHash, Clock clock) {
        if (status != CountingSourceStatus.ACTIVE) {
            throw new ConflictException("SOURCE_REVOKED", "a revoked source cannot be rotated");
        }
        this.keyHash = newKeyHash;
        this.rotatedAt = Instant.now(clock).truncatedTo(ChronoUnit.MILLIS);
    }

    // Returns true when the state changed; revoking a revoked source is a no-op
    public boolean revoke(Clock clock) {
        if (status == CountingSourceStatus.REVOKED) {
            return false;
        }
        this.status = CountingSourceStatus.REVOKED;
        this.activeMarker = null;
        this.revokedAt = Instant.now(clock).truncatedTo(ChronoUnit.MILLIS);
        return true;
    }

    public boolean isActive() {
        return status == CountingSourceStatus.ACTIVE;
    }

    private static String normalizeLabel(String label) {
        if (label == null || label.isBlank()) {
            return null;
        }
        String trimmed = label.trim();
        if (trimmed.length() > MAX_LABEL) {
            throw new RuleViolationException("LABEL_TOO_LONG",
                "label must not exceed " + MAX_LABEL + " characters");
        }
        return trimmed;
    }

    public Long getId() { return id; }
    public Long getCompanyId() { return companyId; }
    public Long getBusId() { return busId; }
    public String getLabel() { return label; }
    public CountingSourceStatus getStatus() { return status; }
    public Integer getActiveMarker() { return activeMarker; }
    public Long getCreatedByUserId() { return createdByUserId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getRotatedAt() { return rotatedAt; }
    public Instant getRevokedAt() { return revokedAt; }
    public Long getVersion() { return version; }
}
