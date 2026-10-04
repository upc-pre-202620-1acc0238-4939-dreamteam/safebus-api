package com.dreamteam.safebus.trip.domain.model;

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
public class DriverShift {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private Long assignmentId;

    @Column(nullable = false)
    private Long driverId;

    @Column(nullable = false)
    private Long busId;

    @Column(nullable = false)
    private Long routeId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ShiftStatus status;

    @Column(nullable = false)
    private Instant startedAt;

    @Column
    private Instant closedAt;

    protected DriverShift() {}

    public static DriverShift start(Long assignmentId, Long driverId,
                                    Long busId, Long routeId, Clock clock) {
        DriverShift ds = new DriverShift();
        ds.assignmentId = assignmentId;
        ds.driverId = driverId;
        ds.busId = busId;
        ds.routeId = routeId;
        ds.status = ShiftStatus.ACTIVE;
        ds.startedAt = Instant.now(clock);
        ds.closedAt = null;
        return ds;
    }

    public Long getId() { return id; }
    public Long getAssignmentId() { return assignmentId; }
    public Long getDriverId() { return driverId; }
    public Long getBusId() { return busId; }
    public Long getRouteId() { return routeId; }
    public ShiftStatus getStatus() { return status; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getClosedAt() { return closedAt; }
}
