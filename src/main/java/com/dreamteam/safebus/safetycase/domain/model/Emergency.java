package com.dreamteam.safebus.safetycase.domain.model;

import com.dreamteam.safebus.shared.domain.exceptions.ConflictException;
import com.dreamteam.safebus.shared.domain.exceptions.RuleViolationException;
import com.dreamteam.safebus.shared.domain.model.GeoPoint;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.Transient;
import jakarta.persistence.Version;
import org.springframework.data.domain.Persistable;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Entity
public class Emergency implements Persistable<String> {

    @Id
    private String id;

    @Transient
    private boolean isNew = true;

    @PostLoad
    void markNotNew() { this.isNew = false; }

    @Override
    public String getId() { return id; }

    @Override
    public boolean isNew() { return isNew; }

    @Column(nullable = false)
    private Long companyId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EmergencySource source;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EmergencyPriority priority;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EmergencyStatus status;

    @Column
    private Long driverId;

    @Column(nullable = false)
    private Long busId;

    @Column(nullable = false)
    private Long shiftId;

    @Column(nullable = false)
    private Long routeId;

    // location exactly as supplied by the device; no server-side fallback
    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "latitude",  column = @Column(name = "latitude",  nullable = true)),
        @AttributeOverride(name = "longitude", column = @Column(name = "longitude", nullable = true))
    })
    private GeoPoint point;

    // occupancyCount always null for now; populated by a later story
    @Column
    private Integer occupancyCount;

    @Column(nullable = false)
    private Instant activatedAt;

    @Column(nullable = false)
    private Instant receivedAt;

    @Column
    private Long responsibleSupervisorUserId;

    @Column
    private Instant attentionStartedAt;

    @Column
    private Instant closedAt;

    @Column(length = 500)
    private String outcome;

    @Column(length = 500)
    private String userResponse;

    @Version
    private Long version;

    protected Emergency() {}

    public static Emergency activateByDriver(String id, Long companyId, Long driverId,
                                              Long busId, Long shiftId, Long routeId,
                                              GeoPoint point, Instant activatedAt, Clock clock) {
        Emergency e = new Emergency();
        e.id = id;
        e.companyId = companyId;
        e.source = EmergencySource.DRIVER;
        e.priority = EmergencyPriority.CRITICAL;
        e.status = EmergencyStatus.ACTIVE;
        e.driverId = driverId;
        e.busId = busId;
        e.shiftId = shiftId;
        e.routeId = routeId;
        e.point = point;
        e.activatedAt = activatedAt.truncatedTo(ChronoUnit.MILLIS);
        e.receivedAt = Instant.now(clock).truncatedTo(ChronoUnit.MILLIS);
        return e;
    }

    public void startAttention(Long supervisorUserId, Instant now) {
        if (status != EmergencyStatus.ACTIVE) {
            throw new ConflictException("INVALID_TRANSITION",
                "emergency must be ACTIVE to start attention");
        }
        this.status = EmergencyStatus.IN_PROGRESS;
        this.responsibleSupervisorUserId = supervisorUserId;
        this.attentionStartedAt = now.truncatedTo(ChronoUnit.MILLIS);
    }

    public void close(String outcome, String userResponse, Instant now) {
        if (status == EmergencyStatus.ACTIVE) {
            throw new RuleViolationException("ATTENTION_NOT_STARTED",
                "attention must be started before closing");
        }
        if (status == EmergencyStatus.CLOSED) {
            throw new ConflictException("INVALID_TRANSITION",
                "emergency is already closed");
        }
        if (outcome == null || outcome.isBlank()) {
            throw new RuleViolationException("OUTCOME_REQUIRED",
                "outcome is required to close an emergency");
        }
        if (outcome.length() > 500) {
            throw new RuleViolationException("OUTCOME_TOO_LONG",
                "outcome must not exceed 500 characters");
        }
        if (userResponse != null && userResponse.length() > 500) {
            throw new RuleViolationException("RESPONSE_TOO_LONG",
                "userResponse must not exceed 500 characters");
        }
        this.status = EmergencyStatus.CLOSED;
        this.outcome = outcome;
        this.userResponse = userResponse;
        this.closedAt = now.truncatedTo(ChronoUnit.MILLIS);
    }

    public boolean hasSamePayloadAs(Long driverId, Long shiftId, Instant activatedAt,
                                     Double latitude, Double longitude) {
        Instant truncated = activatedAt.truncatedTo(ChronoUnit.MILLIS);
        if (!this.shiftId.equals(shiftId)) return false;
        if (!this.activatedAt.equals(truncated)) return false;
        if (this.driverId == null || !this.driverId.equals(driverId)) return false;
        if (latitude == null && longitude == null) {
            return this.point == null;
        }
        if (latitude != null && longitude != null) {
            return this.point != null
                && Double.compare(this.point.getLatitude(), latitude) == 0
                && Double.compare(this.point.getLongitude(), longitude) == 0;
        }
        return false;
    }

    public Long getCompanyId() { return companyId; }
    public EmergencySource getSource() { return source; }
    public EmergencyPriority getPriority() { return priority; }
    public EmergencyStatus getStatus() { return status; }
    public Long getDriverId() { return driverId; }
    public Long getBusId() { return busId; }
    public Long getShiftId() { return shiftId; }
    public Long getRouteId() { return routeId; }
    public GeoPoint getPoint() { return point; }
    public Integer getOccupancyCount() { return occupancyCount; }
    public Instant getActivatedAt() { return activatedAt; }
    public Instant getReceivedAt() { return receivedAt; }
    public Long getResponsibleSupervisorUserId() { return responsibleSupervisorUserId; }
    public Instant getAttentionStartedAt() { return attentionStartedAt; }
    public Instant getClosedAt() { return closedAt; }
    public String getOutcome() { return outcome; }
    public String getUserResponse() { return userResponse; }
}
