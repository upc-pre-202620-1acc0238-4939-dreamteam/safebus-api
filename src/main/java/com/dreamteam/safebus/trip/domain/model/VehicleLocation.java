package com.dreamteam.safebus.trip.domain.model;

import com.dreamteam.safebus.shared.domain.model.GeoPoint;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Version;

import java.time.Clock;
import java.time.Instant;

@Entity
public class VehicleLocation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private Long busId;

    // Nullable columns because a freshly created row has no position yet
    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "latitude",
            column = @Column(name = "latitude", nullable = true)),
        @AttributeOverride(name = "longitude",
            column = @Column(name = "longitude", nullable = true))
    })
    private GeoPoint point;

    @Column
    private Double accuracyMeters;

    @Column
    private Instant capturedAt;

    @Column
    private String lastEventId;

    @Column
    private Instant updatedAt;

    @Version
    private Long version;

    protected VehicleLocation() {}

    public static VehicleLocation empty(Long busId) {
        VehicleLocation vl = new VehicleLocation();
        vl.busId = busId;
        return vl;
    }

    public boolean applyIfNewer(LocationEvent event, Clock clock) {
        if (capturedAt == null || event.getCapturedAt().isAfter(capturedAt)) {
            this.point = event.getPoint();
            this.accuracyMeters = event.getAccuracyMeters();
            this.capturedAt = event.getCapturedAt();
            this.lastEventId = event.getEventId();
            this.updatedAt = Instant.now(clock);
            return true;
        }
        return false;
    }

    public Long getId() { return id; }
    public Long getBusId() { return busId; }
    public GeoPoint getPoint() { return point; }
    public Double getAccuracyMeters() { return accuracyMeters; }
    public Instant getCapturedAt() { return capturedAt; }
    public String getLastEventId() { return lastEventId; }
    public Instant getUpdatedAt() { return updatedAt; }
}
