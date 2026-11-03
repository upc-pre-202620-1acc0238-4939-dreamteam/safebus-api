package com.dreamteam.safebus.safetycase.domain.model;

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

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"shift_id", "open_marker"}))
public class PassengerRequestGroup {

    public static final int MAX_DECISION_TEXT = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long companyId;

    @Column(nullable = false)
    private Long busId;

    @Column(nullable = false)
    private Long shiftId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private GroupStatus status;

    @Column(nullable = false)
    private Instant windowStartedAt;

    @Column(nullable = false)
    private Instant windowExpiresAt;

    @Column
    private Instant awaitingSince;

    @Column
    private Long decidedByUserId;

    @Column
    private Instant decidedAt;

    @Column(length = MAX_DECISION_TEXT)
    private String decisionReason;

    @Column(length = MAX_DECISION_TEXT)
    private String userResponse;

    // 1 while the group is open, null otherwise; the unique constraint allows several NULLs
    @Column(name = "open_marker")
    private Integer openMarker;

    // Nullable so groups created before the column existed stay valid; the unique index allows several NULLs.
    @Column(name = "public_reference", length = 36, unique = true)
    private String publicReference;

    @Version
    private Long version;

    protected PassengerRequestGroup() {}

    public static PassengerRequestGroup open(Long companyId, Long busId, Long shiftId, Instant now) {
        PassengerRequestGroup g = new PassengerRequestGroup();
        g.companyId = companyId;
        g.busId = busId;
        g.shiftId = shiftId;
        g.status = GroupStatus.COLLECTING;
        g.windowStartedAt = now.truncatedTo(ChronoUnit.MILLIS);
        g.windowExpiresAt = g.windowStartedAt.plus(PassengerRequestPolicy.WINDOW);
        g.openMarker = 1;
        g.publicReference = UUID.randomUUID().toString();
        return g;
    }

    public boolean isWindowOverdue(Instant now) {
        return status == GroupStatus.COLLECTING && !now.isBefore(windowExpiresAt);
    }

    public void expire() {
        if (status != GroupStatus.COLLECTING) {
            throw new ConflictException("INVALID_TRANSITION", "only a collecting group can expire");
        }
        this.status = GroupStatus.EXPIRED;
        this.openMarker = null;
    }

    public void moveToAwaitingApproval(Instant now) {
        if (status != GroupStatus.COLLECTING) {
            throw new ConflictException("INVALID_TRANSITION",
                "only a collecting group can await approval");
        }
        this.status = GroupStatus.AWAITING_APPROVAL;
        this.awaitingSince = now.truncatedTo(ChronoUnit.MILLIS);
    }

    public void approve(Long supervisorUserId, Instant now) {
        requireStatus(GroupStatus.AWAITING_APPROVAL, "approved");
        this.status = GroupStatus.APPROVED;
        this.decidedByUserId = supervisorUserId;
        this.decidedAt = now.truncatedTo(ChronoUnit.MILLIS);
    }

    public void decline(Long supervisorUserId, String reason, String response, Instant now) {
        requireStatus(GroupStatus.AWAITING_APPROVAL, "declined");
        String trimmedReason = reason == null ? "" : reason.trim();
        if (trimmedReason.isEmpty()) {
            throw new RuleViolationException("REASON_REQUIRED", "a reason is required to decline a group");
        }
        if (trimmedReason.length() > MAX_DECISION_TEXT) {
            throw new RuleViolationException("REASON_TOO_LONG",
                "reason must not exceed " + MAX_DECISION_TEXT + " characters");
        }
        String trimmedResponse = response == null || response.isBlank() ? null : response.trim();
        if (trimmedResponse != null && trimmedResponse.length() > MAX_DECISION_TEXT) {
            throw new RuleViolationException("RESPONSE_TOO_LONG",
                "response must not exceed " + MAX_DECISION_TEXT + " characters");
        }
        this.status = GroupStatus.NOT_APPROVED;
        this.decidedByUserId = supervisorUserId;
        this.decidedAt = now.truncatedTo(ChronoUnit.MILLIS);
        this.decisionReason = trimmedReason;
        this.userResponse = trimmedResponse;
        this.openMarker = null;
    }

    public void close() {
        requireStatus(GroupStatus.APPROVED, "closed");
        this.status = GroupStatus.CLOSED;
        this.openMarker = null;
    }

    private void requireStatus(GroupStatus required, String action) {
        if (status != required) {
            throw new ConflictException("INVALID_TRANSITION",
                "group is " + status + "; only a " + required + " group can be " + action);
        }
    }

    public Long getId() { return id; }
    public Long getCompanyId() { return companyId; }
    public Long getBusId() { return busId; }
    public Long getShiftId() { return shiftId; }
    public GroupStatus getStatus() { return status; }
    public Instant getWindowStartedAt() { return windowStartedAt; }
    public Instant getWindowExpiresAt() { return windowExpiresAt; }
    public Instant getAwaitingSince() { return awaitingSince; }
    public Integer getOpenMarker() { return openMarker; }
    public Long getDecidedByUserId() { return decidedByUserId; }
    public Instant getDecidedAt() { return decidedAt; }
    public String getDecisionReason() { return decisionReason; }
    public String getUserResponse() { return userResponse; }
    public String getPublicReference() { return publicReference; }
    public Long getVersion() { return version; }
}
