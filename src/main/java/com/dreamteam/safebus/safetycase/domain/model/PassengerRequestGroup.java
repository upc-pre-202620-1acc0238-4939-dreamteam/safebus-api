package com.dreamteam.safebus.safetycase.domain.model;

import com.dreamteam.safebus.shared.domain.exceptions.ConflictException;
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

@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"shift_id", "open_marker"}))
public class PassengerRequestGroup {

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

    // 1 while the group is open, null otherwise; the unique constraint allows several NULLs
    @Column(name = "open_marker")
    private Integer openMarker;

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

    public Long getId() { return id; }
    public Long getCompanyId() { return companyId; }
    public Long getBusId() { return busId; }
    public Long getShiftId() { return shiftId; }
    public GroupStatus getStatus() { return status; }
    public Instant getWindowStartedAt() { return windowStartedAt; }
    public Instant getWindowExpiresAt() { return windowExpiresAt; }
    public Instant getAwaitingSince() { return awaitingSince; }
    public Integer getOpenMarker() { return openMarker; }
    public Long getVersion() { return version; }
}
