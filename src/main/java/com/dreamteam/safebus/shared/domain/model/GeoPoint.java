package com.dreamteam.safebus.shared.domain.model;

import com.dreamteam.safebus.shared.domain.exceptions.RuleViolationException;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public class GeoPoint {

    @Column(nullable = false)
    private double latitude;

    @Column(nullable = false)
    private double longitude;

    protected GeoPoint() {}

    public GeoPoint(double latitude, double longitude) {
        if (!Double.isFinite(latitude) || latitude < -90.0 || latitude > 90.0) {
            throw new RuleViolationException("INVALID_COORDINATES",
                "latitude must be finite and in [-90, 90]");
        }
        if (!Double.isFinite(longitude) || longitude < -180.0 || longitude > 180.0) {
            throw new RuleViolationException("INVALID_COORDINATES",
                "longitude must be finite and in [-180, 180]");
        }
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public double getLatitude() { return latitude; }
    public double getLongitude() { return longitude; }
}
