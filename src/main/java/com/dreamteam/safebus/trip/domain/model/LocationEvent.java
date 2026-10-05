package com.dreamteam.safebus.trip.domain.model;

import com.dreamteam.safebus.shared.domain.exceptions.RuleViolationException;
import com.dreamteam.safebus.shared.domain.model.GeoPoint;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Entity
public class LocationEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String eventId;

    @Column(nullable = false)
    private Long shiftId;

    @Column(nullable = false)
    private Long busId;

    @Embedded
    private GeoPoint point;

    @Column(nullable = false)
    private double accuracyMeters;

    @Column(nullable = false)
    private Instant capturedAt;

    @Column(nullable = false)
    private Instant receivedAt;

    @Column(nullable = false)
    private boolean appliedAsCurrent;

    protected LocationEvent() {}

    public static LocationEvent create(String eventId, Long shiftId, Long busId,
                                        double latitude, double longitude,
                                        double accuracyMeters, Instant capturedAt,
                                        Clock clock) {
        if (accuracyMeters < 0.0) {
            throw new RuleViolationException("INVALID_ACCURACY",
                "accuracyMeters must be zero or positive");
        }
        LocationEvent e = new LocationEvent();
        e.eventId = eventId;
        e.shiftId = shiftId;
        e.busId = busId;
        e.point = new GeoPoint(latitude, longitude);
        e.accuracyMeters = accuracyMeters;
        e.capturedAt = capturedAt.truncatedTo(ChronoUnit.MILLIS);
        e.receivedAt = Instant.now(clock).truncatedTo(ChronoUnit.MILLIS);
        e.appliedAsCurrent = false;
        return e;
    }

    // Called once by the writer after applyIfNewer determines the value
    public void markAppliedAsCurrent() {
        this.appliedAsCurrent = true;
    }

    public boolean hasSamePayloadAs(Long shiftId, Instant capturedAt,
                                     double accuracyMeters,
                                     double latitude, double longitude) {
        Instant truncated = capturedAt.truncatedTo(ChronoUnit.MILLIS);
        return this.shiftId.equals(shiftId)
            && this.capturedAt.equals(truncated)
            && Double.compare(this.accuracyMeters, accuracyMeters) == 0
            && Double.compare(this.point.getLatitude(), latitude) == 0
            && Double.compare(this.point.getLongitude(), longitude) == 0;
    }

    public Long getId() { return id; }
    public String getEventId() { return eventId; }
    public Long getShiftId() { return shiftId; }
    public Long getBusId() { return busId; }
    public GeoPoint getPoint() { return point; }
    public double getAccuracyMeters() { return accuracyMeters; }
    public Instant getCapturedAt() { return capturedAt; }
    public Instant getReceivedAt() { return receivedAt; }
    public boolean isAppliedAsCurrent() { return appliedAsCurrent; }
}
