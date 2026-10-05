package com.dreamteam.safebus.fleet.domain.model;

import com.dreamteam.safebus.shared.domain.exceptions.ConflictException;
import com.dreamteam.safebus.shared.domain.exceptions.RuleViolationException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

import java.time.Clock;
import java.time.Instant;

@Entity
public class ShiftAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long driverId;

    @Column(nullable = false)
    private Long busId;

    @Column(nullable = false)
    private Long routeId;

    @Column(nullable = false)
    private Instant plannedStart;

    @Column(nullable = false)
    private Instant plannedEnd;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AssignmentStatus status;

    @Column(nullable = false)
    private Long createdByUserId;

    @Column(nullable = false)
    private Instant createdAt;

    protected ShiftAssignment() {}

    public static void validatePeriod(Instant start, Instant end) {
        if (start == null || end == null || !end.isAfter(start)) {
            throw new RuleViolationException("INVALID_PERIOD", "end must be strictly after start");
        }
    }

    public static ShiftAssignment create(Long driverId, Long busId, Long routeId,
                                          Instant plannedStart, Instant plannedEnd,
                                          Long createdByUserId, Clock clock) {
        validatePeriod(plannedStart, plannedEnd);
        ShiftAssignment sa = new ShiftAssignment();
        sa.driverId = driverId;
        sa.busId = busId;
        sa.routeId = routeId;
        sa.plannedStart = plannedStart;
        sa.plannedEnd = plannedEnd;
        sa.status = AssignmentStatus.ASSIGNED;
        sa.createdByUserId = createdByUserId;
        sa.createdAt = Instant.now(clock);
        return sa;
    }

    public void activate() {
        if (this.status != AssignmentStatus.ASSIGNED) {
            throw new RuleViolationException("ASSIGNMENT_NOT_AVAILABLE",
                "assignment is not in ASSIGNED status");
        }
        this.status = AssignmentStatus.ACTIVE;
    }

    public boolean close() {
        if (this.status == AssignmentStatus.CLOSED) {
            return false;
        }
        if (this.status != AssignmentStatus.ACTIVE) {
            throw new ConflictException("ASSIGNMENT_NOT_ACTIVE",
                "assignment must be ACTIVE to be closed");
        }
        this.status = AssignmentStatus.CLOSED;
        return true;
    }

    public static boolean overlaps(Instant startA, Instant endA, Instant startB, Instant endB) {
        return startA.isBefore(endB) && startB.isBefore(endA);
    }

    public Long getId() { return id; }
    public Long getDriverId() { return driverId; }
    public Long getBusId() { return busId; }
    public Long getRouteId() { return routeId; }
    public Instant getPlannedStart() { return plannedStart; }
    public Instant getPlannedEnd() { return plannedEnd; }
    public AssignmentStatus getStatus() { return status; }
    public Long getCreatedByUserId() { return createdByUserId; }
    public Instant getCreatedAt() { return createdAt; }
}
